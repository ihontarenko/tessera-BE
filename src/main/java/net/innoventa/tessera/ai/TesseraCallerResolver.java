package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.ai.authorization.McpCredentialService;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.CallerAttributes;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolRefusedException;
import org.jmouse.ai.agent.AgentCallers;
import org.jmouse.ai.spi.CallerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Who Tessera is running a tool call as.
 *
 * <p><strong>Two surfaces, two answers, and the difference is whether an agent is involved.</strong>
 *
 * <ul>
 *   <li><strong>The in-application assistant</strong> runs under a person's own browser session. There is
 *       no agent, the caller and the acting subject are the same member, and
 *       {@link CallerIdentity#of(String)} says exactly that.
 *   <li><strong>The protocol endpoint</strong> runs under a credential naming an agent and a connection.
 *       {@link AgentCallers} turns those into the identity, and which identity that turns out to be is
 *       {@code AgentAuthority}'s answer, not this class's — an agent inheriting its owner's authority
 *       authorizes as the owner, one restricted to its own grants authorizes as itself and is capped.
 * </ul>
 *
 * <p>⚠️ <strong>The branch is on the claim, not on a guess.</strong> A token carrying {@code aid} was
 * minted for the protocol and nothing else can mint one — the two surfaces validate against different
 * signatures. So there is no inference here, and no surface to get wrong.
 *
 * <p>⚠️ <strong>The identifier is the member's, not the token's subject.</strong> Everything Tessera
 * authorizes is keyed on a {@code Member} row, so carrying the identity-provider subject would mean every
 * handler translating it again — and the translation is a query, so it would be thirty of them. The
 * subject travels as an attribute for anything that wants to know where the member came from.
 *
 * <p>⚠️ <strong>The agent survives in the attributes under both authorities.</strong> Where it inherits,
 * the caller identifier <em>is</em> the owner's, so {@link CallerAttributes#AGENT_ID} is the only place
 * the agent is named — and provenance reads it. Attributes are display and record-keeping and never an
 * authorization input, which is the right register for <em>which of my agents did this</em>.
 */
@Component
@RequiredArgsConstructor
public class TesseraCallerResolver implements CallerResolver {

    private final MemberService         memberService;
    private final McpCredentialService  credentials;

    @Override
    public CallerIdentity resolve() {
        Jwt    token   = currentToken();
        Member member  = memberService.requireBySubject(token.getClaimAsString(JwtClaimNames.SUB));
        String agentId = token.getClaimAsString(McpCredentialService.AGENT_CLAIM);

        if (agentId == null || agentId.isBlank()) {
            return asPerson(member);
        }

        return asAgent(member, agentId, token.getClaimAsString(McpCredentialService.CREDENTIAL_CLAIM));
    }

    /** A person at the assistant: one identity, and nothing below had to learn about it. */
    private CallerIdentity asPerson(Member member) {
        return CallerIdentity.of(member.getId()).with(Map.of(
                CallerAttributes.CALLER_NAME,   displayNameOf(member),
                CallerAttributes.SUBJECT_CLAIM, member.getSubject()));
    }

    /**
     * An agent at the protocol endpoint.
     *
     * <p>⚠️ <strong>Refused rather than downgraded when the rows are gone.</strong> A token naming an
     * agent that no longer exists could be treated as an ordinary person's session — the permissive
     * direction, and one that would let a revoked connection keep working by losing the very claim that
     * makes it revocable. The protocol filter already refused this case; if it somehow did not, this is
     * the second door and it is shut.
     */
    private CallerIdentity asAgent(Member member, String agentId, String connectionId) {
        return credentials.actingAgent(agentId, connectionId)
                .map(acting -> AgentCallers.of(acting.agent(), acting.connection()).with(Map.of(
                        CallerAttributes.CALLER_NAME,   displayNameOf(member),
                        CallerAttributes.SUBJECT_NAME,  displayNameOf(member),
                        CallerAttributes.SUBJECT_CLAIM, member.getSubject())))
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.NO_CALLER,
                        "This credential names an agent or a connection that no longer exists. Nothing "
                        + "was changed. Authorize the client again to reconnect."));
    }

    private static String displayNameOf(Member member) {
        String displayName = member.getDisplayName();

        return displayName == null || displayName.isBlank() ? member.getEmail() : displayName;
    }

    private Jwt currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object         principal      = authentication == null ? null : authentication.getPrincipal();

        if (principal instanceof Jwt token && token.getClaimAsString(JwtClaimNames.SUB) != null) {
            return token;
        }

        throw new ToolRefusedException(RefusalReason.NO_CALLER,
                "Nothing was authenticated, so there is nobody to run this as. Sign in and try again.");
    }
}
