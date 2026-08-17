package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.mcp.authorization.server.CredentialIssuer;
import org.springframework.stereotype.Component;

/**
 * What minting means in Tessera, said to a library that must not know.
 *
 * <p>The shared flow walks the protocol and stops at exactly this line. What it hands over is an opaque
 * reference and a client's self-declared name; what comes back is a token, a refresh token and a
 * lifetime. It never learns that the reference is an agent's identifier, that the token is HS256, or
 * that the secret behind it is one only Tessera holds — and that last part is why this interface exists
 * at all, because the other product using the same flow answers the same question a completely different
 * way.
 *
 * <p>⚠️ <strong>Its presence is also the switch.</strong> The shared endpoints are auto-configured only
 * where a {@code CredentialIssuer} bean exists, so deleting this class does not leave three public routes
 * mapped with nothing behind them — it unmaps them.
 *
 * <p>See {@link McpCredentialService} for the confinement, the {@code cid} claim, and why a self-contained
 * token that lasts a month is nonetheless revocable within one call.
 */
@Component
@RequiredArgsConstructor
public class TesseraCredentialIssuer implements CredentialIssuer {

    private final McpCredentialService credentials;

    /**
     * ⚠️ <strong>The reference is an agent now, and it used to be a member.</strong>
     *
     * <p>The consent screen offered the approving person and nothing else, so the only thing to carry was
     * who they were; which agent a client became was then worked out on this side, out of their sight.
     * The screen lists their agents now — the other product's shape, and the reason a person can attach a
     * second client to a persona they already have instead of finding out afterwards which way it went.
     *
     * <p>Everything downstream of the reference is looked up again here rather than carried through the
     * code: an approval and its redemption are minutes apart at most, but they are two requests, and the
     * only party whose standing matters is the one that actually turned up.
     */
    @Override
    public IssuedCredential issue(ApprovedAuthorization approval) {
        Agent agent = credentials.approvedAgent(
                approval.subjectReference(), approval.clientName(), approval.clientId());

        return asIssued(credentials.issueFor(agent, approval.clientName(), approval.clientId()));
    }

    @Override
    public IssuedCredential renew(String refreshToken) {
        return asIssued(credentials.renew(refreshToken));
    }

    private IssuedCredential asIssued(McpCredentialService.IssuedCredential credential) {
        return new IssuedCredential(
                credential.accessToken(), credential.refreshToken(), credential.expiresIn());
    }
}
