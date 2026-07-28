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
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final WorkflowResolver workflowResolver;
    private final ActivityLogService activityLogService;
    private final IssueCatalog issueCatalog;
    private final IssueAssembler issueAssembler;

    @Transactional
    public IssueResponse transition(Jwt jwt, String issueId, TransitionIssueRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.TRANSITION_ISSUE);

        Status target = workflowResolver.requireStatus(request.toStatusId());
        String workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());

        if (!workflowResolver.transitionExists(workflowId, issue.getStatusId(), target.getId())) {
            throw new BusinessRuleViolationException(
                "Illegal transition: no edge to '" + target.getName() + "' from the issue's current status");
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

        activityLogService.record(issue.getId(), caller.getId(), activityLogService.changeSet()
            .compare(FIELD_STATUS, oldStatusName, target.getName())
            .compare(FIELD_RESOLUTION, oldResolutionName, issueCatalog.resolutionName(newResolutionId)));

        return issueAssembler.detail(issue, project);
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
