package net.innoventa.tessera.dto.agent;

import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentAuthority;
import org.jmouse.ai.agent.AgentConnection;

import java.time.Instant;

/**
 * One connection a person holds, as the account screen lists it.
 *
 * <p>⚠️ <strong>Nothing here is a credential.</strong> The access token is never stored and the renewal
 * credential only as a digest, so this is the connection's biography: which agent it belongs to, who
 * holds it, when it was approved, when it was last used, and whether it has been ended. Which is exactly
 * what somebody needs to decide whether to end it.
 *
 * <p><strong>The agent is carried alongside, flattened.</strong> A connection on its own cannot answer
 * <em>what may this thing do</em> — that is the persona's question — and a screen that listed connections
 * without it would offer a revoke button and no reason to press one.
 */
public record ConnectionResponse(
        String         id,
        String         agentId,
        String         agentName,
        AgentAuthority authority,
        boolean        agentEnabled,
        String         clientName,
        Instant        issuedAt,
        Instant        lastUsedAt,
        Instant        revokedAt,
        boolean        active
) {

    public static ConnectionResponse from(AgentConnection connection, Agent agent) {
        return new ConnectionResponse(
                connection.id(),
                agent.id(),
                agent.name(),
                agent.authority(),
                agent.enabled(),
                connection.clientName(),
                connection.issuedAt(),
                connection.lastUsedAt(),
                connection.revokedAt(),
                // ⚠️ Both halves, because either can stop it working and a screen saying "active" about a
                // live connection under a switched-off agent would be telling somebody the opposite of
                // what will happen on the next call.
                agent.enabled() && connection.canBeRenewed(Instant.now()));
    }
}
