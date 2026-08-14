package net.innoventa.tessera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
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
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

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
