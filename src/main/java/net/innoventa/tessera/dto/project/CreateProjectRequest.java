package net.innoventa.tessera.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.ProjectType;

/**
 * Create a project. The key must be uppercase (validated here) and unique instance-wide (validated in
 * the service, since it needs the database). {@code leadMemberId} is optional — the creator leads by
 * default. Key strategy/pattern are not chosen at create time in Phase 1; the default prefixed-sequence
 * strategy is applied.
 */
public record CreateProjectRequest(
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 32) @Pattern(
        regexp = "^[A-Z][A-Z0-9]*$",
        message = "must be uppercase letters and digits, starting with a letter") String key,
    @NotNull ProjectType type,
    String leadMemberId
) {
}
