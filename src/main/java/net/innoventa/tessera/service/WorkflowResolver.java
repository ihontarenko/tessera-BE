package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.WorkflowScheme;
import net.innoventa.tessera.domain.WorkflowSchemeItem;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.repository.TransitionRepository;
import net.innoventa.tessera.repository.WorkflowSchemeItemRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves which {@link net.innoventa.tessera.domain.Workflow} governs an issue and answers the
 * data-driven questions the workflow engine asks of it (ADR-0005): what status a new issue lands in,
 * whether a given {@code (from → to)} edge is legal, and what moves are available from a status. The
 * workflow for an issue is the project's {@link WorkflowScheme} default, unless a per-type override
 * exists — never a {@code type ==} branch.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowResolver {

    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeItemRepository workflowSchemeItemRepository;
    private final TransitionRepository transitionRepository;
    private final StatusRepository statusRepository;

    /** The workflow an issue of {@code issueTypeId} runs under in {@code project}. */
    public String resolveWorkflowId(Project project, String issueTypeId) {
        WorkflowScheme scheme = workflowSchemeRepository.findById(project.getWorkflowSchemeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Workflow scheme not found: " + project.getWorkflowSchemeId()));

        return workflowSchemeItemRepository.findBySchemeIdAndIssueTypeId(scheme.getId(), issueTypeId)
            .map(WorkflowSchemeItem::getWorkflowId)
            .orElse(scheme.getDefaultWorkflowId());
    }

    /** The status a newly-created issue lands in — the target of the create (null-from) transition. */
    public Status initialStatus(String workflowId) {
        Transition create = transitionRepository.findFirstByWorkflowIdAndFromStatusIdIsNull(workflowId)
            .orElseThrow(() -> new IllegalStateException(
                "Workflow has no create transition: " + workflowId));

        return requireStatus(create.getToStatusId());
    }

    /** Whether the {@code (from → to)} edge exists in the workflow — the legality check (ADR-0005). */
    public boolean transitionExists(String workflowId, String fromStatusId, String toStatusId) {
        return transitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId);
    }

    /** The legal moves out of a status — the "available transitions" the detail view offers. */
    public List<Transition> availableTransitions(String workflowId, String fromStatusId) {
        return transitionRepository.findByWorkflowIdAndFromStatusId(workflowId, fromStatusId);
    }

    public Status requireStatus(String statusId) {
        return statusRepository.findById(statusId)
            .orElseThrow(() -> new ResourceNotFoundException("Status not found: " + statusId));
    }

}
