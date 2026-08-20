package net.innoventa.tessera.config;

import lombok.Setter;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import net.innoventa.tessera.ai.authorization.McpCredentialValidator;
import org.jmouse.ai.mcp.authorization.server.AgentCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Everything the Model Context Protocol's authorization flow is made of, wired once.
 *
 * <p><strong>What used to be here and is not any more</strong>: the loopback allow-list, the proof-key
 * policy and the one-time code store. All three are requirements of the protocol rather than decisions of
 * this product, and {@code jmouse-ai-mcp-authorization} now declares them from
 * {@code jmouse.mcp.authorization} — along with the routes, the code's lifetime and the address a
 * person's browser is sent to. What Tessera decides is what is left: how long its own credentials last,
 * and the secret they are signed with.
 *
 * <h2>⚠️ The signing secret, and what happens without one</h2>
 *
 * <p>Absent, a random one is generated at startup and the fact is logged as a warning. That is the right
 * default for development — every credential dies on restart, a client notices its token no longer works,
 * re-authorizes, and nobody thinks about it. It is the <strong>wrong</strong> thing in a deployment for the
 * same reason plus one worse: with more than one instance, a credential minted by one is refused by the
 * next. Set {@code TESSERA_MCP_SIGNING_SECRET} anywhere real.
 *
 * <p>⚠️ <strong>32 bytes minimum, refused rather than padded.</strong> HS256 keys shorter than its digest
 * are what {@code MACSigner} rejects at the first mint — which is a failure at the token endpoint on
 * somebody's first connection attempt, long after the deploy that caused it. Failing at startup puts it
 * where somebody is watching.
 */
@Configuration
@ConfigurationProperties(prefix = "tessera.mcp")
@Setter
public class McpAuthorizationConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpAuthorizationConfiguration.class);

    /** HS256 signs with a key at least as long as its digest; anything shorter is refused outright. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** The secret protocol credentials are signed with. Empty means "generate one and warn". */
    private String signingSecret = "";

    /**
     * The address a client reaches this API at — the same one the resource metadata publishes.
     *
     * <p>⚠️ Still read here as well as by the library, because the two validators below are this
     * product's: the issuer a protocol token must carry is the address this server answers on.
     */
    @Value("${tessera.security.resource:http://localhost:8100}")
    private String resourceUrl;

    /** The audience a protocol credential carries, so it reads like every other token this server sees. */
    @Value("${tessera.security.audience:tessera}")
    private String audience;

    @Bean
    public JwtEncoder mcpTokenEncoder(SecretKey mcpSigningKey) {
        return NimbusJwtEncoder.withSecretKey(mcpSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * The decoder the protocol's own filter chain validates with — <strong>and no other chain does</strong>.
     *
     * <p>⚠️ Not named {@code jwtDecoder} and not primary: {@code SecurityConfiguration} keeps that bean for
     * Identity's tokens, and the two must never be confused for one another. That they cannot both verify
     * one token is what confines a protocol credential to this endpoint.
     */
    @Bean
    public JwtDecoder mcpJwtDecoder(SecretKey mcpSigningKey, AgentCredentials credentials) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(mcpSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(resourceUrl),
                audienceValidator(),
                new McpCredentialValidator(credentials)));

        return decoder;
    }

    @Bean
    public SecretKey mcpSigningKey() {
        if (signingSecret == null || signingSecret.isBlank()) {
            LOGGER.warn("No tessera.mcp.signing-secret is configured, so one was generated for this run. "
                      + "Every Model Context Protocol credential becomes invalid on restart and cannot be "
                      + "shared between instances — set TESSERA_MCP_SIGNING_SECRET anywhere but a laptop.");

            return generatedKey();
        }

        byte[] secret = signingSecret.getBytes(StandardCharsets.UTF_8);

        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("tessera.mcp.signing-secret is " + secret.length
                    + " bytes; HS256 needs at least " + MINIMUM_SECRET_BYTES
                    + ". Generate one rather than lengthening this by hand.");
        }

        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "Token audience must contain '" + audience + "'", null));
    }

    private static SecretKey generatedKey() {
        byte[] secret = new byte[MINIMUM_SECRET_BYTES];
        new SecureRandom().nextBytes(secret);

        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }
}
