package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The data-driven workflow engine (ADR-0005) — a thin transition check, not Spring Statemachine. A
 * move to a new status passes only if the {@code (from → to)} edge exists in the issue's workflow
 * (else 409) and the caller holds {@code TRANSITION_ISSUE} (else 403).
 * <p>
 * Resolution is the independent second axis (ADR-0004), driven entirely from the target's
 * {@link StatusCategory}, never a per-transition flag: entering a {@link StatusCategory#DONE} status
 * <em>requires</em> a resolution; leaving DONE clears it. The canonical invariant
 * {@code resolution IS NULL ⇔ open} is preserved by every path here, so every future board/report
 * query stays correct.
 */
@Service
@RequiredArgsConstructor
public class TransitionService {

    static final String FIELD_STATUS = "status";
    static final String FIELD_RESOLUTION = "resolution";

    private final IssueRepository issueRepository;
    private final ResolutionRepository resolutionRepository;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final WorkflowResolver workflowResolver;
    private final ActivityLogService activityLogService;
    private final IssueCatalog issueCatalog;
    private final IssueArchiveService issueArchiveService;
    private final IssueAssembler issueAssembler;
    private final IssueBlockers issueBlockers;

    @Transactional
    public IssueResponse transition(Jwt jwt, String issueId, TransitionIssueRequest request) {
        return transition(memberService.resolveMember(jwt), issueId, request);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse transition(Member caller, String issueId, TransitionIssueRequest request) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        // ⚠️ TRANSITION_ISSUE is gated on both ways in: the route declares it, and a tool call is
        // refused by the dispatcher against the same engine. What is left below is the workflow's own
        // refusals — a transition the scheme does not allow, a resolution the target status needs —
        // which were never authorization and stay exactly where they are.

        Status target = workflowResolver.requireStatus(request.toStatusId());
        String workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());

        if (!workflowResolver.transitionExists(workflowId, issue.getStatusId(), target.getId())) {
            throw new BusinessRuleViolationException(
                "Illegal transition: no edge to '" + target.getName() + "' from the issue's current status");
        }

        // ⚠️ A second kind of refusal, and it is deliberately not the workflow's (TSSR-41). The edge
        // exists; something else is holding this issue up. Read here as well as in the offered list,
        // because a caller that never looked at the list — the protocol, a script — still has to be told.
        String blocked = issueBlockers.refusalFor(issue, target.getCategory());

        if (blocked != null) {
            throw new BusinessRuleViolationException(blocked);
        }

        String newResolutionId = resolveResolutionForTarget(target, request.resolutionId());

        String oldStatusName = issueCatalog.statusName(issue.getStatusId());
        String oldResolutionName = issueCatalog.resolutionName(issue.getResolutionId());

        issue.setStatusId(target.getId());
        issue.setResolutionId(newResolutionId);
        // resolvedAt tracks entry into a DONE status (ADR-0011): set on the way in, cleared on the way
        // out — the same axis as resolution, driven by the target category, never a free field. Powers
        // the board's done-threshold hiding accurately, unlike updatedAt which any later edit resets.
        issue.setResolvedAt(target.getCategory() == StatusCategory.DONE ? LocalDateTime.now() : null);

        // ⚠️ Reopening takes the issue back out of the archive (TSSR-4). Archived work is finished work
        // that has been put away, so an issue that is open again cannot stay put away — it would be open
        // and invisible at once, the one state no screen in the product could explain. Same axis, same
        // path, same instant as the resolution being cleared.
        if (target.getCategory() != StatusCategory.DONE) {
            issueArchiveService.clear(issue);
        }

        activityLogService.record(issue.getId(), caller.getId(), activityLogService.changeSet()
            .compare(FIELD_STATUS, oldStatusName, target.getName())
            .compare(FIELD_RESOLUTION, oldResolutionName, issueCatalog.resolutionName(newResolutionId)));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Derive the issue's resolution from the target status category (ADR-0004/0005): entering DONE
     * requires a valid resolution; any other category clears it. The resolution is never a free field.
     */
    private String resolveResolutionForTarget(Status target, String requestedResolutionId) {
        if (target.getCategory() != StatusCategory.DONE) {
            return null;
        }

        if (requestedResolutionId == null || requestedResolutionId.isBlank()) {
            throw new BusinessRuleViolationException(
                "A resolution is required when moving an issue to a Done status");
        }

        Resolution resolution = resolutionRepository.findById(requestedResolutionId)
            .orElseThrow(() -> new ResourceNotFoundException("Resolution not found: " + requestedResolutionId));

        return resolution.getId();
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
