package net.innoventa.tessera.service.block.spi;

import net.innoventa.tessera.domain.Member;

/**
 * One directive to answer.
 *
 * <p>⚠️ <strong>The caller travels with the request rather than being read from a security context.</strong>
 * A resolver has to narrow its answer to what this person may see — an issue in a project they hold
 * nothing at must come back a miss — and a thread-local is the wrong place to keep that: an MCP tool
 * call has neither the {@code SecurityContext} nor the request scope, so a resolver reading one would
 * work from a screen and quietly answer as nobody from a tool.
 *
 * @param argument what followed the directive name, trimmed and never blank
 * @param caller   who is reading the document
 */
public record BlockRequest(String argument, Member caller) {
}
