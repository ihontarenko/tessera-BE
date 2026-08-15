package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.jmouse.ai.mcp.authorization.server.CredentialIssuer;
import org.springframework.stereotype.Component;

/**
 * What minting means in Tessera, said to a library that must not know.
 *
 * <p>The shared flow walks the protocol and stops at exactly this line. What it hands over is an opaque
 * reference and a client's self-declared name; what comes back is a token, a refresh token and a
 * lifetime. It never learns that the reference is a member's identifier, that the token is HS256, or
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

    private final MemberRepository     members;
    private final McpCredentialService credentials;

    /**
     * ⚠️ The member is looked up again here, at redemption, rather than carried through the code.
     *
     * <p>An approval and its redemption are minutes apart at most, but they are two requests, and the
     * only party whose standing matters is the one that actually turned up. A member who has been removed
     * between the two gets a refusal rather than a credential.
     */
    @Override
    public IssuedCredential issue(ApprovedAuthorization approval) {
        Member member = members.findById(approval.subjectReference())
                .orElseThrow(() -> new McpAuthorizationException(
                        "The member this code was approved by no longer exists."));

        return asIssued(credentials.issueFor(member, approval.clientName()));
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
