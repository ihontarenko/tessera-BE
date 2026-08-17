package net.innoventa.tessera.domain;

/**
 * What a link of this type <em>does</em>, as opposed to what it is called (TSSR-40).
 *
 * <p>Every link in this product used to be decoration: a name, two labels, and nothing that read them.
 * This is the field that lets the catalog say which relationships the product acts on.
 *
 * <h2>⚠️ Why an enum and not a boolean</h2>
 *
 * <p>{@code informationalOnly} looks sufficient and is not, because the effect is <strong>asymmetric</strong>:
 * in "A blocks B" it is <strong>B</strong> that cannot proceed, never A. A flag says <em>whether</em>
 * something happens and never <em>to which end</em>, so the value has to name the direction along with
 * the action. Every value below is written from the point of view of the <strong>inward</strong> side —
 * the issue that reads "is blocked by".
 *
 * <h2>⚠️ Why the warning level exists from the beginning</h2>
 *
 * <p>The escape from a hard gate is retyping the link, and a team doing that weekly is telling you they
 * needed a warning rather than a wall. If the softer level does not exist they corrupt the catalog
 * instead of turning the strength down — and adding it afterwards means migrating a value everybody has
 * already worked around.
 *
 * <p>⚠️ <strong>"Open" is not defined here.</strong> It is the canonical invariant (ADR-0004): an issue
 * is open exactly while it holds no resolution. Restating it in link code would be a second definition
 * able to disagree with the first.
 */
public enum LinkTypeEffect {

    /** Informational — the product reads it and does nothing. What every link type was until now. */
    NONE,

    /**
     * The inward side is <em>warned</em> when it enters an in-progress status while the outward side is
     * still open, and proceeds anyway.
     */
    WARNS_START,

    /** The inward side may not enter an in-progress status while the outward side is still open. */
    BLOCKS_START,

    /**
     * The inward side may not close while the outward side is still open.
     *
     * <p>⚠️ Shipped unused. It is here because blocking the start and blocking the finish are genuinely
     * different rules, and discovering that after choosing a boolean would mean a migration.
     */
    BLOCKS_DONE

}
