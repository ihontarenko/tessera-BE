package net.innoventa.tessera.dto.configuration;

import net.innoventa.tessera.domain.StatusCategory;

import java.util.List;

/**
 * The whole global configuration in one payload — what the UI loads once to render issue-type
 * pickers, priority/status/resolution dropdowns, and scheme selectors without a call per catalog.
 */
public record ConfigurationResponse(
    List<IssueTypeResponse> issueTypes,
    List<PriorityResponse> priorities,
    List<StatusCategory> statusCategories,
    List<StatusResponse> statuses,
    List<ResolutionResponse> resolutions,
    List<WorkflowResponse> workflows,
    List<IssueTypeSchemeResponse> issueTypeSchemes,
    List<WorkflowSchemeResponse> workflowSchemes,
    List<EstimationSchemeResponse> estimationSchemes
) {
}
