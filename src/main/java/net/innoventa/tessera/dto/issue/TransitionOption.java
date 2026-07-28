package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.StatusCategory;

/**
 * One legal move out of an issue's current status (ADR-0005), offered to the UI so it can render only
 * the transitions the workflow actually allows. {@code requiresResolution} is true when the target is
 * a DONE-category status, so the UI knows to prompt for a resolution before submitting.
 */
public record TransitionOption(
    String transitionId,
    String name,
    String toStatusId,
    String toStatusName,
    StatusCategory toCategory,
    boolean requiresResolution
) {
}
