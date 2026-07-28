package net.innoventa.tessera.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plan a sprint: a name and, optionally, what it is for. Deliberately <strong>no dates</strong> — a
 * future sprint is a named bucket, and its window is fixed when it is started.
 */
public record CreateSprintRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 1000) String goal
) {
}
