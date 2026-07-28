package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotBlank;

/**
 * Link this issue to another (ticket 12): a {@code linkTypeId} and the {@code targetIssueId} on the
 * outward end. Stored as one directed row; the target renders the inward label. Requires
 * {@code EDIT_ISSUE}.
 */
public record CreateIssueLinkRequest(
    @NotBlank String linkTypeId,
    @NotBlank String targetIssueId
) {
}
