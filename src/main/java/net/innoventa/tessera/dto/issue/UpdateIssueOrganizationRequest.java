package net.innoventa.tessera.dto.issue;

import java.util.List;

/**
 * Replace an issue's organization associations in one call (ticket 11): free-text {@code labels}
 * (created on the fly), {@code componentIds}, and the two distinct version associations
 * {@code affectsVersionIds} and {@code fixVersionIds}. Each list is a full replacement of that
 * association; a null list is treated as empty. Components and versions must belong to the issue's
 * project. Requires {@code EDIT_ISSUE}.
 */
public record UpdateIssueOrganizationRequest(
    List<String> labels,
    List<String> componentIds,
    List<String> affectsVersionIds,
    List<String> fixVersionIds
) {

    public List<String> labelsOrEmpty() {
        return labels == null ? List.of() : labels;
    }

    public List<String> componentIdsOrEmpty() {
        return componentIds == null ? List.of() : componentIds;
    }

    public List<String> affectsVersionIdsOrEmpty() {
        return affectsVersionIds == null ? List.of() : affectsVersionIds;
    }

    public List<String> fixVersionIdsOrEmpty() {
        return fixVersionIds == null ? List.of() : fixVersionIds;
    }

}
