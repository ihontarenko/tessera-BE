package net.innoventa.tessera.dto.membership;

/** The compact {id, name} reference to a project role. */
public record RoleSummary(
    String id,
    String name
) {
}
