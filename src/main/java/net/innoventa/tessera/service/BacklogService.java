package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.backlog.BacklogIssueView;
import net.innoventa.tessera.dto.backlog.BacklogPanelView;
import net.innoventa.tessera.dto.backlog.BacklogResponse;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The backlog screen in one read (Phase-3 ticket 02): the running sprint's panel, a panel per planned
 * sprint, and the product backlog — each already carrying its issues, its count and its story-point
 * total. Visibility is the membership gate every project-scoped read uses (non-member {@code 404},
 * member without {@code BROWSE_PROJECT} {@code 403}).
 * <p>
 * <strong>Every project has one</strong> (ADR-0016). The scope strategy decides whether sprints exist,
 * not whether a backlog does, so nothing here refuses to answer for a board showing all issues.
 * <p>
 * What lands in the backlog list is exactly: planning units (ADR-0014), still open
 * ({@code resolution IS NULL}, ADR-0004 — never judged by status name), that <strong>the board does not
 * render</strong> — see {@link #boardRenders}. Board and backlog are complementary by construction, so
 * an issue is on one or the other and never both. Sub-tasks and epics are absent by construction.
 * <p>
 * A sprint panel, by contrast, shows every current member <em>including</em> issues completed inside it
 * — a sprint's contents are what was committed, not what is left. Membership is asked of
 * {@link SprintMembershipService} rather than re-derived here, which is the whole point of that service
 * existing, and only a sprint-planning project is asked at all. Every list is ordered by the single
 * global LexoRank (ADR-0006); the panels are disjoint slices of it, so interleaving across panels is
 * invisible.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacklogService {

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final StatusRepository statusRepository;
    private final PriorityRepository priorityRepository;
    private final MemberRepository memberRepository;

    private final ProjectService projectService;
    private final MemberService memberService;
    private final BoardService boardService;
    private final BoardColumnResolver boardColumnResolver;
    private final SprintMembershipService sprintMembershipService;

    public BacklogResponse getBacklog(Jwt jwt, String projectId) {
        memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        return render(projectId);
    }

    /**
     * The screen as it now stands, with no permission check of its own — the shape the move endpoint
     * returns once it has already gated the write, so panel counts and totals come back from the same
     * code that produced them on load rather than being recomputed by the client. Called from inside the
     * move's read-write transaction, which it joins.
     */
    BacklogResponse render(String projectId) {
        Board board = boardService.requireBoard(projectId);
        boolean plansInSprints = board.getScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT;

        List<Issue> issues = issueRepository.findByProjectIdOrderByRankAsc(projectId);
        Catalogs catalogs = loadCatalogs(issues);
        BoardService.ColumnMapping mapping = boardService.loadColumnMapping(board.getId());

        // A board showing all issues has no sprint panels to fill, so it never asks about membership —
        // and its backlog is decided by column mapping alone.
        Map<String, SprintIssue> liveMemberships = plansInSprints
            ? sprintMembershipService.liveMembershipsByIssue(projectId)
            : Map.of();

        Map<String, List<Issue>> committedBySprint = new HashMap<>();
        List<Issue> backlog = new ArrayList<>();

        for (Issue issue : issues) {
            if (!PlanningUnit.isPlanningUnit(catalogs.types.get(issue.getIssueTypeId()))) {
                continue;
            }

            SprintIssue membership = liveMemberships.get(issue.getId());
            if (membership != null) {
                committedBySprint.computeIfAbsent(membership.getSprintId(), sprintId -> new ArrayList<>()).add(issue);
            } else if (issue.getResolutionId() == null && !boardRenders(board, issue, catalogs, mapping)) {
                backlog.add(issue);
            }
        }

        List<Sprint> openSprints = plansInSprints ? sprintMembershipService.openSprints(projectId) : List.of();

        BacklogPanelView activePanel = openSprints.stream()
            .filter(sprint -> sprint.getState() == SprintState.ACTIVE)
            .findFirst()
            .map(sprint -> panel(sprint, committedBySprint, catalogs))
            .orElse(null);

        List<BacklogPanelView> futurePanels = openSprints.stream()
            .filter(sprint -> sprint.getState() == SprintState.FUTURE)
            .map(sprint -> panel(sprint, committedBySprint, catalogs))
            .toList();

        return new BacklogResponse(
            projectId,
            board.getScopeStrategy(),
            activePanel,
            futurePanels,
            BacklogPanelView.of(null, rows(backlog, catalogs))
        );
    }

    /**
     * Does the board render this open, uncommitted planning unit? ADR-0016 makes the backlog the exact
     * complement of that answer, so this one predicate <em>is</em> the membership rule.
     * <p>
     * An {@code ACTIVE_SPRINT} board draws only from the running sprint, so an issue that reached here —
     * holding no live membership — is off it whatever its status. An {@code ALL_ISSUES} board draws from
     * the whole project, so the only thing that can keep an issue off it is a status its columns map
     * nowhere. That is why unmapping a status is the administrator's control over what the backlog
     * contains, and why a freshly seeded board — every reachable status mapped — starts with none.
     */
    private boolean boardRenders(Board board, Issue issue, Catalogs catalogs, BoardService.ColumnMapping mapping) {
        if (board.getScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT) {
            return false;
        }

        return boardColumnResolver.rendersStatus(
            catalogs.statuses.get(issue.getStatusId()), mapping.columns(), mapping.statusToColumn());
    }

    private BacklogPanelView panel(Sprint sprint, Map<String, List<Issue>> committedBySprint, Catalogs catalogs) {
        List<Issue> members = committedBySprint.getOrDefault(sprint.getId(), List.of());

        return BacklogPanelView.of(SprintSummary.from(sprint), rows(members, catalogs));
    }

    private List<BacklogIssueView> rows(List<Issue> issues, Catalogs catalogs) {
        return issues.stream()
            .map(issue -> new BacklogIssueView(
                issue.getId(),
                issue.getIssueKey(),
                issue.getSummary(),
                IssueTypeSummary.from(catalogs.types.get(issue.getIssueTypeId())),
                PrioritySummary.from(catalogs.priorities.get(issue.getPriorityId())),
                memberSummary(catalogs, issue.getAssigneeMemberId()),
                issue.getStoryPoints(),
                issue.getResolutionId() == null,
                issue.getRank()))
            .toList();
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
            issueTypeRepository.findAll().stream().collect(Collectors.toMap(IssueType::getId, Function.identity())),
            statusRepository.findAll().stream().collect(Collectors.toMap(Status::getId, Function.identity())),
            priorityRepository.findAll().stream().collect(Collectors.toMap(Priority::getId, Function.identity())),
            memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, Function.identity()))
        );
    }

    /** The small global catalogs a row references, batched once so no row costs a query. */
    private record Catalogs(
        Map<String, IssueType> types,
        Map<String, Status> statuses,
        Map<String, Priority> priorities,
        Map<String, Member> members
    ) {
    }

}
