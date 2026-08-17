package net.innoventa.tessera.dto.wiki;

/**
 * Move a page into a section, or out of the tree entirely.
 *
 * <p>Its own route rather than a field on the save, because the two are different acts with different
 * costs: re-filing is a drag in a sidebar and must not require sending the whole document back, and a
 * save that carried a category would re-file a page every time somebody fixed a typo in a tab that had
 * a stale tree.
 */
public record FileWikiPageRequest(
    /** The section it goes into. Null takes it out of the tree — uncategorised is a place. */
    String categoryId
) {
}
