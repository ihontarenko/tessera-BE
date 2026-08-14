package net.innoventa.tessera.exception;

import net.innoventa.tessera.security.access.AccessAxis;
import net.innoventa.tessera.security.access.AccessReason;
import org.jmouse.access.AccessDecision;

/**
 * <em>You may not</em> — one idea, one exception, and the reason that decides what the client reads.
 *
 * <p>It used to reach the client as two different exceptions chosen by hand at each call site:
 * {@link ForbiddenException} for a missing permission and {@link ResourceNotFoundException} for a
 * non-member, with every author having to remember which. The distinction was right — a person outside
 * a project must not learn it exists — and remembering it was the fragile part.
 *
 * <p>Now the status hangs off the {@link AccessReason} the engine's axis produced, and
 * {@link GlobalExceptionHandler} looks it up in one place. What each refusal keeps is its <strong>own
 * words</strong>: the status is shared, the sentence is not.
 */
public class AccessDeniedException extends RuntimeException {

    private final AccessReason reason;

    public AccessDeniedException(AccessReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * The refusal an engine decision already carries, thrown without rewording it.
     *
     * <p>The decision hands back the model's open {@code RefusalReason}; this exception holds Tessera's
     * constant, because what it exists for is the status mapping, and the status is this product's.
     */
    public static AccessDeniedException of(AccessDecision decision) {
        return new AccessDeniedException(AccessReason.of(decision.reason()), decision.words());
    }

    public AccessReason getReason() {
        return reason;
    }

    public AccessAxis getAxis() {
        return reason.axis();
    }
}
