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
                .requestMatchers("/actuator/health").permitAll()
                // The bundled React shell (built into src/main/resources/static by the
                // frontend-maven-plugin, see pom.xml) must load without a token; its own /api/**
                // calls stay authenticated below. The SPA obtains a token via OIDC Authorization
                // Code + PKCE against Identity, exactly as Moneta and Central already do.
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/favicon.svg", "/vite.svg")
                .permitAll()
                // Client-side SPA routes (React Router owns them, SinglePageApplicationController
                // forwards them to index.html). A browser hitting one directly must receive the shell
                // so the app can boot and run its own OIDC sign-in — reachable without a token. The
                // shell's own /api/** calls stay authenticated below.
                .requestMatchers("/dashboard", "/projects/**", "/boards/**", "/backlog/**", "/issues/**", "/settings/**")
                .permitAll()
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
