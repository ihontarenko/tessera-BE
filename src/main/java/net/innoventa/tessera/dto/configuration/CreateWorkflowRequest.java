package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new workflow, <strong>with its first status</strong>.
 *
 * <p>⚠️ The initial status is not optional, and that is the create transition speaking. A workflow needs
 * exactly one edge with no source, always — without it {@code WorkflowResolver.initialStatus} throws and
 * issue creation answers 500 — so an empty workflow is not a state that can be allowed to exist even for
 * as long as it takes somebody to add a transition to it.
 *
 * <p>A workflow's statuses stay derived from its transitions: there is no {@code workflow_statuses}
 * table, so "the workflow starts with this status" and "the create transition points here" are the same
 * sentence.
 */
public record CreateWorkflowRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description,
    @NotBlank String initialStatusId,
    @Size(max = 64) String createTransitionName
) {

    /** What the create transition is called when nobody says — the seeded workflows' own word. */
    public String createTransitionNameOrDefault() {
        return createTransitionName == null || createTransitionName.isBlank() ? "Create" : createTransitionName.trim();
    }

}
