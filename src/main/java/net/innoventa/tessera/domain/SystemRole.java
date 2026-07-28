package net.innoventa.tessera.domain;

/**
 * The lightweight global (instance) authorization tier — deliberately just two values, not a
 * catalogue. {@code ADMIN} manages the global configuration catalogs/schemes; everything
 * fine-grained lives at the project tier ({@code ProjectRole} + {@code Permission}). There is no
 * global permission-override machinery by design (see CONTEXT.md, "System role").
 */
public enum SystemRole {
    ADMIN,
    USER
}
