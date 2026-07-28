package net.innoventa.tessera.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Correct a sprint after creating it — its name and its goal. A rename does not rewrite history: the
 * activity log snapshots sprint names as point-in-time display strings (ADR-0007), so an entry that
 * mentions the old name keeps mentioning it.
 */
public record UpdateSprintRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 1000) String goal
) {
}
