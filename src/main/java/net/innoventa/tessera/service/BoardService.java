package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.dto.sprint.ActiveSprintView;
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
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EpicResolver epicResolver;
    private final SprintService sprintService;
    private final SprintMembershipService sprintMembershipService;

    public BoardResponse getBoard(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        Board board = requireBoard(projectId);
        ColumnMapping mapping = loadColumnMapping(board.getId());

        // Only a sprint-scoped board asks about sprints at all, so an ALL_ISSUES board renders through
        // exactly the code path it did before this phase — one fewer query, and no behaviour to regress.
        Sprint activeSprint = board.getScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT
            ? sprintService.activeSprint(projectId).orElse(null)
            : null;

        List<Issue> issues = issuesInScope(board, projectId, activeSprint);
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
            board.getScopeStrategy(),
            board.getHideDoneOlderThanDays(),
            activeSprint == null ? null : ActiveSprintView.from(activeSprint, LocalDate.now()),
            columnViews,
            cards
        );
    }

    /**
     * The issues the board draws from — the single thing a scope strategy changes (ADR-0012).
     * {@code ALL_ISSUES} is the whole project, exactly as before sprints existed. {@code ACTIVE_SPRINT}
     * narrows to the running sprint's current members plus <em>their sub-tasks</em>: a sub-task is never
     * separately committed but it is not invisible either, so it rides onto the board on its parent's
     * commitment (ADR-0014). With no sprint running the board holds nothing — zero cards, not an error.
     * <p>
     * Everything downstream — column resolution, WIP counts, swimlanes, quick filters, done-threshold
     * hiding — is untouched; only this set narrows.
     */
    private List<Issue> issuesInScope(Board board, String projectId, Sprint activeSprint) {
        List<Issue> projectIssues = issueRepository.findByProjectIdOrderByRankAsc(projectId);

        if (board.getScopeStrategy() != BoardScopeStrategy.ACTIVE_SPRINT) {
            return projectIssues;
        }

        if (activeSprint == null) {
            return List.of();
        }

        Set<String> committedIssueIds = sprintMembershipService.currentMembers(activeSprint.getId()).stream()
            .map(SprintIssue::getIssueId)
            .collect(Collectors.toSet());

        return projectIssues.stream()
            .filter(issue -> committedIssueIds.contains(issue.getId())
                || committedIssueIds.contains(issue.getParentId()))
            .toList();
    }

    /** The board entity for a project, or {@code 404} — shared with {@link BoardMoveService}. */
    public Board requireBoard(String projectId) {
        return boardRepository.findByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Board not provisioned for project: " + projectId));
    }

    /**
     * The project's board behind the {@code ADMINISTER_PROJECT} gate every board-configuration mutation
     * shares — {@link BoardColumnService}'s column reshaping and {@link BoardSettingsService}'s view
     * settings alike. A non-member gets a {@code 404} from {@link ProjectService#requireProject} /
     * membership resolution, a member without the permission a {@code 403}.
     */
    public Board requireAdministrableBoard(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.ADMINISTER_PROJECT);
        return requireBoard(projectId);
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
            issue.getPriorityId(),
            catalogs.epicKeys.get(issue.getId()),
            issue.getResolvedAt()
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

        Map<String, IssueType> types = issueTypeRepository.findAll().stream()
            .collect(Collectors.toMap(IssueType::getId, Function.identity()));

        return new Catalogs(
            statusRepository.findAll().stream().collect(Collectors.toMap(Status::getId, Function.identity())),
            types,
            priorityRepository.findAll().stream().collect(Collectors.toMap(Priority::getId, Function.identity())),
            memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, Function.identity())),
            epicResolver.resolveAll(issues, types, ancestorLookup(issues))
        );
    }

    /**
     * Ancestor lookup for the epic walk: the already-loaded slice first, the repository only for a
     * parent outside it. A full-board render loads the whole project and a parent is always in the same
     * project ({@link IssueHierarchyService#validateParent}), so the hot path never falls through —
     * while the single-card render after a move, which loads one issue, still resolves correctly.
     */
    private Function<String, Issue> ancestorLookup(List<Issue> issues) {
        Map<String, Issue> loaded = issues.stream().collect(Collectors.toMap(Issue::getId, Function.identity()));

        return issueId -> loaded.containsKey(issueId)
            ? loaded.get(issueId)
            : issueRepository.findById(issueId).orElse(null);
    }

    /** The batched global catalogs plus the per-slice derivations the board's cards reference — no per-card lookup. */
    private record Catalogs(
        Map<String, Status> statuses,
        Map<String, IssueType> types,
        Map<String, Priority> priorities,
        Map<String, Member> members,
        Map<String, String> epicKeys
    ) {
    }

}
