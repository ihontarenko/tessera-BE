package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotBlank;

/**
 * Move an issue to {@code toStatusId} through the workflow (ticket 09). {@code resolutionId} is
 * required when — and only when — the target is a DONE-category status; it is ignored (and the
 * resolution cleared) when moving out of DONE. Illegal edge → 409, missing {@code TRANSITION_ISSUE}
 * → 403, DONE without a resolution → 409.
 */
public record TransitionIssueRequest(
    @NotBlank String toStatusId,
    String resolutionId
) {
}
