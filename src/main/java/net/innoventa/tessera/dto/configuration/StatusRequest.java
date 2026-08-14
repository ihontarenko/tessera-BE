package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.StatusCategory;

/**
 * A workflow state as an administrator states it.
 *
 * <p>The category is required and has no default. There is no sensible one: guessing {@code TODO} for a
 * status somebody means as terminal would put every issue in it on the wrong side of "closed", and a
 * field this consequential should be chosen rather than inherited.
 */
public record StatusRequest(
    @NotBlank @Size(max = 64) String name,
    @NotNull StatusCategory category
) {
}
