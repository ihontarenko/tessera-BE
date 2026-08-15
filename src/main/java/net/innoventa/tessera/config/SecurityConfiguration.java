package net.innoventa.tessera.config;

import jakarta.servlet.http.HttpServletRequest;
import net.innoventa.tessera.ai.McpEndpoint;
import net.innoventa.tessera.dto.PublicAvatarRoutes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import net.innoventa.tessera.security.MemberAuthoritiesConverter;

/**
 * Resource-server-only security: Tessera never mints tokens, it validates the ones issued by the
 * Identity service against its JWKS endpoint. Like Innoventa/BE and Moneta/BE — and unlike
 * Innoventa Central, which accepts a whole allow-list of audiences because several products call it
 * — Tessera only ever sees tokens minted for itself, so it enforces a single required audience: the
 * token's {@code aud} claim must contain {@code tessera.security.audience} (default {@code tessera},
 * stamped by the {@code tessera} client registered in Identity's {@code application.yml}). A token
 * minted for Moneta or any other product cannot be replayed here.
 * <p>
 * Authorization (project roles, permissions) stays local to Tessera per Identity's "identity is
 * centralized; authorization is not" contract — none of it lives in the token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Value("${tessera.security.audience:tessera}")
    private String requiredAudience;

    /**
     * What this server calls itself: the address a client reaches it at, published as the resource it
     * protects and — since the protocol's credentials are Tessera's own — as the authorization server that
     * issues them.
     */
    @Value("${tessera.security.resource:http://localhost:8100}")
    private String resource;

    /**
     * How a Model Context Protocol client finds out where to get a credential.
     *
     * <ol>
     *   <li>It calls {@code /api/mcp} with no token and gets <strong>401</strong> carrying
     *       {@code WWW-Authenticate: Bearer resource_metadata="…"}. Spring Security emits that on its
     *       own; nothing here arranges it.
     *   <li>It fetches that document — this — and reads {@code authorization_servers}.
     *   <li>It fetches <em>that</em> server's metadata, registers itself (RFC 7591), and runs
     *       authorization-code with PKCE against it, with a loopback redirect.
     *   <li>It comes back with a credential {@link #mcpSecurityFilterChain}'s decoder accepts and no
     *       other chain can verify at all.
     * </ol>
     *
     * <h3>⚠️ The authorization server named here is Tessera, and it used to be Identity</h3>
     *
     * <p>That was the shape the specification assumes — resource here, tokens there — and it did not
     * work: <strong>Identity supports no dynamic client registration</strong>, and a protocol client will
     * not use a client identifier a human configured, because it has nowhere to be told one. Claude Code
     * refuses at exactly that step with <em>"Incompatible auth server: does not support dynamic client
     * registration"</em>, having walked every step above successfully. Pre-registering
     * {@code tessera-mcp} in Identity could not help and the registration is now unused.
     *
     * <p>So Tessera issues the protocol's credentials itself, the way Innoventa does — see
     * {@code McpCredentialService} for what is signed and {@code ClientAuthorizationFlow} for who approves
     * it. Identity remains the only thing that authenticates a <em>person</em>: the consent screen is
     * behind an Identity session, so a client is approved by somebody Identity signed in, and Tessera
     * mints nothing for anybody it has not already been told about.
     *
     * <p>⚠️ <strong>{@code scope} is stated because a document a client reads has to be well-formed</strong>,
     * not because the scope grants anything: what an agent may do is the approving member's permissions,
     * resolved per call by the same engine that answers for their browser.
     *
     * <p>⚠️ <strong>Through the DSL, not a filter bean of its own.</strong> A separately-registered
     * {@code OAuth2ProtectedResourceMetadataFilter} is ordered <em>after</em> the security chain, so
     * Security's own instance answers first and the customizer never runs — which fails in the worst
     * way available: a 200 with a plausible document that is missing {@code authorization_servers}, so a
     * client discovers the resource and then has nowhere to go. Found by reading the response, not by
     * reasoning about it.
     *
     * <p>⚠️ {@code tlsClientCertificateBoundAccessTokens} is set to <strong>false</strong> explicitly.
     * The filter's default is {@code true}, and Tessera binds no token to a client certificate — a
     * document claiming otherwise is a promise about how tokens are protected that nothing keeps.
     */
    private void describeThisResource(OAuth2ProtectedResourceMetadata.Builder metadata) {
        metadata.resource(resource)
                .resourceName("Tessera")
                .authorizationServer(resource)
                .scope(AuthorizationRoutes.SCOPE)
                // Header only. A token in a query string ends up in access logs, browser history and
                // proxy caches, and the protocol needs neither of the other two ways.
                .bearerMethods(methods -> {
                    methods.clear();
                    methods.add("header");
                })
                .tlsClientCertificateBoundAccessTokens(false);
    }

    /**
     * The protocol endpoint, and <strong>only</strong> credentials Tessera itself signed.
     *
     * <h3>⚠️ This is where the confinement is, and it is cryptographic</h3>
     *
     * <p>The decoder here verifies HS256 against Tessera's own secret; {@link #jwtDecoder} verifies RS256
     * against Identity's JWKS. Neither can be made to accept the other's token, so two things are true
     * without anything having to check them: a credential a person approved for a client works
     * <em>nowhere but here</em>, and the token a browser holds cannot drive the tools. Before this chain
     * existed the second half was false — the endpoint accepted an ordinary Identity token, which
     * {@code McpConfiguration} called out as a real gap for exactly as long as it was there.
     *
     * <p>⚠️ <strong>The matcher compares the request URI itself, and it has to.</strong> A path pattern —
     * {@code securityMatcher("/api/mcp", "/api/mcp/*")}, the obvious spelling — silently matches
     * <em>nothing</em> here: the protocol is served by a servlet of its own mapped at {@code /api/mcp/*},
     * so Spring Security matches patterns against the path <em>within that servlet</em>, which for
     * {@code POST /api/mcp} is empty. The chain simply never answered, every request fell through to the
     * browser chain below, and the symptom was the opposite of a security error — an ordinary Identity
     * token driving the tools with a cheerful {@code 200}, exactly the confinement this chain exists to
     * end. Found by presenting such a token on purpose and expecting a refusal.
     *
     * <p>The authorities converter is shared with the browser chain on purpose. An agent is a person: the
     * same subject resolves to the same {@code Member} row and the same global tier, and everything below
     * this line is unaware there are two ways in.
     */
    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(
        HttpSecurity httpSecurity,
        // ⚠️ QUALIFIED, AND NOT AS A FORMALITY. There are two JwtDecoder beans, one of them @Primary, and
        // a primary candidate beats a parameter-name match — so the obvious spelling (a parameter simply
        // named mcpJwtDecoder) silently handed this chain IDENTITY's decoder. The endpoint then accepted
        // exactly the token it exists to refuse and refused the credential it exists to accept, with
        // nothing anywhere saying so. Found by presenting both tokens and reading both answers.
        @Qualifier("mcpJwtDecoder") JwtDecoder mcpJwtDecoder,
        JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        httpSecurity
            .securityMatcher(SecurityConfiguration::isProtocolRequest)
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt
                    .decoder(mcpJwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter))
                // ⚠️ Load-bearing on this chain above all others: the 401 it produces is the FIRST thing
                // a client ever gets, and the `resource_metadata` parameter this adds to
                // WWW-Authenticate is the only reason the client knows where discovery starts.
                .protectedResourceMetadata(metadata ->
                    metadata.protectedResourceMetadataCustomizer(this::describeThisResource)));

        return httpSecurity.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
        HttpSecurity httpSecurity,
        @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
        JwtAuthenticationConverter jwtAuthenticationConverter,
        McpAuthorizationProperties mcpAuthorization
    ) throws Exception {
        AuthorizationRoutes mcpRoutes = mcpAuthorization.routes();

        httpSecurity
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                // This backend serves an API and nothing else. Tessera/UI is a separate application
                // on its own origin, so there is no shell to let through unauthenticated and no
                // client-side route for this filter chain to know about — the browser never asks
                // this port for one.
                .requestMatchers("/actuator/health").permitAll()
                // ⚠️ Public, and it must be: a Model Context Protocol client reads these documents to
                // find out WHERE TO GET A CREDENTIAL, so requiring one to read them is a circle. They
                // disclose nothing that the 401 on /api/mcp does not already announce in its own
                // WWW-Authenticate header.
                //
                // ⚠️ AND THEY LIVE AT THE SITE ROOT because RFC 9728 and RFC 8414 say so — which makes
                // this a deployment fact as much as a security rule: a reverse proxy that forwards only
                // /api leaves discovery answering with the frontend's HTML.
                .requestMatchers(AuthorizationRoutes.WELL_KNOWN_PATTERN).permitAll()
                // The three steps a client walks before it holds anything. Each grants nothing on its
                // own: registration hands out a label, authorization hands the browser to a screen where
                // a PERSON decides, and the token endpoint spends a code that only exists because
                // somebody already approved it. Review and approval are NOT here — they are the person's
                // half and require their session.
                //
                // ⚠️ And the consent screen itself is public, because a browser arrives at it carrying
                // nothing: it is an empty page until its own script has found the session this
                // application already put in the browser's storage. Everything it then calls — review,
                // approve — falls through to authenticated() below, which is where the actual gate is.
                .requestMatchers(
                    mcpRoutes.registration(),
                    mcpRoutes.authorization(),
                    mcpRoutes.token(),
                    mcpRoutes.consentRoute()).permitAll()
                // ⚠️ Avatar bytes, and the only route here that serves data rather than protocol
                // metadata. It is public because an <img> tag sends no Authorization header and cannot
                // be given one — the alternative is fetching every thumbnail as a blob in JavaScript.
                //
                // The address is a capability, not a name: it carries a random registry identifier, so
                // it cannot be constructed from knowing who somebody is, cannot be walked to the next
                // person, and is only ever learned from an authenticated response that already showed
                // you that member. And what may be stored behind it is an allowlist of raster image
                // types with SVG deliberately absent — see PublicAvatarController, which is where the
                // whole argument is written down.
                .requestMatchers(PublicAvatarRoutes.PATTERN).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                // ⚠️ Named explicitly, unlike before: there are two JwtDecoder beans now, and a
                // by-type lookup between them fails at startup rather than picking wrong — but only
                // because it is stated here. Never let this resolve implicitly again.
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter))
                .protectedResourceMetadata(metadata ->
                    metadata.protectedResourceMetadataCustomizer(this::describeThisResource)));

        return httpSecurity.build();
    }

    /**
     * Whether a request is for the protocol endpoint, judged on the address as sent.
     *
     * <p>Independent of servlet mappings, path-pattern parsing and any context path — see
     * {@link #mcpSecurityFilterChain} for what happens when it is not.
     */
    private static boolean isProtocolRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        return path.equals(McpEndpoint.PATH) || path.startsWith(McpEndpoint.PATH + "/");
    }

    /**
     * Replaces the default scope-based authorities with {@link MemberAuthoritiesConverter}'s
     * locally-resolved global tier ({@code ROLE_ADMIN} / {@code ROLE_USER}), since Identity's tokens
     * carry no Tessera authorization claims.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(MemberAuthoritiesConverter memberAuthoritiesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(memberAuthoritiesConverter);
        return converter;
    }

    /**
     * Identity's tokens — everything a browser does.
     *
     * <p>⚠️ {@code @Primary} because {@code McpAuthorizationConfiguration} contributes a second
     * {@link JwtDecoder} for the protocol endpoint. This is the general case; that one is reachable only
     * where it is named.
     */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties resourceServerProperties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri(resourceServerProperties.getJwt().getJwkSetUri())
            .build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(requiredAudience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "Token audience must contain '" + requiredAudience + "'", null));

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienceValidator));

        return jwtDecoder;
    }

}
