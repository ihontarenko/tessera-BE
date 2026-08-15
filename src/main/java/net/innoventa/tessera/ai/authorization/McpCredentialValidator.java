package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What makes a self-contained credential revocable: every protocol call asks whether the connection it
 * was issued against still exists.
 *
 * <p>Without this, ending a connection would mean waiting out the access token — and a credential that
 * cannot be taken back before it expires is one nobody can safely hand out for a month. The claim it reads
 * is {@code cid}; a token without one was not minted here and is refused as such rather than treated as
 * unrevokable.
 *
 * <p>⚠️ <strong>It also stamps "last used", which is a write from a validator and deliberate.</strong>
 * This is the one place every protocol call demonstrably passes through, and the alternative — a servlet
 * filter of its own beside the transport — would be a second thing to keep in step with the first. The
 * write is rate-limited inside {@link McpCredentialService} so a busy client does not turn every tool call
 * into an update.
 */
@RequiredArgsConstructor
public class McpCredentialValidator implements OAuth2TokenValidator<Jwt> {

    private final McpCredentialService credentials;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String credentialId = token.getClaimAsString(McpCredentialService.CREDENTIAL_CLAIM);

        if (credentialId == null || credentialId.isBlank()) {
            return refuse("This token carries no connection reference, so it was not issued for the "
                        + "Model Context Protocol endpoint.");
        }

        if (!credentials.isHonoured(credentialId)) {
            return refuse("The connection this credential belongs to has been ended. Authorize the client "
                        + "again to reconnect.");
        }

        credentials.noteUsage(credentialId);

        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult refuse(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", description, null));
    }
}
