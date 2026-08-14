package net.innoventa.tessera.security.access;

import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeNature;

import java.util.Optional;

/**
 * Where a permission means something in Tessera.
 *
 * <p><strong>Three floors, and the two that were considered and rejected are the interesting part.</strong>
 * A scope kind added later is a migration and one removed later is a policy rewrite, so this is written
 * down before anything depends on it.
 *
 * <h2>What is here</h2>
 *
 * <ul>
 *   <li>{@link #GLOBAL} — the installation. Every model needs exactly one {@code EVERYTHING} scope, and
 *       it is the link in the covering chain that always matches, so an unscoped grant means something.
 *   <li>{@link #PROJECT} — the only place Tessera actually holds a grant at. A person is a member of a
 *       project, carries a role there, and may be denied one permission there personally. Everything
 *       the tracker authorizes reduces to <em>which project is this about</em>.
 *   <li>{@link #SELF} — the narrowest thing a grant can be about: a person's own rows. Nothing uses it
 *       yet. It is registered because ownership is not a separate mechanism in this model — it is this
 *       scope — and adding it the day the first <em>"issues you reported"</em> rule appears would be
 *       the migration this comment exists to avoid.
 * </ul>
 *
 * <h2>⚠️ Why there is no {@code BOARD}</h2>
 *
 * <p>The handover note proposed one and called it the canary — a floor Innoventa does not have, worth
 * registering to find out whether the library trips over it. It is not here, for a reason that is
 * about Tessera rather than about the library: <strong>a board is one-to-one with its project</strong>
 * ({@code Board.projectId} is unique, since ADR-0015 there is nothing to branch on) and is a
 * <em>view</em> over that project's issues rather than a place anything is held at. A grant at
 * {@code BOARD:x} would cover exactly what a grant at its project covers, so the floor would be a
 * synonym — and a covering chain with a synonym in it is one every reader has to be told to ignore.
 *
 * <p>The canary still sings, quieter: {@code PROJECT} is itself a floor Innoventa does not have, so the
 * library's claim to know no product vocabulary is tested by this enum existing at all. ⚠️ What is
 * <em>not</em> tested is nesting — Tessera turned out to be one floor deep where the handover assumed
 * three, so {@link org.jmouse.access.spi.ScopeHierarchy#flat()} is the honest answer and the
 * hierarchy machinery goes unexercised here. That is worth knowing rather than claiming otherwise.
 *
 * <h2>⚠️ Why there is no {@code ORGANIZATION}</h2>
 *
 * <p>Tessera has no such row. Registering a floor nothing can name would put an instance in the
 * covering chain that never resolves, which is the failure mode the handover's own first warning
 * describes: a grant that parses, projects, and decides nothing.
 *
 * <p><strong>Declaration order is width order</strong> — {@link #rank()} is {@code ordinal()} and there
 * is deliberately no rank column. A floor in the wrong position is a covering chain nobody reordered.
 */
public enum TesseraScope implements ScopeKind {

    /** Everything. Exactly one scope must be this, and it is what makes an unscoped grant meaningful. */
    GLOBAL(ScopeNature.EVERYTHING, null),

    /** One project, named in a route by {@code projectId} and in a policy document by its key. */
    PROJECT(ScopeNature.PLACE, "projectId"),

    /** A person's own rows. Registered ahead of its first use — see the class note. */
    SELF(ScopeNature.OWN_ROWS, null);

    private final ScopeNature nature;
    private final String      requestParameter;

    TesseraScope(ScopeNature nature, String requestParameter) {
        this.nature           = nature;
        this.requestParameter = requestParameter;
    }

    public static TesseraScope of(ScopeKind kind) {
        return kind instanceof TesseraScope scope ? scope : valueOf(kind.name());
    }

    @Override
    public ScopeNature nature() {
        return nature;
    }

    @Override
    public int rank() {
        return ordinal();
    }

    @Override
    public Optional<String> requestParameter() {
        return Optional.ofNullable(requestParameter);
    }
}
