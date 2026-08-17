package net.innoventa.tessera.dto.category;

/**
 * Where a category should sit: under whom, and in which place among its new siblings.
 *
 * <p>Both halves travel together because a drag is one gesture. Sending the parent and then the position
 * would leave a window where the tree is reordered but not re-parented, and a client that failed between
 * the two would have moved a section somewhere nobody asked for.
 */
public record MoveCategoryRequest(
    /** The new parent, or null for the root. */
    String parentId,

    /**
     * Its index among the new siblings, counting from zero and clamped to the list.
     *
     * <p>⚠️ An index rather than a sort-order value: the stored numbers are an implementation detail
     * with gaps in them, and a client sending one would be writing into that detail. What a drag knows
     * is "third from the top".
     */
    int position
) {
}
