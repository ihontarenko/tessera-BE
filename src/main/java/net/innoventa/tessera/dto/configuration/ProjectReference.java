package net.innoventa.tessera.dto.configuration;

/**
 * A project as the Administration screens name it — enough to print and enough to link to.
 *
 * <p>Not {@code ProjectResponse}: that carries the caller's permissions, both schemes and a board
 * strategy, none of which a "used by" list needs, and computing them per project would turn a panel
 * into a page of queries.
 */
public record ProjectReference(String id, String key, String name) {
}
