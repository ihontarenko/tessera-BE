package net.innoventa.tessera.dto.category;

import java.util.List;

/**
 * One category and everything under it, as the tree is drawn.
 *
 * <p>⚠️ <strong>Nested rather than flat with a {@code parentId}.</strong> The client draws a tree either
 * way; what the nesting buys is that it cannot draw a wrong one — a flat list makes every consumer
 * re-derive the shape, and the first one to get an orphan wrong shows a section at the root that
 * belongs three levels down.
 *
 * <p>{@code itemCount} is what is filed <em>directly</em> here, not the subtree's total. A count that
 * summed descendants would say "12" beside a section whose own page list is empty, which reads as a bug
 * every time.
 */
public record CategoryNode(
    String id,
    String name,
    String slug,
    int sortOrder,
    long itemCount,
    List<CategoryNode> children
) {
}
