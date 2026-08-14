package net.innoventa.tessera.dto.membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Add a member to a project with one or more roles (which combine additively).
 *
 * <p>Roles are named — {@code PROJECT_DEVELOPER} — rather than identified. The table that handed out
 * identifiers is gone, and a name is what the engine stores, what the policy document declares, and
 * what the access screen shows.
 */
public record AddProjectMemberRequest(
    @NotBlank String memberId,
    @NotEmpty List<String> roleNames
) {
}
