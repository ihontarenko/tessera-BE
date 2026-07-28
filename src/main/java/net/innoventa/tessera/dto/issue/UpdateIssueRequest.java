package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Edit an issue's directly-editable fields — summary, description, priority, assignee, story points
 * (ticket 07). Type is deliberately <strong>not</strong> editable here: it is fixed at creation, so a
 * later edit cannot silently break the hierarchy invariant against the issue's parent or children
 * (ticket 10). Status/resolution move through the workflow, parent through the hierarchy endpoint, and
 * labels/components/versions through the organization endpoint. A change to {@code assigneeMemberId}
 * additionally requires {@code ASSIGN_ISSUE}; the rest require {@code EDIT_ISSUE}.
 */
public record UpdateIssueRequest(
    @NotBlank @Size(max = 255) String summary,
    @Size(max = 4000) String description,
    @NotBlank String priorityId,
    String assigneeMemberId,
    @PositiveOrZero Double storyPoints
) {
}
