package net.innoventa.tessera.dto.version;

import net.innoventa.tessera.domain.VersionState;

import java.time.LocalDate;

/** A version with its lifecycle state (ticket 06). */
public record VersionResponse(
    String id,
    String projectId,
    String name,
    String description,
    LocalDate releaseDate,
    VersionState state,
    int sequence
) {
}
