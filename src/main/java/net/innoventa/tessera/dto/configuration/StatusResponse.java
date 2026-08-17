package net.innoventa.tessera.dto.configuration;

import net.innoventa.tessera.domain.StatusCategory;

public record StatusResponse(
    String id,
    String name,
    StatusCategory category,
    /** A CSS colour, or null to be drawn from the category. */
    String color
) {
}
