package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.sprint.CreateSprintRequest;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.dto.sprint.StartSprintRequest;
import net.innoventa.tessera.dto.sprint.UpdateSprintRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
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
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final SprintMembershipService sprintMembershipService;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<SprintSummary> list(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

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

    /** The {@code MANAGE_SPRINT} gate every sprint mutation shares, returning the acting member. */
    private Member requireSprintManager(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.MANAGE_SPRINT);

        return caller;
    }

}
