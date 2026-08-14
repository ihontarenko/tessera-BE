package net.innoventa.tessera.security.access;

import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.VisibilityScope;
import org.jmouse.access.spi.ResolutionCache;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What this request has already asked, so that it is never asked twice.
 *
 * <p>Two things ride here, and both are the same shape of saving: the answer is a property of the
 * <em>reader</em>, not of the row, so anything resolving inside a loop asks one question many times and
 * gets one answer many times.
 *
 * <ul>
 *   <li><strong>Effective permissions.</strong> A board render asks about half a dozen permissions and a
 *       backlog asks about a page of issues; all of it resolves one set, once.
 *   <li><strong>Visibility scopes.</strong> Which projects a reader may see — asked once per listing
 *       rather than once per row.
 * </ul>
 *
 * <p>{@link #resolutionCount()} exists so a test can fail when somebody moves a resolution inside a
 * loop. An N+1 that only shows up as a slow board is an N+1 nobody finds.
 */
@Component
@RequestScope
public class AccessContext implements ResolutionCache {

    private final Map<PermissionQuestion, EffectivePermissions> permissionSets   = new HashMap<>();
    private final Map<VisibilityQuestion, VisibilityScope>      visibilityScopes = new HashMap<>();

    private int resolutionCount = 0;

    /**
     * The effective set for one subject at one scope chain, resolved at most once per request.
     *
     * <p>Keyed on the chain rather than on the subject, because the same person legitimately has
     * different answers in two projects.
     */
    @Override
    public EffectivePermissions permissions(
            String                         subjectId,
            List<ScopeReference>           chain,
            Supplier<EffectivePermissions> loader) {

        return permissionSets.computeIfAbsent(new PermissionQuestion(subjectId, chain), question -> {
            resolutionCount++;
            return loader.get();
        });
    }

    @Override
    public VisibilityScope visibility(
            String                    subjectId,
            String                    permission,
            Supplier<VisibilityScope> loader) {

        return visibilityScopes.computeIfAbsent(
                new VisibilityQuestion(subjectId, permission), question -> {
                    resolutionCount++;
                    return loader.get();
                });
    }

    /** How many resolutions this request actually performed — the number an N+1 test asserts on. */
    public int resolutionCount() {
        return resolutionCount;
    }

    private record PermissionQuestion(String subjectId, List<ScopeReference> chain) {}

    private record VisibilityQuestion(String subjectId, String permission) {}
}
