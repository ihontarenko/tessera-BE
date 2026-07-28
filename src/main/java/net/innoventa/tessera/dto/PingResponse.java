package net.innoventa.tessera.dto;

import java.util.List;

/**
 * Smoke-test payload for {@code GET /api/ping}: echoes the authenticated subject and the token's
 * audiences so the full Identity login -> token -> resource-server chain can be verified end-to-end
 * before any real domain endpoint exists.
 */
public record PingResponse(String service, String subject, List<String> audiences) {
}
