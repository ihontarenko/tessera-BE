package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads a project's board (Phase-2 ticket 01): the board configuration plus a flat, column-resolved
 * card list. Visibility is the same membership gate every project-scoped read uses — a non-member gets
 * a {@code 404}, a member without {@code BROWSE_PROJECT} a {@code 403}
 * ({@link ProjectPermissionService#requireVisible}).
 * <p>
 * The render is denormalised for the hot path: the small global catalogs (statuses, types, priorities)
 * and the referenced members are batched into maps once, and each card's column is resolved from
 * already-prefetched inputs by {@link BoardColumnResolver} — no per-card query.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final IssueRepository issueRepository;
    private final StatusRepository statusRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final MemberRepository memberRepository;

    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final BoardColumnResolver boardColumnResolver;

    public BoardResponse getBoard(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        Board board = requireBoard(projectId);
        ColumnMapping mapping = loadColumnMapping(board.getId());

        List<Issue> issues = issueRepository.findByProjectIdOrderByRankAsc(projectId);
        Catalogs catalogs = loadCatalogs(issues);

        List<BoardCardView> cards = issues.stream()
            .map(issue -> toCard(issue, mapping.columns(), mapping.statusToColumn(), catalogs))
            .toList();

        Map<String, List<String>> explicitStatusesByColumn = mapping.statusToColumn().entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        List<BoardColumnView> columnViews = mapping.columns().stream()
            .map(column -> BoardColumnView.from(column, explicitStatusesByColumn.getOrDefault(column.getId(), List.of())))
            .toList();

        return new BoardResponse(
            board.getId(),
            board.getProjectId(),
            board.getName(),
            board.getSwimlaneStrategy(),
            board.getHideDoneOlderThanDays(),
            columnViews,
            cards
        );
    }

    /** The board entity for a project, or {@code 404} — shared with {@link BoardMoveService}. */
    public Board requireBoard(String projectId) {
        return boardRepository.findByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Board not provisioned for project: " + projectId));
    }

    /**
     * A board's ordered columns plus its explicit status→column map, prefetched together — the two
     * inputs {@link BoardColumnResolver} needs. Shared by the full-board render and
     * {@link BoardMoveService}, which resolves a single issue's current column before a move.
     */
    public ColumnMapping loadColumnMapping(String boardId) {
        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        Map<String, String> statusToColumn = boardColumnStatusRepository.findByBoardId(boardId).stream()
            .collect(Collectors.toMap(BoardColumnStatus::getStatusId, BoardColumnStatus::getBoardColumnId));
        return new ColumnMapping(columns, statusToColumn);
    }

    /** One issue projected onto the board — the single-card counterpart of {@link #getBoard}, used to
     *  shape the response of a move without re-rendering the whole board. */
    public BoardCardView cardView(Issue issue, ColumnMapping mapping) {
        return toCard(issue, mapping.columns(), mapping.statusToColumn(), loadCatalogs(List.of(issue)));
    }

    /** A board's prefetched columns and explicit status→column mapping (ADR-0010's two pure-function inputs). */
    public record ColumnMapping(List<BoardColumn> columns, Map<String, String> statusToColumn) {
    }

    private BoardCardView toCard(
        Issue issue,
        List<BoardColumn> columns,
        Map<String, String> statusToColumn,
        Catalogs catalogs
    ) {
        Status status = catalogs.statuses.get(issue.getStatusId());
        String columnId = boardColumnResolver.resolveColumnId(status, columns, statusToColumn);

        return new BoardCardView(
            issue.getId(),
            issue.getIssueKey(),
            issue.getSummary(),
            IssueTypeSummary.from(catalogs.types.get(issue.getIssueTypeId())),
            PrioritySummary.from(catalogs.priorities.get(issue.getPriorityId())),
            StatusSummary.from(status),
            memberSummary(catalogs, issue.getAssigneeMemberId()),
            issue.getResolutionId() == null,
            columnId,
            issue.getRank(),
            issue.getAssigneeMemberId(),
            issue.getPriorityId()
        );
    }

    private MemberSummary memberSummary(Catalogs catalogs, String memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = catalogs.members.get(memberId);
        return member == null ? null : MemberSummary.from(member);
    }

    private Catalogs loadCatalogs(List<Issue> issues) {
        List<String> memberIds = issues.stream()
            .map(Issue::getAssigneeMemberId)
            .filter(memberId -> memberId != null)
            .distinct()
            .toList();

        return new Catalogs(
            statusRepository.findAll().stream().collect(Collectors.toMap(Status::getId, Function.identity())),
            issueTypeRepository.findAll().stream().collect(Collectors.toMap(IssueType::getId, Function.identity())),
            priorityRepository.findAll().stream().collect(Collectors.toMap(Priority::getId, Function.identity())),
            memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, Function.identity()))
        );
    }

    /** The batched global catalogs plus the members the board's cards reference — no per-card lookup. */
    private record Catalogs(
        Map<String, Status> statuses,
        Map<String, IssueType> types,
        Map<String, Priority> priorities,
        Map<String, Member> members
    ) {
    }

}
