package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A kind of work as an administrator states it.
 *
 * <p>{@code hierarchyLevel} is an {@code int} rather than an enum, and that is the model's oldest
 * decision about issue types: level 1 contains other work, level 0 is what a board shows and a sprint
 * plans (ADR-0014), level −1 always belongs to a parent, and an Initiative at 2 was always anticipated.
 * The picker offers the three that exist by name; nothing forbids the others existing in data.
 *
 * @param iconKey one of {@code IssueTypeIcons.ALL}, or null. Not free text — an unrecognised key draws
 *                the generic fallback and is therefore a typo nobody ever sees
 */
public record IssueTypeRequest(
    @NotBlank @Size(max = 64) String name,
    int hierarchyLevel,
    @Size(max = 64) String iconKey,
    @Size(max = 255) String description
) {
}
