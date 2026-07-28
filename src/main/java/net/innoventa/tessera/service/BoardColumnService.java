package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.CreateBoardColumnRequest;
import net.innoventa.tessera.dto.board.SetColumnFallbackRequest;
import net.innoventa.tessera.dto.board.UpdateBoardColumnRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Board column configuration (Phase-2 ticket 03) — create/rename/reorder/delete a column, set its soft
 * WIP limits, assign/clear which category it is the fallback for, and explicitly map/unmap a status onto
 * it. Every mutation requires {@code ADMINISTER_PROJECT}; reads (the board itself) stay in
 * {@link BoardService}.
 * <p>
 * The one invariant every method here protects (ADR-0010): <strong>exactly one fallback column per
 * category always exists</strong>. Assigning a category's fallback role atomically strips it from
 * whichever column held it; clearing it, or deleting the column that holds it, is refused ({@code 409})
 * unless another column can immediately take over — never leaving a category homeless.
 */
@Service
@RequiredArgsConstructor
public class BoardColumnService {

    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final StatusRepository statusRepository;
    private final BoardService boardService;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final Supplier<String> idGenerator;

    @Transactional
    public BoardColumnView create(Jwt jwt, String projectId, CreateBoardColumnRequest request) {
        Board board = requireAdminister(jwt, projectId);
        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId());

        int insertAt = request.position() == null ? columns.size() : clamp(request.position(), columns.size());

        BoardColumn column = BoardColumn.builder()
            .id(idGenerator.get())
            .boardId(board.getId())
            .name(request.name())
            .position(insertAt)
            .minIssues(request.minIssues())
            .maxIssues(request.maxIssues())
            .build();

        List<BoardColumn> withNewColumn = new ArrayList<>(columns);
        withNewColumn.add(insertAt, column);
        reindex(withNewColumn);
        boardColumnRepository.save(column);
        boardColumnRepository.saveAll(columns);

        return toView(column);
    }

    @Transactional
    public BoardColumnView update(Jwt jwt, String projectId, String columnId, UpdateBoardColumnRequest request) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        column.setName(request.name());
        column.setMinIssues(request.minIssues());
        column.setMaxIssues(request.maxIssues());

        return toView(column);
    }

    @Transactional
    public BoardColumnView reorder(Jwt jwt, String projectId, String columnId, int newPosition) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        List<BoardColumn> columns = new ArrayList<>(
            boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()));
        columns.remove(column);
        columns.add(clamp(newPosition, columns.size()), column);
        reindex(columns);
        boardColumnRepository.saveAll(columns);

        return toView(column);
    }

    @Transactional
    public void delete(Jwt jwt, String projectId, String columnId) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        if (column.getFallbackForCategory() != null) {
            reassignFallbackAwayFrom(board.getId(), column);
        }

        boardColumnStatusRepository.deleteAll(boardColumnStatusRepository.findByBoardColumnId(columnId));
        boardColumnRepository.delete(column);

        List<BoardColumn> remaining = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId());
        reindex(remaining);
        boardColumnRepository.saveAll(remaining);
    }

    @Transactional
    public BoardColumnView setFallback(Jwt jwt, String projectId, String columnId, SetColumnFallbackRequest request) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        // Switching this column onto a *different* category would otherwise strip its current one with
        // nobody left to take over — vacate it onto a sibling first (409 if none can), same rule delete/
        // clear enforce, so a column can never carry away its category's only fallback home unnoticed.
        if (column.getFallbackForCategory() != null && column.getFallbackForCategory() != request.category()) {
            reassignFallbackAwayFrom(board.getId(), column);
        }

        boardColumnRepository.findByBoardIdAndFallbackForCategory(board.getId(), request.category())
            .filter(holder -> !holder.getId().equals(column.getId()))
            .ifPresent(holder -> holder.setFallbackForCategory(null));

        column.setFallbackForCategory(request.category());

        return toView(column);
    }

    @Transactional
    public void clearFallback(Jwt jwt, String projectId, String columnId) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        if (column.getFallbackForCategory() == null) {
            return;
        }

        reassignFallbackAwayFrom(board.getId(), column);
    }

    @Transactional
    public void mapStatus(Jwt jwt, String projectId, String columnId, String statusId) {
        Board board = requireAdminister(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());
        Status status = statusRepository.findById(statusId)
            .orElseThrow(() -> new ResourceNotFoundException("Status not found: " + statusId));

        boardColumnStatusRepository.findByBoardIdAndStatusId(board.getId(), status.getId())
            .ifPresent(boardColumnStatusRepository::delete);

        boardColumnStatusRepository.save(BoardColumnStatus.builder()
            .id(idGenerator.get())
            .boardId(board.getId())
            .boardColumnId(column.getId())
            .statusId(status.getId())
            .build());
    }

    @Transactional
    public void unmapStatus(Jwt jwt, String projectId, String columnId, String statusId) {
        Board board = requireAdminister(jwt, projectId);
        requireColumn(columnId, board.getId());

        BoardColumnStatus mapping = boardColumnStatusRepository.findByBoardIdAndStatusId(board.getId(), statusId)
            .filter(entry -> entry.getBoardColumnId().equals(columnId))
            .orElseThrow(() -> new ResourceNotFoundException("Status is not explicitly mapped to this column"));

        boardColumnStatusRepository.delete(mapping);
    }

    /**
     * Hand {@code column}'s fallback role to a sibling with no fallback role of its own — the only kind
     * of sibling that can take it over without orphaning whichever category it already backs. No such
     * sibling exists only when every column already backs a distinct category (the classic default
     * 3-column-per-3-category board), which is exactly the "last fallback column" case the spec refuses.
     */
    private void reassignFallbackAwayFrom(String boardId, BoardColumn column) {
        StatusCategory category = column.getFallbackForCategory();

        BoardColumn successor = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
            .filter(candidate -> !candidate.getId().equals(column.getId()))
            .filter(candidate -> candidate.getFallbackForCategory() == null)
            .findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException(
                "Cannot remove the last column backing the '" + category + "' category"));

        successor.setFallbackForCategory(category);
        column.setFallbackForCategory(null);
    }

    private int clamp(int position, int size) {
        return Math.max(0, Math.min(position, size));
    }

    private void reindex(List<BoardColumn> columns) {
        for (int index = 0; index < columns.size(); index++) {
            columns.get(index).setPosition(index);
        }
    }

    private BoardColumnView toView(BoardColumn column) {
        List<String> explicitStatusIds = boardColumnStatusRepository.findByBoardColumnId(column.getId()).stream()
            .map(BoardColumnStatus::getStatusId)
            .toList();
        return BoardColumnView.from(column, explicitStatusIds);
    }

    private Board requireAdminister(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.ADMINISTER_PROJECT);
        return boardService.requireBoard(projectId);
    }

    private BoardColumn requireColumn(String columnId, String boardId) {
        BoardColumn column = boardColumnRepository.findById(columnId)
            .orElseThrow(() -> new ResourceNotFoundException("Board column not found: " + columnId));

        if (!column.getBoardId().equals(boardId)) {
            throw new ResourceNotFoundException("Board column not found: " + columnId);
        }

        return column;
    }

}
