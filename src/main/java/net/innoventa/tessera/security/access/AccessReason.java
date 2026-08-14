package net.innoventa.tessera.security.access;

import org.jmouse.access.RefusalReason;
import org.springframework.http.HttpStatus;

/**
 * Why a request was refused, and the one place that decides what the client reads.
 *
 * <p>Tessera already made the distinction this enum formalises, and made it in prose rather than in a
 * type: {@code ProjectPermissionService.requireVisible} answered <strong>404</strong> for a non-member
 * and <strong>403</strong> for a member without {@code BROWSE_PROJECT}, because a person who is not in
 * a project must not learn that it exists. That reasoning survives here as
 * {@link #NOT_FOUND_OR_HIDDEN} and {@link #NO_PERMISSION}; what changes is that the status now hangs
 * off the <em>axis that answered</em> rather than off which service happened to throw.
 *
 * <p><strong>Each refusal keeps its own words.</strong> The status is shared; the sentence is not. Told
 * the same sentence by two different axes, a reader concludes the product is broken rather than that
 * two different things are true.
 */
public enum AccessReason implements RefusalReason {

    /** Nobody is signed in. The reader's next move: sign in. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, AccessAxis.IDENTITY, "Not signed in"),

    /** The permission is not held anywhere covering this. Next move: ask somebody who can grant it. */
    NO_PERMISSION(HttpStatus.FORBIDDEN, AccessAxis.PERMISSION, "Not yours to do"),

    /**
     * Deliberately indistinguishable from "there is no such row".
     *
     * <p><strong>Tessera's membership isolation, kept whole.</strong> ADR-0002: a caller who is not in a
     * project cannot learn that it exists, so being outside one reads exactly as a project that is not
     * there. That is why there is no {@code NOT_IN_SCOPE} here and Innoventa has one — its workspaces are
     * discoverable and a person outside one is told so, which is a different product's decision about a
     * different kind of place.
     *
     * <p>⚠️ <strong>The words have to match the status.</strong> A 404 whose body says <em>you are not a
     * member of this project</em> discloses the very thing the 404 was for. Whatever refuses with this
     * says "not found" and nothing more.
     */
    NOT_FOUND_OR_HIDDEN(HttpStatus.NOT_FOUND, AccessAxis.PERMISSION, "Not found"),

    /**
     * The route promised to publish a value, and the call supplied nothing for it.
     *
     * <p>⚠️ A refusal about the <em>call</em> rather than about the caller, which is why it is a 400:
     * nobody is being told no, the request simply cannot be decided about. Next move: pass the
     * parameter.
     */
    UNDECIDABLE_CALL(HttpStatus.BAD_REQUEST, AccessAxis.PERMISSION, "Not enough to decide");

    private final HttpStatus status;
    private final AccessAxis axis;
    private final String     title;

    AccessReason(HttpStatus status, AccessAxis axis, String title) {
        this.status = status;
        this.axis   = axis;
        this.title  = title;
    }

    /**
     * What the client reads — the single mapping {@code GlobalExceptionHandler} looks up.
     *
     * <p>Deliberately <em>not</em> on {@link RefusalReason}. A refusal is a fact about authorization and
     * a status code is a fact about a transport; the engine is called from an interceptor today and
     * could be called from a tool dispatcher tomorrow, and neither should have to know about the other.
     */
    public HttpStatus status() {
        return status;
    }

    @Override
    public AccessAxis axis() {
        return axis;
    }

    /**
     * The refusal a reason is, for the transport code that needs the status mapping above.
     *
     * <p>Every reason this installation produces is one of these already; the lookup by name is there so
     * that one that somehow is not fails at the boundary rather than three frames further in.
     */
    public static AccessReason of(RefusalReason reason) {
        return reason instanceof AccessReason known ? known : valueOf(reason.name());
    }

    /**
     * The heading, distinct per reason.
     *
     * <p>Distinct on purpose: "Access denied" over every one of these is how a reader learns that the
     * product refuses without knowing why.
     */
    public String title() {
        return title;
    }

    /** The machine-readable value carried on the {@code ProblemDetail}, beside the prose. */
    public String wireName() {
        return name().toLowerCase().replace('_', '-');
    }
}
