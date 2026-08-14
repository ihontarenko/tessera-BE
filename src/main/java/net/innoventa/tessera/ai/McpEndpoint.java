package net.innoventa.tessera.ai;

/**
 * The one path the Model Context Protocol is served on.
 *
 * <p>Two unrelated things have to agree on it: the transport that publishes the protocol there, and the
 * security rule that decides what a credential reaching it may do. A constant is what keeps them from
 * drifting — and {@code McpEndpointAgreement} is what will refuse the boot if they ever do, once there
 * is a second value to check against.
 *
 * <p>It sits under {@code /api} so the Vite proxy and any reverse proxy already forward it, and
 * deliberately does not say "ai": this is a tool protocol, not an AI feature. Innoventa chose the same
 * path for the same two reasons.
 */
public final class McpEndpoint {

    /** JSON-RPC on POST, an event stream on GET, and a session teardown on DELETE. */
    public static final String PATH = "/api/mcp";

    /** The servlet mapping, and the pattern a security rule would use. */
    public static final String ALL_PATTERN = PATH + "/*";

    private McpEndpoint() {
    }
}
