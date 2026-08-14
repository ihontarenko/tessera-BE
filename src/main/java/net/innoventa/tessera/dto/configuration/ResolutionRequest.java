package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason an issue closed, as an administrator states it. */
public record ResolutionRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description
) {
}
