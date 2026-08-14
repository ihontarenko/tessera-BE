package net.innoventa.tessera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    /** Where a client is sent to get a token — Identity's base URL, not this service's. */
    @Value("${tessera.security.issuer:http://localhost:9090}")
    private String issuer;

    /**
     * What this server calls itself, which a client echoes back so Identity mints a token for Tessera
     * and not for something else.
     */
    @Value("${tessera.security.resource:http://localhost:8100}")
    private String resource;

    /** The path Spring Security both serves this document at and advertises in its 401. */
    static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

    /**
     * How a Model Context Protocol client finds out where to get a token.
     *
     * <ol>
     *   <li>It calls {@code /api/mcp} with no token and gets <strong>401</strong> carrying
     *       {@code WWW-Authenticate: Bearer resource_metadata="…"}. Spring Security emits that on its
     *       own; nothing here arranges it.
     *   <li>It fetches that document — this — and reads {@code authorization_servers}.
     *   <li>It runs authorization-code with PKCE against <em>Identity</em>, as the {@code tessera-mcp}
     *       client registered there, with a loopback redirect.
     *   <li>It comes back with a token whose {@code aud} is {@code tessera}, the only kind
     *       {@link #jwtDecoder} accepts.
     * </ol>
     *
     * <p><strong>Innoventa had to build all of that</strong> — authorization endpoints, a code store,
     * dynamic client registration, PKCE policy — because Innoventa <em>is</em> its own authorization
     * server, so both halves are its. Tessera is only the resource half, which is the shape the
     * protocol's specification assumes. It costs this method.
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
                .authorizationServer(issuer)
                // Header only. A token in a query string ends up in access logs, browser history and
                // proxy caches, and the protocol needs neither of the other two ways.
                .bearerMethods(methods -> {
                    methods.clear();
                    methods.add("header");
                })
                .tlsClientCertificateBoundAccessTokens(false);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity httpSecurity,
        JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        httpSecurity
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                // This backend serves an API and nothing else. Tessera/UI is a separate application
                // on its own origin, so there is no shell to let through unauthenticated and no
                // client-side route for this filter chain to know about — the browser never asks
                // this port for one.
                .requestMatchers("/actuator/health").permitAll()
                // ⚠️ Public, and it must be: a Model Context Protocol client reads this document to
                // find out WHERE TO GET A TOKEN, so requiring one to read it is a circle. It discloses
                // nothing that the 401 on /api/mcp does not already announce in its own
                // WWW-Authenticate header.
                .requestMatchers(METADATA_PATH).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .protectedResourceMetadata(metadata ->
                    metadata.protectedResourceMetadataCustomizer(this::describeThisResource)));

        return httpSecurity.build();
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

    @Bean
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
