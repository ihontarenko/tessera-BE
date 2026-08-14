package net.innoventa.tessera.security.access;

import org.jmouse.access.AccessTarget;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeReference;

/**
 * Tessera's one floor, named.
 *
 * <p>{@link AccessTarget} and {@link ScopeReference} name no floor at all — they offer
 * {@code at(kind, id)}, {@code place(kind)} and {@code names(kind)}, which is what let them move into a
 * library at all. Everything here is one line over those, and it exists because
 * {@code Targets.project(id)} reads better at seventy call sites than
 * {@code AccessTarget.installation().at(TesseraScope.PROJECT, id)} does.
 *
 * <p>A second floor would be a constant in {@link TesseraScope} plus four lines here, and nothing
 * above — which is exactly the argument {@code TesseraScope}'s javadoc makes for not adding one.
 */
public final class Targets {

    private Targets() {
    }

    /** A request aimed at one project. */
    public static AccessTarget project(String projectId) {
        return AccessTarget.installation().at(TesseraScope.PROJECT, projectId);
    }

    /** A row somebody owns, in the project it lives in. */
    public static AccessTarget ownedBy(String projectId, String ownerId) {
        return project(projectId).withOwner(ownerId);
    }

    public static AccessTarget withProject(AccessTarget target, String projectId) {
        return target.at(TesseraScope.PROJECT, projectId);
    }

    /** Which project this target is about, or null where it is about none. */
    public static String projectIdOf(AccessTarget target) {
        return target.place(TesseraScope.PROJECT).orElse(null);
    }

    public static boolean hasProject(AccessTarget target) {
        return target.names(TesseraScope.PROJECT);
    }

    // ── The same three scopes, as references ──────────────────────────────────

    public static ScopeReference installationScope() {
        return ScopeReference.of(TesseraScope.GLOBAL, ScopeKind.NO_INSTANCE);
    }

    public static ScopeReference selfScope() {
        return ScopeReference.of(TesseraScope.SELF, ScopeKind.NO_INSTANCE);
    }

    public static ScopeReference projectScope(String projectId) {
        return ScopeReference.of(TesseraScope.PROJECT, projectId);
    }
}
