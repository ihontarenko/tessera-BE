package net.innoventa.tessera.security.access;

import org.jmouse.access.AxisCatalog;
import org.jmouse.access.AxisKind;

/**
 * Which of the questions answered a request, in the order they are asked.
 *
 * <p><strong>Two, where Innoventa has six, and the difference is the whole point of the axis
 * catalogue.</strong> {@link #IDENTITY} and {@link #PERMISSION} are what <em>may I</em> means anywhere;
 * everything else Innoventa asks — a ceiling, an entitlement, a per-place switch, a condition — is a
 * question about something Tessera does not have. A tracker that sells nothing has nothing to refuse
 * with 402, and a product with no modules has no switch to read. Registering an axis nothing answers
 * would put a link in the chain that always allows, which is a step every reader has to be told to
 * ignore.
 *
 * <p><strong>The order is declared and total.</strong> {@link #order()} is {@code ordinal()}, and the
 * value of a verdict naming an axis is that it names the <em>outermost</em> reason: somebody who is not
 * signed in must read <em>sign in</em> rather than <em>you do not have permission</em>. An ordering that
 * emerged from bean discovery order would make that a coincidence.
 *
 * <p><strong>Both are {@link #required()}.</strong> They are the two the model itself is made of, and a
 * missing one is a question silently answered yes — which is the single failure this enum exists to
 * prevent. {@link AxisCatalog} refuses to build on a vocabulary with a hole in it, so an installation
 * that loses a bean does not start rather than starting permissive.
 *
 * <p>⚠️ <strong>There is deliberately no ownership axis.</strong> A row somebody reported is the
 * narrowest scope of {@link #PERMISSION} — {@link TesseraScope#SELF} — rather than a question of its
 * own, and membership of a project is what the permission axis says by default when no assignment
 * covers it. Both were mechanisms in Tessera before this; neither is one now.
 */
public enum AccessAxis implements AxisKind {

    /** Who is asking. Refuses exactly one thing: nobody. */
    IDENTITY(true),

    /** Whether <em>this person</em> may do <em>this</em>, <em>in this project</em>. */
    PERMISSION(true);

    private final boolean required;

    AccessAxis(boolean required) {
        this.required = required;
    }

    /** The declaration order above, which is the order the engine runs them in. */
    @Override
    public int order() {
        return ordinal();
    }

    /** Whether an installation without a bean for this axis should refuse to start. */
    @Override
    public boolean required() {
        return required;
    }
}
