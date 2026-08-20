package net.innoventa.tessera.security;

import lombok.RequiredArgsConstructor;
import org.jmouse.ai.mcp.authorization.server.AgentCredentials;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentDirectory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Which agent, if any, is behind the request being served.
 *
 * <h2>Why this is ambient rather than a parameter</h2>
 *
 * <p>An issue and a comment are written by the same services whether a person typed them or an agent
 * asked for them — that sameness is the point, and it is what keeps a tool call and a browser click from
 * drifting into two code paths. Threading "and by the way, which agent" through those services would mean
 * a new argument on a dozen methods, {@code null} at every call site but two, and a compiler that cannot
 * tell anybody they forgot one.
 *
 * <p>So it is read from the same place the caller's identity already comes from: the token on the
 * request. ⚠️ <strong>This is legitimate precisely because it decides nothing.</strong> Authorization is
 * settled long before a service runs, by the engine, against a {@code Subject}. What this answers is a
 * question of record-keeping — <em>whose name goes on the row</em> — and getting it wrong writes a
 * misleading badge rather than permitting something.
 *
 * <p>⚠️ <strong>The name is looked up rather than taken off the token.</strong> The token carries the
 * client's self-declared name from the day it was minted, and an agent can be renamed since. The row
 * wants the name as it is at the moment of writing, because that is what a snapshot means.
 */
@Component
@RequiredArgsConstructor
public class CallingAgent {

    private final AgentDirectory agents;

    /** The agent behind this request, or empty where a person is at the keyboard. */
    public Optional<Agent> current() {
        return currentAgentId().flatMap(agents::find);
    }

    private Optional<String> currentAgentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object         principal      = authentication == null ? null : authentication.getPrincipal();

        if (!(principal instanceof Jwt token)) {
            return Optional.empty();
        }

        return Optional.ofNullable(token.getClaimAsString(AgentCredentials.AGENT_CLAIM))
                .filter(claim -> !claim.isBlank());
    }
}
