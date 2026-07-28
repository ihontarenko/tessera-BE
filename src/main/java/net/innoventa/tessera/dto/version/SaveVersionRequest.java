package net.innoventa.tessera.dto.version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.VersionState;

import java.time.LocalDate;

/**
 * Create or edit a version (ticket 06): a name unique within its project, an optional description and
 * release date, and a required {@link VersionState} lifecycle (never released/archived booleans).
 * Requires {@code ADMINISTER_PROJECT}.
 */
public record SaveVersionRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 255) String description,
    LocalDate releaseDate,
    @NotNull VersionState state
) {
}
