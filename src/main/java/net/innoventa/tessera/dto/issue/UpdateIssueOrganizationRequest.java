package net.innoventa.tessera.dto.issue;

import java.util.List;

/**
 * Replace an issue's labels in one call (ticket 11): free-text names, created on the fly. The list is a
 * full replacement of the association rather than a delta, so the client sends the state it wants to
 * end up with; a null list is treated as empty. Requires {@code EDIT_ISSUE}.
 */
public record UpdateIssueOrganizationRequest(
    List<String> labels
) {

    /** The requested labels, with a missing list read as "no labels" rather than left null. */
    public List<String> resolveLabels() {
        return labels == null ? List.of() : labels;
    }

}
