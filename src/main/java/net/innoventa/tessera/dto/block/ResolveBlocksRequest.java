package net.innoventa.tessera.dto.block;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every directive one document contains, in one request.
 *
 * <p>⚠️ <strong>A batch, and it has to be.</strong> A page with twenty {@code :::issue} lines would
 * otherwise be twenty round trips, each one a permission resolution and a query, on a path that runs
 * every time somebody opens the page.
 *
 * <p>The ceiling is a backstop rather than a policy: no real page carries a hundred live blocks, and a
 * request that claims to is either a mistake or somebody probing the endpoint.
 */
public record ResolveBlocksRequest(
    @Size(max = 100) List<Directive> directives
) {

    /**
     * One directive as the reader found it.
     *
     * <p>The argument travels exactly as written, because the server checks it against the page's stored
     * text — a client that normalised it would be asking about a line the page does not contain.
     */
    public record Directive(String name, String argument) {
    }

}
