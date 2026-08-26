package net.innoventa.tessera.service.block.spi;

/**
 * One thing a document in another product could refer to.
 *
 * <p>The product-side half of a suggestion. It carries a <strong>relative path</strong> rather than a
 * URL, for the same reason {@link PageBlockResolver} returns a {@code PageBlockView} rather than a
 * wire type: a resolver knows where a thing lives <em>inside this product</em> and has no business
 * knowing which address a browser reaches this product at. The adapter that speaks the cross-product
 * contract composes the absolute one, exactly as it already does for a resolved block.
 *
 * @param reference ⚠️ what a document writes, and the whole point — {@code issue:9f3a21}, built from the
 *                  identifier that does <strong>not</strong> move. A consumer inserts it verbatim, so
 *                  this is the one place the format is decided
 * @param label     the short name it is known by — a key
 * @param title     the line somebody picks by — a summary
 * @param subtitle  the state around it, in one line. Optional
 * @param path      where it lives in this product, from the root: {@code /issues/TES-42}
 */
public record BlockSuggestion(
    String reference,
    String label,
    String title,
    String subtitle,
    String path
) {
}
