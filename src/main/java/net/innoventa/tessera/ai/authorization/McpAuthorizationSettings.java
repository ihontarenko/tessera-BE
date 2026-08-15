package net.innoventa.tessera.ai.authorization;

import java.time.Duration;

/**
 * What Tessera decides about a protocol credential, once the shared flow has taken everything it owns.
 *
 * <p>The walk itself — the routes, the code's lifetime, where a browser is sent to approve — moved into
 * {@code jmouse-ai-mcp-authorization} and is configured under {@code jmouse.mcp.authorization}. What is
 * left here is minting, which is the one thing the library refuses to have an opinion about: what a token
 * claims to be, who honours it, and how long anything lasts.
 *
 * <p>⚠️ <strong>{@code resourceUrl} is the token's {@code iss} as well as the API's address</strong>, and
 * the decoder validates against it. Changing one without the other refuses every credential already
 * issued, at the next call rather than at startup.
 *
 * @param resourceUrl          absolute base address of this API, without a trailing slash
 * @param audience             the {@code aud} claim a protocol token carries
 * @param accessTokenLifetime  how long a minted access token is honoured
 * @param refreshTokenLifetime how long a connection may be renewed for without asking again
 */
public record McpAuthorizationSettings(
        String   resourceUrl,
        String   audience,
        Duration accessTokenLifetime,
        Duration refreshTokenLifetime
) {

    public McpAuthorizationSettings {
        resourceUrl = withoutTrailingSlash(resourceUrl);
    }

    /** An absolute API address for a path a client is told to call. */
    public String apiUrl(String path) {
        return resourceUrl + path;
    }

    private static String withoutTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }

        return url;
    }
}
