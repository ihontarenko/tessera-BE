package net.innoventa.tessera.dto.membership;

import net.innoventa.tessera.domain.PermissionEffect;

/** One individual permission override on a member — which permission, and which direction. */
public record OverrideSummary(
    String permissionId,
    String permissionName,
    PermissionEffect effect
) {
}
