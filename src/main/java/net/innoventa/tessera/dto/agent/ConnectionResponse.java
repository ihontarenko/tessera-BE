package net.innoventa.tessera.dto.agent;

import net.innoventa.tessera.domain.McpCredential;

import java.time.LocalDateTime;

/**
 * One connection a person holds, as the account screen lists it.
 *
 * <p>⚠️ <strong>Nothing here is a credential.</strong> The access token is never stored and the refresh
 * token only as a digest, so this is the connection's biography: who holds it, when it was approved, when
 * it was last used, and whether it has been ended. Which is exactly what somebody needs to decide whether
 * to end it.
 */
public record ConnectionResponse(
        String        id,
        String        clientName,
        LocalDateTime issuedAt,
        LocalDateTime lastUsedAt,
        LocalDateTime revokedAt,
        boolean       active
) {

    public static ConnectionResponse from(McpCredential credential) {
        return new ConnectionResponse(
                credential.getId(),
                credential.getClientName(),
                credential.getIssuedAt(),
                credential.getLastUsedAt(),
                credential.getRevokedAt(),
                credential.canBeRenewed(LocalDateTime.now()));
    }
}
