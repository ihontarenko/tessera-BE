package net.innoventa.tessera.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.Category;

/**
 * Create a category, or rename one.
 *
 * <p>Only the name travels. The slug is derived and kept unique by the service — a client that could
 * choose one would be choosing an identifier, and two clients would eventually choose the same.
 *
 * <p>⚠️ <strong>{@code parentId} is here for creation and ignored on rename.</strong> Moving is
 * {@code PUT …/position}, deliberately a different route: a rename that silently accepted a parent would
 * make "fix the typo" and "restructure the wiki" the same request, and the second needs an answer about
 * depth and cycles that the first should never have to think about.
 */
public record SaveCategoryRequest(
    @NotBlank @Size(max = Category.MAXIMUM_NAME_LENGTH) String name,

    /** Where it goes. Null puts it at the root — the ordinary case for a project's first sections. */
    String parentId
) {
}
