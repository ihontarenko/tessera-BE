package net.innoventa.tessera.service.block.spi;

import net.innoventa.tessera.dto.block.PageBlockView;

/**
 * How this product answers one kind of {@code :::} directive.
 *
 * <p>The seam that keeps the block engine from knowing what an issue, a sprint or a board is. The engine
 * declares this interface and dispatches to whatever is registered; each implementation lives with the
 * concept that owns its meaning and is the only side that holds a repository.
 *
 * <p>⚠️ <strong>Optional, and the engine is complete with none registered.</strong> Every directive then
 * comes back a visible miss, which is a working document renderer for an installation with nothing to
 * embed rather than a broken one.
 *
 * <p>⚠️ <strong>Three of Innoventa's members are deliberately absent.</strong> Its version carries a
 * {@code publicSafe()} flag, and a {@code describe()} returning a label and a module for the editor's
 * block palette. None of them has anything to say here: Tessera has no public pages, so there is no
 * unauthenticated audience for a block to be withheld from; it has no per-workspace module system, so
 * every directive applies to every project; and the palette is a static list in
 * {@code tesseraMarkdownStack.ts} because the Markdown library's block picker takes its entries at
 * construction and cannot be handed a list fetched later. Copying them across would have been three
 * members that are always the same value — which is how a seam stops describing the product it is in.
 *
 * <p>The label belongs here the day any of those stops being true. It is one method plus a
 * {@code catalog()} on the engine, and the palette becomes a fetch.
 *
 * <p>⚠️ <strong>This package is not under {@code service.wiki}, on purpose.</strong> TSSR-19 moves pages
 * out to WiQi and leaves the resolvers here, because "what is TSSR-4 doing right now" is a question only
 * this product can answer. When that happens the engine's caller changes from a local controller to a
 * remote one and nothing in this package moves.
 */
public interface PageBlockResolver {

    /** The directive name this resolver handles, e.g. {@code "issue"}. Lowercase. */
    String directive();

    /**
     * Answer one directive.
     *
     * <p>⚠️ <strong>A miss is a return value, never an exception.</strong> Documents outlive their
     * subjects: an issue gets deleted, a sprint gets renamed, a project stops being visible to the
     * reader. All of those are ordinary and all of them render as a notice, so a resolver that cannot
     * answer returns {@link PageBlockView#miss} rather than throwing.
     */
    PageBlockView resolve(BlockRequest request);

}
