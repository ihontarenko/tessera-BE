package net.innoventa.tessera.dto.component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or edit a component (ticket 06): a name unique within its project, an optional lead member,
 * and an optional description. Requires {@code ADMINISTER_PROJECT}.
 */
public record SaveComponentRequest(
    @NotBlank @Size(max = 128) String name,
    String leadMemberId,
    @Size(max = 255) String description
) {
}
