package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IncompleteIssueDestination;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.sprint.CompleteSprintRequest;
import net.innoventa.tessera.dto.sprint.CreateSprintRequest;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.dto.sprint.StartSprintRequest;
import net.innoventa.tessera.dto.sprint.UpdateSprintRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.SprintRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A sprint's lifecycle: plan it, correct it, abandon it, run it. The states move one way only —
 * {@code FUTURE → ACTIVE → CLOSED} — and every mutation here is gated by {@code MANAGE_SPRINT}, while
 * reads go through the same {@code requireVisible} gate every project-scoped read uses (non-member
 * {@code 404}, member without {@code BROWSE_PROJECT} {@code 403}).
 * <p>
 * Two invariants are enforced here rather than in the schema, for the portability reason recorded in
 * the {@code V000008} header: a project has at most one {@code ACTIVE} sprint, and a sprint that is
 * running or finished cannot be deleted. Both are refused with a {@code 409} — history is not erasable
 * by a misclick.
 * <p>
 * Membership belongs to {@link SprintMembershipService}; this service never touches
 * {@code sprint_issues} directly.
 */
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final SprintMembershipService sprintMembershipService;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<SprintSummary> list(Jwt jwt, String projectId) {
        memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        return sprintRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .map(SprintSummary::from)
            .toList();
    }

    /** A planned sprint: named, optionally with a goal, and deliberately with no dates at all. */
    @Transactional
    public SprintSummary create(Jwt jwt, String projectId, CreateSprintRequest request) {
        requireSprintManager(jwt, projectId);

        Sprint sprint = sprintRepository.save(Sprint.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .name(request.name())
            .goal(request.goal())
            .state(SprintState.FUTURE)
            .build());

        return SprintSummary.from(sprint);
    }

    /** Correct a sprint's name or goal at any point in its life — a rename never rewrites history. */
    @Transactional
    public SprintSummary update(Jwt jwt, String projectId, String sprintId, UpdateSprintRequest request) {
        requireSprintManager(jwt, projectId);
        Sprint sprint = requireSprintInProject(projectId, sprintId);

        sprint.setName(request.name());
        sprint.setGoal(request.goal());

        return SprintSummary.from(sprint);
    }

    /**
     * Abandon a planned sprint. Its issues return to the product backlog — their membership rows go, the
     * issues do not. A running or finished sprint is refused with a {@code 409}: closing is how a sprint
     * ends, and a closed sprint's membership rows are the sprint report.
     */
    @Transactional
    public void delete(Jwt jwt, String projectId, String sprintId) {
        Member caller = requireSprintManager(jwt, projectId);
        Sprint sprint = requireSprintInProject(projectId, sprintId);

        if (sprint.getState() != SprintState.FUTURE) {
            throw new BusinessRuleViolationException(
                "Only a future sprint can be deleted; '" + sprint.getName() + "' is " + sprint.getState());
        }

        sprintMembershipService.discardMemberships(sprint, caller);
        sprintRepository.delete(sprint);
    }

    /**
     * Start a planned sprint. The end date is required — without it the burndown has no axis — and the
     * project must have no sprint already running, so "the current sprint" is never ambiguous. Both
     * refusals are {@code 409}s, and {@code startedAt} is stamped here rather than taken from the client.
     */
    @Transactional
    public SprintSummary start(Jwt jwt, String projectId, String sprintId, StartSprintRequest request) {
        requireSprintManager(jwt, projectId);
        Sprint sprint = requireSprintInProject(projectId, sprintId);

        if (sprint.getState() != SprintState.FUTURE) {
            throw new BusinessRuleViolationException(
                "Only a future sprint can be started; '" + sprint.getName() + "' is " + sprint.getState());
        }

        if (request.endDate() == null) {
            throw new BusinessRuleViolationException("A sprint cannot be started without an end date");
        }

        activeSprint(projectId).ifPresent(running -> {
            throw new BusinessRuleViolationException(
                "Sprint '" + running.getName() + "' is already running in this project");
        });

        sprint.setState(SprintState.ACTIVE);
        sprint.setStartedAt(LocalDateTime.now());
        sprint.setEndDate(request.endDate());

        return SprintSummary.from(sprint);
    }

    /**
     * Close a running sprint (Phase-3 ticket 05). What was finished stays recorded against the sprint it
     * was finished in — the closed sprint's membership rows are not touched at all, because they are what
     * the sprint report reads. Unfinished work ({@code resolution IS NULL}, ADR-0004 — never a status
     * name) additionally gains a membership row in the destination the request names, so an issue that
     * took two sprints is visibly in both.
     * <p>
     * The destination is explicit: the server does not guess whether unfinished work falls back to the
     * backlog or rolls into a named sprint. The whole close runs in one transaction, so a failure partway
     * leaves the sprint running and every membership untouched.
     */
    @Transactional
    public SprintSummary complete(Jwt jwt, String projectId, String sprintId, CompleteSprintRequest request) {
        Member caller = requireSprintManager(jwt, projectId);
        Sprint sprint = requireSprintInProject(projectId, sprintId);

        if (sprint.getState() != SprintState.ACTIVE) {
            throw new BusinessRuleViolationException(
                "Only a running sprint can be completed; '" + sprint.getName() + "' is " + sprint.getState());
        }

        Sprint destination = resolveDestination(projectId, request);

        for (Issue issue : incompleteMembers(sprint)) {
            sprintMembershipService.carryOver(issue, sprint, destination, caller);
        }

        // One-way: nothing reopens a closed sprint, so this is the last state this row ever holds.
        sprint.setState(SprintState.CLOSED);
        sprint.setCompletedAt(LocalDateTime.now());

        return SprintSummary.from(sprint);
    }

    /**
     * Where the unfinished work goes — null for the product backlog, or the named sprint it rolls into.
     * A named destination must be a {@code FUTURE} sprint of <em>this</em> project; a closed, running,
     * unknown or foreign one is refused with a {@code 409} rather than quietly falling back to the
     * backlog, because a bad target means the caller is closing something other than what they think.
     */
    private Sprint resolveDestination(String projectId, CompleteSprintRequest request) {
        if (request.moveIncompleteTo() != IncompleteIssueDestination.SPRINT) {
            return null;
        }

        if (request.targetSprintId() == null) {
            throw new BusinessRuleViolationException(
                "Moving incomplete issues to a sprint requires naming which sprint");
        }

        Sprint destination = sprintRepository.findById(request.targetSprintId())
            .filter(candidate -> candidate.getProjectId().equals(projectId))
            .orElseThrow(() -> new BusinessRuleViolationException(
                "No sprint of this project with id " + request.targetSprintId()));

        if (destination.getState() != SprintState.FUTURE) {
            throw new BusinessRuleViolationException("Incomplete issues can only move to a future sprint; '"
                + destination.getName() + "' is " + destination.getState());
        }

        return destination;
    }

    /**
     * The sprint's current members that are still open. An issue dropped out of the sprint earlier is not
     * a member and is not carried anywhere; one finished inside the sprint stays exactly where it is.
     */
    private List<Issue> incompleteMembers(Sprint sprint) {
        List<String> memberIssueIds = sprintMembershipService.currentMembers(sprint.getId()).stream()
            .map(SprintIssue::getIssueId)
            .toList();

        return issueRepository.findAllById(memberIssueIds).stream()
            .filter(issue -> issue.getResolutionId() == null)
            .toList();
    }

    /**
     * The project's finished sprints, oldest first — velocity's series (Phase-3 ticket 07). Running and
     * future sprints are left out: a sprint measured mid-flight would report a commitment against work
     * that has not had its time yet.
     */
    @Transactional(readOnly = true)
    public List<Sprint> closedSprints(String projectId) {
        return sprintRepository.findByProjectIdAndStateOrderByStartedAtAsc(projectId, SprintState.CLOSED);
    }

    /** The project's running sprint, if one is — the read side of the one-active-sprint invariant. */
    @Transactional(readOnly = true)
    public Optional<Sprint> activeSprint(String projectId) {
        return sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE);
    }

    /** A sprint of this project, or {@code 404} — a sprint id from elsewhere is simply not found here. */
    @Transactional(readOnly = true)
    public Sprint requireSprintInProject(String projectId, String sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));

        if (!sprint.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Sprint not found: " + sprintId);
        }

        return sprint;
    }

    /**
     * The acting member, for a sprint mutation.
     *
     * <p>⚠️ It no longer checks {@code MANAGE_SPRINT} — {@code SprintController} declares it once for the
     * whole class and the engine has refused before any of this runs. The name survives because what the
     * method still asserts is worth asserting: that the caller has a member row, and that the project
     * they name exists.
     */
    private Member requireSprintManager(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        return caller;
    }

}
