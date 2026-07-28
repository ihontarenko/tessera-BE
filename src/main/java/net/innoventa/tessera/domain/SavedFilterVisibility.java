package net.innoventa.tessera.domain;

/**
 * Who a saved filter is for. Deliberately three values and no sharing graph: a filter is the owner's
 * own working set, something the project shares, or a preset the product ships. Anything finer is a
 * permission model, and the project already has one.
 */
public enum SavedFilterVisibility {

    /** Visible only to the member who saved it. */
    PRIVATE,

    /** Visible to every member who can browse the project. */
    PROJECT,

    /**
     * A seeded preset (migration {@code V000010}): visible everywhere, owned by nobody, editable by
     * nobody. The only visibility whose rows carry a null {@code projectId} and {@code ownerMemberId} —
     * "my open issues" means the same thing in every project, so binding it to one would mean
     * re-seeding it for every project ever created. Never accepted from a client.
     */
    GLOBAL

}
