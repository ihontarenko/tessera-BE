package net.innoventa.tessera.ai.authorization;

import org.jmouse.ai.mcp.authorization.server.AgentCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What makes a self-contained credential revocable: every protocol call asks whether the agent and the
 * connection it was issued against are both still good for it.
 *
 * <p>Without this, ending a connection would mean waiting out the access token — and a credential that
 * cannot be taken back before it expires is one nobody can safely hand out for a month.
 *
 * <p><strong>Both claims are required, and a token missing either was not minted here.</strong>
 * {@code cid} names the connection and {@code aid} the agent; the two answer different halves of the same
 * question — <em>has this client been ended</em> and <em>has this persona been switched off</em> — and a
 * token carrying only one could be refused for the wrong reason or not at all.
 *
 * <p>⚠️ <strong>It also stamps "last used", which is a write from a validator and deliberate.</strong>
 * This is the one place every protocol call demonstrably passes through, and the alternative — a servlet
 * filter of its own beside the transport — would be a second thing to keep in step with the first. The
 * write is rate-limited inside {@link AgentCredentials} so a busy client does not turn every tool call
 * into an update.
 */
@RequiredArgsConstructor
public class McpCredentialValidator implements OAuth2TokenValidator<Jwt> {

    private final AgentCredentials credentials;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String connectionId = token.getClaimAsString(AgentCredentials.CREDENTIAL_CLAIM);
        String agentId      = token.getClaimAsString(AgentCredentials.AGENT_CLAIM);

        if (isBlank(connectionId) || isBlank(agentId)) {
            return refuse("This token does not name both a connection and an agent, so it was not issued "
                        + "for the Model Context Protocol endpoint.");
        }

        // ⚠️ The refusal sentence is the library's, so this product and the next say the same thing about
        // a switched-off agent — and so that a new reason to refuse arrives here without an edit.
        return credentials.admit(agentId, connectionId)
                .map(McpCredentialValidator::refuse)
                .orElseGet(() -> {
                    credentials.noteUsage(agentId, connectionId);

                    return OAuth2TokenValidatorResult.success();
                });
    }

    private static boolean isBlank(String claim) {
        return claim == null || claim.isBlank();
    }

    private static OAuth2TokenValidatorResult refuse(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", description, null));
    }
}
