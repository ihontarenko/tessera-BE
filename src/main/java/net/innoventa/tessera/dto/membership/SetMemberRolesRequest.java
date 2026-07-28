package net.innoventa.tessera.dto.membership;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replace the whole set of roles a member holds in a project (must keep at least one). */
public record SetMemberRolesRequest(
    @NotEmpty List<String> roleIds
) {
}
