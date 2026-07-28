package net.innoventa.tessera.dto.membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.innoventa.tessera.domain.PermissionEffect;

/** Set (or replace) an individual ALLOW/DENY override for a member in a project. */
public record SetPermissionOverrideRequest(
    @NotBlank String permissionId,
    @NotNull PermissionEffect effect
) {
}
