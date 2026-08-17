package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;

/**
 * Compact status projection for issue payloads — carries {@code category} so boards/UI can group, and
 * {@code color} so every pill in the product is drawn the same way from one read (TSSR-21).
 *
 * <p>⚠️ <strong>Null {@code color} means "draw it from the category"</strong>, which is what a client
 * did before the field existed. It is never a colour to substitute for.
 */
public record StatusSummary(String id, String name, StatusCategory category, String color) {

    public static StatusSummary from(Status status) {
        return status == null
            ? null
            : new StatusSummary(status.getId(), status.getName(), status.getCategory(), status.getColor());
    }

}
