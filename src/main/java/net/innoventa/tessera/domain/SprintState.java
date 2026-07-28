package net.innoventa.tessera.domain;

/**
 * A sprint's lifecycle position, with one-way transitions: {@code FUTURE → ACTIVE → CLOSED}. Nothing
 * reopens a closed sprint and nothing sends a sprint backwards.
 * <p>
 * Deliberately a plain enum rather than the data-driven ADR-0005 workflow engine: a sprint is not an
 * issue, its states are fixed by the product rather than configured per project, and a
 * {@link Transition} graph over three product-owned states would be cargo cult.
 */
public enum SprintState {

    /** Planned but not running — a named bucket with no dates at all. */
    FUTURE,

    /** Running. At most one per project, enforced in the service layer. */
    ACTIVE,

    /** Finished. Its membership rows are its history and are never rewritten. */
    CLOSED;

    /** Not {@code CLOSED} — the states whose membership still counts as a live commitment. */
    public boolean isOpen() {
        return this != CLOSED;
    }

}
