package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.CreateBoardColumnRequest;
import net.innoventa.tessera.dto.board.SetColumnFallbackRequest;
import net.innoventa.tessera.dto.board.UpdateBoardColumnRequest;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Board column configuration (Phase-2 ticket 03) — create/rename/reorder/delete a column, set its soft
 * WIP limits, assign/clear which category it is the fallback for, and explicitly map/unmap a status onto
 * it. Every mutation requires {@code ADMINISTER_PROJECT} — the shared gate in
 * {@link BoardService#requireAdministrableBoard}; reads (the board itself) stay in {@link BoardService}.
 * <p>
 * The one invariant left is that a category has <strong>at most one</strong> fallback column: assigning
 * the role atomically strips it from whichever column held it. It used to be <em>exactly</em> one
 * (ADR-0010), with clearing the role — or deleting the column holding it — refused unless a sibling
 * could take over. ADR-0016 drops that half: a category with no fallback column simply means the board
 * does not render its statuses, and those issues are the backlog. Leaving a category off the board is
 * the point of the control, so it is no longer something to defend against.
 */
@Service
@RequiredArgsConstructor
public class BoardColumnService {

    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final StatusRepository statusRepository;
    private final BoardService boardService;
    private final Supplier<String> idGenerator;

    @Transactional
    public BoardColumnView create(Jwt jwt, String projectId, CreateBoardColumnRequest request) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
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
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        column.setName(request.name());
        column.setMinIssues(request.minIssues());
        column.setMaxIssues(request.maxIssues());

        return toView(column);
    }

    @Transactional
    public BoardColumnView reorder(Jwt jwt, String projectId, String columnId, int newPosition) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        List<BoardColumn> columns = new ArrayList<>(
            boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()));
        columns.remove(column);
        columns.add(clamp(newPosition, columns.size()), column);
        reindex(columns);
        boardColumnRepository.saveAll(columns);

        return toView(column);
    }

    /**
     * Remove a column, with its explicit status mappings. Whatever it was the fallback for is left
     * without one, and whatever it explicitly mapped falls back by category — either way any status
     * that now maps nowhere moves to the backlog rather than disappearing (ADR-0016).
     */
    @Transactional
    public void delete(Jwt jwt, String projectId, String columnId) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        boardColumnStatusRepository.deleteAll(boardColumnStatusRepository.findByBoardColumnId(columnId));
        boardColumnRepository.delete(column);

        List<BoardColumn> remaining = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId());
        reindex(remaining);
        boardColumnRepository.saveAll(remaining);
    }

    /**
     * Make this column the category's fallback home, taking the role off whichever column held it —
     * that swap is what keeps "at most one fallback per category" true. The category this column backed
     * before is simply left without one, and its statuses move to the backlog.
     */
    @Transactional
    public BoardColumnView setFallback(Jwt jwt, String projectId, String columnId, SetColumnFallbackRequest request) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        BoardColumn column = requireColumn(columnId, board.getId());

        boardColumnRepository.findByBoardIdAndFallbackForCategory(board.getId(), request.category())
            .filter(holder -> !holder.getId().equals(column.getId()))
            .ifPresent(holder -> holder.setFallbackForCategory(null));

        column.setFallbackForCategory(request.category());

        return toView(column);
    }

    /**
     * Stop this column being any category's fallback home. The category is then rendered by no column,
     * which is how an administrator decides that its statuses belong in the backlog (ADR-0016) — the
     * whole reason this no longer hunts for a successor.
     */
    @Transactional
    public void clearFallback(Jwt jwt, String projectId, String columnId) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);

        requireColumn(columnId, board.getId()).setFallbackForCategory(null);
    }

    @Transactional
    public void mapStatus(Jwt jwt, String projectId, String columnId, String statusId) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
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
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        requireColumn(columnId, board.getId());

        BoardColumnStatus mapping = boardColumnStatusRepository.findByBoardIdAndStatusId(board.getId(), statusId)
            .filter(entry -> entry.getBoardColumnId().equals(columnId))
            .orElseThrow(() -> new ResourceNotFoundException("Status is not explicitly mapped to this column"));

        boardColumnStatusRepository.delete(mapping);
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

    private BoardColumn requireColumn(String columnId, String boardId) {
        BoardColumn column = boardColumnRepository.findById(columnId)
            .orElseThrow(() -> new ResourceNotFoundException("Board column not found: " + columnId));

        if (!column.getBoardId().equals(boardId)) {
            throw new ResourceNotFoundException("Board column not found: " + columnId);
        }

        return column;
    }

}
