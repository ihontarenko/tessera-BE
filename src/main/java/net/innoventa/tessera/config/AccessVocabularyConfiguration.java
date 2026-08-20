package net.innoventa.tessera.config;

import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.AccessAxis;
import net.innoventa.tessera.security.access.AccessReason;
import net.innoventa.tessera.security.access.TesseraScope;
import org.jmouse.access.AccessEngine;
import org.jmouse.access.AxisCatalog;
import org.jmouse.access.AxisKind;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.EngineRefusals;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.VisibilityScopeResolver;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.jmouse.access.spi.AccessTargetRegistry;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.access.spi.GrantStore;
import org.jmouse.access.spi.ResolutionCache;
import org.jmouse.access.spi.ScopeHierarchy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Where Tessera tells the authorization engine what its words are.
 *
 * <p>The engine holds the mechanism and none of the vocabulary: it knows that scopes nest and that axes
 * run in order, and it learns which scopes and which axes exist only from here. That separation is what
 * makes adopting it a matter of registering three catalogues rather than of bending a tracker into
 * another product's shape — Tessera's floor is a <strong>project</strong>, which Innoventa does not
 * have, and nothing above this file knows the difference.
 *
 * <p><strong>Each catalogue validates what it is handed</strong> and refuses to build on a vocabulary
 * that does not hold together — a duplicate rank, a missing widest scope, an axis nothing answers. A
 * context that starts is a context whose authorization model was checked.
 *
 * @see AccessStorageConfiguration for the storage half — the rows, and the ports that write them
 */
@Configuration
public class AccessVocabularyConfiguration {

    /**
     * Tessera's three scopes: the installation, a project, and a subject's own rows.
     *
     * <p>Handed over as {@link TesseraScope#values()} rather than listed again, because the enum's
     * declaration order <em>is</em> the containment order and writing the list twice is how the two
     * drift apart.
     */
    @Bean
    public ScopeCatalog scopeCatalog() {
        return new ScopeCatalog(List.<ScopeKind>of(TesseraScope.values()));
    }

    /**
     * ⚠️ <strong>Flat, and saying so is more honest than leaving it out.</strong>
     *
     * <p>The hierarchy answers <em>which wider places contain this one</em>, and in Tessera the answer
     * is none: a project sits directly under the installation and there is no account, department or
     * organisation between them. Several beans default to {@link ScopeHierarchy#flat()} when no bean
     * exists, so this registration changes no behaviour — what it changes is that a reader finds the
     * answer here instead of inferring it from four separate absences.
     *
     * <p>The day a floor is added above {@code PROJECT}, this is the one bean that has to grow a real
     * implementation, and everything that reads it already does.
     */
    @Bean
    public ScopeHierarchy scopeHierarchy() {
        return ScopeHierarchy.flat();
    }

    /**
     * Tessera's two axes, in the order a request is asked them.
     *
     * <p>Innoventa registers six because it has modules to switch off and capabilities to sell.
     * Registering those here would put links in the chain that always allow — a step every reader has
     * to be told to ignore. See {@link AccessAxis}.
     */
    @Bean
    public AxisCatalog axisCatalog() {
        return new AxisCatalog(List.<AxisKind>of(AccessAxis.values()));
    }

    // ⚠️ THERE IS NO `PermissionCatalog` BEAN HERE EITHER, AND THAT IS DELIBERATE.
    // `AccessPolicyAutoConfiguration` reads it off the merged policy documents — `tessera.jmp` declares
    // what a subject may DO, `tools.jmp` declares one permission per published tool action, and
    // `declare permissions` IS the vocabulary. A bean here would shadow that and put the list in two
    // places again, which is exactly the failure this arrangement replaced: the same names as Java
    // constants and as declarations, a startup check comparing them, and a boot that died naming
    // twenty-four permissions "that do not exist" while they sat declared in a file.

    /**
     * The words for the two refusals the dispatcher raises itself.
     *
     * <p>Every other refusal comes out of an axis, and an axis is a bean of Tessera's that already knows
     * how to say what it means.
     */
    @Bean
    public EngineRefusals engineRefusals() {
        return new EngineRefusals(AccessReason.NOT_FOUND_OR_HIDDEN, AccessReason.UNDECIDABLE_CALL);
    }

    // ── The engine itself ─────────────────────────────────────────────────────
    //
    // Declared here rather than component-scanned, because these classes live in `jmouse-access` and
    // carry no Spring annotations at all — which is the point of them living there. Wiring a library is
    // the application's job, and this is where Tessera does it.

    /**
     * Every feature's answer to "where do my rows live", collected by type.
     *
     * <p>Two resolvers today, and both are about an issue: every other route in this product spells its
     * project into the URL, so the binder reads the place off the path and asks nobody.
     */
    @Bean
    public AccessTargetRegistry accessTargetRegistry(List<AccessTargetResolver<?>> resolvers) {
        return new AccessTargetRegistry(resolvers);
    }

    /**
     * @param grants ⚠️ <strong>the rows, and only the rows.</strong> The policy auto-configuration
     *               registers a store of its own over the document and would compose the two; that
     *               composition is refused in {@link AccessStorageConfiguration}, which claims the
     *               composed bean's name for the JPA store. Which of the two arrives here is the whole
     *               of "a grant lives in exactly one place"
     */
    @Bean
    public EffectivePermissionsResolver effectivePermissionsResolver(
            GrantStore      grants,
            ScopeCatalog    scopes,
            ResolutionCache cache) {

        // ⚠️ No ShareGrants: Tessera has no share links. The resolver takes null for "this
        // installation grants nothing through a token", which is correct rather than degraded.
        return new EffectivePermissionsResolver(grants, scopes, cache, null);
    }

    /**
     * Which projects a reader may see under one permission — resolved once per request, never per row.
     *
     * <p>What a listing filters on. Asking it inside a loop would put the same question to the same
     * answer once per issue on every board, which is the N+1 {@code AccessContext} exists to make
     * visible.
     */
    @Bean
    public VisibilityScopeResolver visibilityScopeResolver(
            GrantStore grants, ResolutionCache cache, ScopeHierarchy scopeHierarchy) {

        return new VisibilityScopeResolver(grants, cache, scopeHierarchy);
    }

    @Bean
    public AccessEngine accessEngine(
            List<AccessAxisEvaluator> axes,
            AxisCatalog               declaredAxes,
            AccessTargetRegistry      targets,
            VisibilityScopeResolver   visibilityScopes,
            EngineRefusals            refusals) {

        return new AccessEngine(axes, declaredAxes, targets, visibilityScopes, refusals);
    }
}
