package net.innoventa.tessera.dto.project;

/** The compact {id, name} reference to a scheme a project points at. */
public record SchemeSummary(
    String id,
    String name
) {
}
