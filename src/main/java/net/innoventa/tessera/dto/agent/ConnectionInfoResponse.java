package net.innoventa.tessera.dto.agent;

/**
 * The one thing somebody needs in order to connect a client: the address to point it at.
 *
 * <p>⚠️ <strong>Asked of the server rather than derived from the browser's own origin.</strong> Those are
 * the same thing once deployed and are <em>not</em> the same thing in development, where the interface is
 * a dev server on one port and the API answers on another — a page that printed its own origin printed a
 * URL nothing serves the protocol on. It is also the address the discovery documents publish, so a
 * client configured with anything else is refused by its own resource check before it ever reaches
 * Tessera.
 */
public record ConnectionInfoResponse(String serverUrl) {
}
