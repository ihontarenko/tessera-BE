package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A workflow's own two fields. Its shape is its transitions, and they are edited one at a time. */
public record WorkflowRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description
) {
}
