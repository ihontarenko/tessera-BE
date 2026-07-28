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
import net.innoventa.tessera.dto.filter.FilterPreviewView;
import net.innoventa.tessera.dto.sprint.ActiveSprintView;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.exception.FilterExpressionException;
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
import net.innoventa.tessera.service.filter.BoardFilterEvaluator;
import net.innoventa.tessera.service.filter.IssueFilterView;
import net.innoventa.tessera.service.filter.IssueFilterViewFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final IssueFilterViewFactory issueFilterViewFactory;
    private final BoardFilterEvaluator boardFilterEvaluator;

    /**
     * @param filter an optional jME predicate over one issue (ADR-0008), e.g.
     *               {@code issue.assignee == currentMember}. Null or blank renders the whole scope.
     *               Only the expression travels — the issues are already here — which is what lets a
     *               filter outlive the client-side stub it replaced and, later, move into SQL.
     */
    public BoardResponse getBoard(Jwt jwt, String projectId, String filter) {
        LoadedBoard loaded = loadBoard(jwt, projectId);

        Board board = loaded.board();
        List<Issue> issues = loaded.issues();
        Catalogs catalogs = loaded.catalogs();
        Sprint activeSprint = loaded.activeSprint();
        List<Issue> committed = issues.stream()
            .filter(issue -> loaded.committedIssueIds().contains(issue.getId()))
            .toList();

        ColumnMapping mapping = loadColumnMapping(board.getId());
        List<String> matchedCardIds = matchedCardIds(filter, issues, catalogs, loaded.caller());

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
            activeSprint == null ? null : ActiveSprintView.from(
                activeSprint,
                LocalDate.now(),
                (int) committed.stream().filter(issue -> issue.getResolutionId() != null).count(),
                (int) committed.stream().filter(issue -> issue.getResolutionId() == null).count()),
            columnViews,
            cards,
            matchedCardIds
        );
    }

    /**
     * The board and everything derived from it, behind the membership gate every project-scoped read
     * shares. Extracted so the filter editor's preview narrows <em>the caller's own board</em> — the
     * same scope, the same permissions, the same slice — rather than growing a second, subtly different
     * idea of what a project's issues are.
     */
    private LoadedBoard loadBoard(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        Board board = requireBoard(projectId);

        // Only a sprint-scoped board asks about sprints at all, so an ALL_ISSUES board renders through
        // exactly the code path it did before this phase — one fewer query, and no behaviour to regress.
        Sprint activeSprint = board.getScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT
            ? sprintService.activeSprint(projectId).orElse(null)
            : null;

        // Read once and reused twice — the board's issue source narrows to it, and the header's
        // done / not-done split is counted over it. An ALL_ISSUES board never asks.
        Set<String> committedIssueIds = activeSprint == null
            ? Set.of()
            : sprintMembershipService.currentMembers(activeSprint.getId()).stream()
                .map(SprintIssue::getIssueId)
                .collect(Collectors.toSet());

        List<Issue> issues = issuesInScope(board, projectId, activeSprint, committedIssueIds);

        return new LoadedBoard(caller, board, activeSprint, committedIssueIds, issues, loadCatalogs(issues));
    }

    /**
     * Try an expression against the caller's own board without applying it — what the filter editor
     * needs to say "valid, matches 4 of 37" while someone is still typing.
     * <p>
     * It answers rather than throws for an unusable expression: a half-typed predicate is the normal
     * state of a text field, so a 400 per keystroke would be noise. Everything else about it is the
     * board read — same gate, same slice — so a preview can no more see a foreign issue than a render
     * can, and the count it reports is the count the member would actually get.
     */
    public FilterPreviewView previewFilter(Jwt jwt, String projectId, String expression) {
        LoadedBoard loaded = loadBoard(jwt, projectId);
        List<IssueFilterView> views = issueFilterViewFactory.build(loaded.issues(), loaded.catalogs().epicKeys);

        try {
            Set<String> matched = boardFilterEvaluator
                .matchingIssueIds(expression, views, loaded.caller(), Instant.now());

            return FilterPreviewView.valid(matched.size(), views.size());
        } catch (FilterExpressionException exception) {
            return FilterPreviewView.invalid(exception.getMessage(), views.size());
        }
    }

    /** One gated load of a project's board: the caller, the board, its sprint, its issues, its catalogs. */
    private record LoadedBoard(
        Member caller,
        Board board,
        Sprint activeSprint,
        Set<String> committedIssueIds,
        List<Issue> issues,
        Catalogs catalogs
    ) {
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
    private List<Issue> issuesInScope(
        Board board,
        String projectId,
        Sprint activeSprint,
        Set<String> committedIssueIds
    ) {
        List<Issue> projectIssues = issueRepository.findByProjectIdOrderByRankAsc(projectId);

        if (board.getScopeStrategy() != BoardScopeStrategy.ACTIVE_SPRINT) {
            return projectIssues;
        }

        if (activeSprint == null) {
            return List.of();
        }

        return projectIssues.stream()
            .filter(issue -> committedIssueIds.contains(issue.getId())
                || committedIssueIds.contains(issue.getParentId()))
            .toList();
    }

    /**
     * The cards satisfying the request's filter predicate, or {@code null} when there is no filter
     * (ADR-0008). The predicate runs <em>after</em> SQL, over the already-loaded scope, so it can only
     * ever mark what the caller could already read — a filter grants no visibility of its own.
     * <p>
     * Nothing here executes on an unfiltered render: the filter view-model, with its label/component/
     * version joins, is hydrated only when a request actually carries an expression. {@code now} is
     * resolved once, here, so every card in the run is measured against the same instant rather than
     * drifting between the first card and the last.
     */
    private List<String> matchedCardIds(String filter, List<Issue> issues, Catalogs catalogs, Member caller) {
        if (filter == null || filter.isBlank()) {
            return null;
        }

        List<IssueFilterView> views = issueFilterViewFactory.build(issues, catalogs.epicKeys);

        return List.copyOf(boardFilterEvaluator.matchingIssueIds(filter, views, caller, Instant.now()));
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
