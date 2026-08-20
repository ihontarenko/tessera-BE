package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import org.jmouse.access.enforcement.PublicEndpoint;
import org.jmouse.ai.mcp.authorization.AuthorizationDocuments;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * How a client finds out that any of this exists.
 *
 * <p>It is read <strong>before</strong> the reader holds any credential — that is the entire reason it
 * exists — so it is public by necessity, and everything it discloses is a route that was already
 * reachable.
 *
 * <p>⚠️ <strong>The protected-resource document is not served here, deliberately.</strong> Spring
 * Security's own filter serves it, built by {@code SecurityConfiguration.describeThisResource}, and the
 * same configuration is what puts its address into the {@code WWW-Authenticate} header of every refusal
 * — which is where a client learns to look. A second copy of that document here would be one sentence
 * about this installation written in two places, and the failure mode is the worst available: a plausible
 * document that disagrees with the header. The appended-path form is answered with a redirect to the one
 * real copy for exactly that reason.
 *
 * <p>⚠️ <strong>The authorization-server document is Tessera's own.</strong> It used to be Identity that a
 * client was sent to, and Identity does not support dynamic client registration — so a client discovered
 * the resource, read the issuer, fetched its metadata, found no {@code registration_endpoint} and gave up
 * before anything it could act on. See {@code AgentCredentials} for what changed and why a protocol
 * credential is signed here.
 *
 * <p>The three addresses it advertises are resolved from the shared route descriptor rather than written
 * out, because a document that advertises a path nothing is served at is a client that cannot connect and
 * cannot say why.
 */
@RestController
@RequiredArgsConstructor
@PublicEndpoint("Discovery is read before a client holds anything — that is what it is for — and it discloses only routes that were already reachable.")
public class McpDiscoveryController {

    private final McpAuthorizationProperties   settings;
    private final McpAuthorizationProperties authorization;

    /** Where a client authorizes, redeems a code, and renews a credential (RFC 8414). */
    @GetMapping({
            AuthorizationRoutes.AUTHORIZATION_SERVER_METADATA,
            AuthorizationRoutes.AUTHORIZATION_SERVER_METADATA + AuthorizationRoutes.ANY_RESOURCE_SUFFIX
    })
    public Map<String, Object> authorizationServer() {
        AuthorizationRoutes routes = authorization.routes();

        return AuthorizationDocuments.authorizationServer(
                settings.getResourceUrl(),
                settings.apiUrl(routes.authorization()),
                settings.apiUrl(routes.token()),
                settings.apiUrl(routes.registration()),
                AuthorizationRoutes.SCOPE);
    }

    /**
     * The same protected-resource document, for a client that appended the resource's path to the
     * well-known location — which is how one asks about one resource among several on a host.
     */
    @GetMapping(AuthorizationRoutes.PROTECTED_RESOURCE_METADATA + AuthorizationRoutes.ANY_RESOURCE_SUFFIX)
    public ResponseEntity<Void> protectedResource() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(settings.apiUrl(AuthorizationRoutes.PROTECTED_RESOURCE_METADATA)))
                .build();
    }
}
