package net.innoventa.tessera.domain;

/**
 * The direction of an individual {@link ProjectPermissionOverride} layered over a member's role
 * permissions. {@code DENY} always wins over any {@code ALLOW} and any role grant (see
 * {@code ProjectPermissionService}), so revoking access is reliable.
 */
public enum PermissionEffect {
    ALLOW,
    DENY
}
