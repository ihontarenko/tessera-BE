package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.VisibilityScope;
import org.jmouse.access.spi.ResolutionCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * The engine's {@link ResolutionCache}, backed by the request.
 *
 * <p>{@link AccessContext} is the cache and is request-scoped; the resolvers that read it are
 * singletons in {@code jmouse-access} that know nothing about Spring. This is the one bean bridging the
 * two, and the whole of its content is the answer to "what if there is no request".
 *
 * <p><strong>No request means no caching, not no answer.</strong> A tool invocation, a startup check and
 * a scheduled job all ask the engine questions off a servlet thread. Falling back to the loader keeps
 * them correct at the cost of asking twice, which is the right trade: a singleton cache remembering
 * across them would keep serving a revoked grant until restart.
 *
 * <h2>⚠️ Why the test is {@link RequestContextHolder} and not {@code getIfAvailable() == null}</h2>
 *
 * <p>Because the obvious version does not work, and fails <em>late</em>. {@code @RequestScope} defaults
 * to {@code proxyMode = TARGET_CLASS}, so the provider hands back a CGLIB proxy whatever the thread is
 * — never null. The null check therefore always passes, and the failure lands on the first method call
 * as {@code ScopeNotActiveException: Scope 'request' is not active for the current thread}, from inside
 * the engine, on whichever off-request caller happened to run first. Here that was the startup parallel
 * run; in production it would have been an agent's tool call.
 *
 * <p>Asking the holder is the question this class actually wants answered — <em>is there a request</em>
 * — rather than a proxy's identity standing in for it.
 */
/*
 * @Primary because there are two beans of this type and only one of them is injectable: AccessContext
 * implements the interface as well — it is the store, after all — but it is @RequestScope, so a
 * singleton asking for a ResolutionCache must get this one. Without the annotation the context fails to
 * start with "expected single matching bean but found 2".
 */
@Component
@Primary
@RequiredArgsConstructor
public class RequestScopedResolutionCache implements ResolutionCache {

    private final ObjectProvider<AccessContext> request;

    @Override
    public EffectivePermissions permissions(
            String subjectId, List<ScopeReference> chain, Supplier<EffectivePermissions> loader) {

        AccessContext current = onARequest();

        return current == null ? loader.get() : current.permissions(subjectId, chain, loader);
    }

    @Override
    public VisibilityScope visibility(
            String subjectId, String permission, Supplier<VisibilityScope> loader) {

        AccessContext current = onARequest();

        return current == null ? loader.get() : current.visibility(subjectId, permission, loader);
    }

    /** This request's cache, or null where there is no request to have one. */
    private AccessContext onARequest() {
        return RequestContextHolder.getRequestAttributes() == null ? null : request.getIfAvailable();
    }
}
