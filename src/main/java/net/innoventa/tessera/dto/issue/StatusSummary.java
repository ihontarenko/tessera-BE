package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;

/** Compact status projection for issue payloads — carries {@code category} so boards/UI can group. */
public record StatusSummary(String id, String name, StatusCategory category) {

    public static StatusSummary from(Status status) {
        return status == null
            ? null
            : new StatusSummary(status.getId(), status.getName(), status.getCategory());
    }

}
