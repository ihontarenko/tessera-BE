package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.backlog.BacklogMoveRequest;
import net.innoventa.tessera.dto.backlog.BacklogResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Every drag on the backlog screen (Phase-3 ticket 03) — backlog → sprint, sprint → sprint,
 * sprint → backlog and reordering within any one of them — through a single endpoint that changes
 * membership and rank in <strong>one transaction</strong>. Mirrors the board's move, and for the same
 * reason: two calls would expose a state where an issue is committed but unplaced.
 * <p>
 * Permissions split exactly as the board's move does. A drop that changes which list the issue is in is
 * a re-plan and needs {@code MANAGE_SPRINT}; a drop that only reorders within one list is prioritising
 * and needs {@code EDIT_ISSUE}, so a member who cannot run the ceremony can still express priority. No
 * new permission catalog rows exist for any of this — {@code MANAGE_SPRINT} was seeded in Phase 1.
 * <p>
 * Committing an epic or a sub-task is refused with a {@code 409} rather than silently ignored, checked
 * against {@code hierarchyLevel} and never a type name (ADR-0014). The response is the whole screen,
 * because a move changes two panels' counts and totals and the client should not have to agree with the
 * server about arithmetic.
 */
@Service
@RequiredArgsConstructor
public class BacklogMoveService {

    private final IssueTypeRepository issueTypeRepository;

    private final ProjectService projectService;
    private final ProjectAccess projectAccess;
    private final MemberService memberService;
    private final IssueService issueService;
    private final SprintService sprintService;
    private final SprintMembershipService sprintMembershipService;
    private final BacklogService backlogService;
    private final RankService rankService;

    @Transactional
    public BacklogResponse move(Jwt jwt, String projectId, BacklogMoveRequest request) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        Issue issue = issueService.requireIssueInProject(request.issueKey(), projectId);
        Sprint targetSprint = resolveTargetSprint(projectId, request.targetSprintId());

        String currentSprintId = sprintMembershipService.liveMembership(issue.getId())
            .map(SprintIssue::getSprintId)
            .orElse(null);
        String targetSprintId = targetSprint == null ? null : targetSprint.getId();
        boolean membershipChanges = !Objects.equals(currentSprintId, targetSprintId);

        // ⚠️ What a drag costs depends on where it lands, which is why this is here and not on the route:
        // committing work to a sprint is a planning act, reordering the backlog is an edit.
        if (membershipChanges) {
            projectAccess.require(caller, projectId, Permissions.MANAGE_SPRINT);
        } else {
            projectAccess.require(caller, projectId, Permissions.EDIT_ISSUE);
        }

        if (targetSprint != null) {
            requirePlanningUnit(issue);
        }

        // Rank and membership are written together: a drag expresses both at once, and half of it is a
        // state the screen cannot render.
        issue.setRank(rankService.between(
            issueService.neighbourRank(request.beforeIssueKey(), projectId),
            issueService.neighbourRank(request.afterIssueKey(), projectId)));

        if (membershipChanges) {
            sprintMembershipService.moveToSprint(issue, targetSprint, caller);
        }

        return backlogService.render(projectId);
    }

    /**
     * The sprint a drop landed on, or null for the product backlog. A closed sprint is refused: its
     * membership rows are its report, and dragging work into finished history is never what was meant.
     */
    private Sprint resolveTargetSprint(String projectId, String targetSprintId) {
        if (targetSprintId == null) {
            return null;
        }

        Sprint sprint = sprintService.requireSprintInProject(projectId, targetSprintId);
        if (sprint.getState() == SprintState.CLOSED) {
            throw new BusinessRuleViolationException("Sprint '" + sprint.getName() + "' is closed");
        }

        return sprint;
    }

    /** ADR-0014: only a hierarchy-level-0 issue may hold sprint membership. */
    private void requirePlanningUnit(Issue issue) {
        IssueType type = issueTypeRepository.findById(issue.getIssueTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("Issue type not found: " + issue.getIssueTypeId()));

        if (!PlanningUnit.isPlanningUnit(type)) {
            throw new BusinessRuleViolationException(PlanningUnit.isSubTask(type)
                ? "A sub-task is committed through its parent, not on its own: " + issue.getIssueKey()
                : "An epic is a container and is never committed to a sprint: " + issue.getIssueKey());
        }
    }

}
