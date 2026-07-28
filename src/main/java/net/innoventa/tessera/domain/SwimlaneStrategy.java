package net.innoventa.tessera.domain;

/**
 * How a {@link Board} groups its cards into horizontal lanes (Phase-2 ticket 04). A fixed enum, not a
 * seeded catalog, because grouping is a closed set the client resolves from fields the board payload
 * already carries ({@code assigneeId}, {@code epicKey}, {@code priorityId}) — the server stores the
 * choice and returns a flat card list, it never groups. Adding a grouping is a new constant plus a
 * client resolver, never a schema change.
 */
public enum SwimlaneStrategy {
    NONE,
    ASSIGNEE,
    EPIC,
    PRIORITY
}
