package net.innoventa.tessera.service.category;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Category;
import net.innoventa.tessera.domain.CategoryEntityType;
import net.innoventa.tessera.dto.category.CategoryNode;
import net.innoventa.tessera.dto.category.MoveCategoryRequest;
import net.innoventa.tessera.dto.category.SaveCategoryRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A project's category tree: reading it, adding to it, renaming, moving and removing (TSSR-15).
 *
 * <p><strong>It knows nothing about what is filed into it.</strong> The one place a kind of content
 * appears is {@link #tree}, which takes the {@link CategoryEntityType} whose counts the caller wants
 * beside each section — and takes it as an argument rather than assuming pages, so the wiki's screen and
 * a future file screen ask the same method two different questions.
 *
 * <p>⚠️ <strong>The whole tree is read per operation, including the writes.</strong> A move has to know
 * about depth and about its own descendants, a create has to know where the last sibling sits, and a
 * rename has to know which slugs are taken — none of which one row can answer. At tens of rows that is
 * one query; at the size where it would not be, this class is where a nested set goes.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryFilingService filingService;
    private final Supplier<String> idGenerator;

    /**
     * The project's tree, nested, with a count of what is filed directly in each section.
     *
     * <p>Two queries whatever the tree's shape: the categories, and one grouped count over all of them.
     */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree(String projectId, CategoryEntityType entityType) {
        List<Category> categories = categoryRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        CategoryTree tree = CategoryTree.of(categories);

        Map<String, Long> counts = filingService.countsByCategory(
            categories.stream().map(Category::getId).toList(), entityType);

        return tree.roots().stream().map(root -> toNode(root, tree, counts)).toList();
    }

    @Transactional
    public CategoryNode create(String projectId, SaveCategoryRequest request) {
        CategoryTree tree = loadTree(projectId);
        String parentId = requireSameProjectParent(projectId, tree, request.parentId());

        if (parentId != null && tree.depthOf(tree.find(parentId)) >= Category.MAXIMUM_DEPTH) {
            throw new BusinessRuleViolationException(
                "Categories nest %d levels deep at most — make this a section of its own instead."
                    .formatted(Category.MAXIMUM_DEPTH));
        }

        Category category = categoryRepository.save(Category.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .parentId(parentId)
            .name(request.name().trim())
            .slug(freeSlug(projectId, request.name()))
            .sortOrder(tree.siblingsOf(parentId).size())
            .build());

        return new CategoryNode(category.getId(), category.getName(), category.getSlug(),
                                category.getSortOrder(), 0, List.of());
    }

    /**
     * Rename one.
     *
     * <p>⚠️ <strong>The slug is re-derived, and that changes the section's address.</strong> The
     * alternative — freezing the slug at creation — leaves a section called "Runbooks" living at
     * {@code /wiki/c/notes} forever, which is worse: a stale identifier that nobody can see is how a
     * rename quietly becomes a lie. Nothing stores a category slug, so the cost is a stale bookmark.
     */
    @Transactional
    public CategoryNode rename(String projectId, String categoryId, SaveCategoryRequest request) {
        Category category = requireCategory(projectId, categoryId);
        String name = request.name().trim();

        if (!name.equals(category.getName())) {
            category.setName(name);
            category.setSlug(freeSlugExcept(projectId, name, category.getSlug()));
        }

        return describe(projectId, categoryRepository.save(category));
    }

    /** Move one under a new parent, at a chosen place among its new siblings. */
    @Transactional
    public CategoryNode move(String projectId, String categoryId, MoveCategoryRequest request) {
        Category category = requireCategory(projectId, categoryId);
        CategoryTree tree = loadTree(projectId);
        String parentId = requireSameProjectParent(projectId, tree, request.parentId());

        // ⚠️ Both halves of "a tree stays a tree". Dropping a section onto itself or onto something
        // beneath it would detach the whole branch from the root — every row still present, none of them
        // reachable, and no constraint in the schema able to notice.
        if (parentId != null && tree.isDescendant(parentId, categoryId)) {
            throw new BusinessRuleViolationException(
                "A section cannot be moved inside itself or inside one of its own subsections.");
        }

        int parentDepth = parentId == null ? 0 : tree.depthOf(tree.find(parentId));

        if (parentDepth + tree.heightOf(categoryId) > Category.MAXIMUM_DEPTH) {
            throw new BusinessRuleViolationException(
                "That move would nest sections more than %d levels deep — this one carries subsections of its own."
                    .formatted(Category.MAXIMUM_DEPTH));
        }

        category.setParentId(parentId);
        categoryRepository.save(category);
        reorderSiblings(projectId, parentId, categoryId, request.position());

        return describe(projectId, category);
    }

    /**
     * Remove one.
     *
     * <p>⚠️ <strong>Refused while anything is inside it</strong>, whether a subsection or a filed page,
     * and the refusal says which. The tempting alternatives are both worse: deleting the contents makes
     * a tidy-up destroy work, and silently re-filing them to the parent moves pages somebody was not
     * looking at. Emptying a section first is a decision, and it should be made by the person making it.
     */
    @Transactional
    public void delete(String projectId, String categoryId) {
        Category category = requireCategory(projectId, categoryId);

        if (categoryRepository.existsByParentId(category.getId())) {
            throw new BusinessRuleViolationException(
                "'%s' still has subsections. Move or remove them first.".formatted(category.getName()));
        }

        if (filingService.holdsAnything(category.getId())) {
            throw new BusinessRuleViolationException(
                "'%s' still holds pages. Move them elsewhere first — removing a section never removes what is in it."
                    .formatted(category.getName()));
        }

        categoryRepository.delete(category);
    }

    /**
     * The category with this id, in this project — the check every caller that files something has to
     * make.
     *
     * <p>⚠️ <strong>A category from another project is a 404, not a 403.</strong> Naming one back would
     * confirm it exists to somebody who cannot see the project it is in, which is the same disclosure
     * this product refuses everywhere else.
     */
    @Transactional(readOnly = true)
    public Category requireCategory(String projectId, String categoryId) {
        return categoryRepository.findById(categoryId)
            .filter(category -> category.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("No such section: " + categoryId));
    }

    /** The same, tolerating null — "filed nowhere" is a legitimate answer to "which category?". */
    @Transactional(readOnly = true)
    public String requireOptionalCategory(String projectId, String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }

        return requireCategory(projectId, categoryId).getId();
    }

    // ── The shape ─────────────────────────────────────────────────────────────

    private CategoryTree loadTree(String projectId) {
        return CategoryTree.of(categoryRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId));
    }

    private CategoryNode toNode(Category category, CategoryTree tree, Map<String, Long> counts) {
        List<CategoryNode> children = tree.childrenOf(category.getId()).stream()
            .map(child -> toNode(child, tree, counts))
            .toList();

        return new CategoryNode(category.getId(), category.getName(), category.getSlug(),
                                category.getSortOrder(), counts.getOrDefault(category.getId(), 0L), children);
    }

    /** One category as a node, with its own children — what a write returns so the client can re-place it. */
    private CategoryNode describe(String projectId, Category category) {
        CategoryTree tree = loadTree(projectId);
        Map<String, Long> counts = filingService.countsByCategory(List.of(category.getId()), CategoryEntityType.PAGE);

        return toNode(category, tree, counts);
    }

    private String requireSameProjectParent(String projectId, CategoryTree tree, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }

        if (!tree.contains(parentId)) {
            throw new ResourceNotFoundException("No such parent section: " + parentId);
        }

        return parentId;
    }

    /**
     * Renumbers one level so that {@code movedId} lands at {@code position}.
     *
     * <p>Rewritten from zero every time rather than nudged, because a level is a handful of rows and a
     * scheme of gaps is only worth its complexity when the writes are the expensive part. Here the read
     * already happened.
     */
    private void reorderSiblings(String projectId, String parentId, String movedId, int position) {
        List<Category> siblings = new ArrayList<>(loadTree(projectId).siblingsOf(parentId));
        siblings.removeIf(sibling -> sibling.getId().equals(movedId));

        int landing = Math.max(0, Math.min(position, siblings.size()));
        siblings.add(landing, requireCategory(projectId, movedId));

        for (int index = 0; index < siblings.size(); index++) {
            siblings.get(index).setSortOrder(index);
        }

        categoryRepository.saveAll(siblings);
    }

    // ── Slugs ─────────────────────────────────────────────────────────────────

    private String freeSlug(String projectId, String name) {
        return Slugs.unique(name, candidate -> categoryRepository.existsByProjectIdAndSlug(projectId, candidate));
    }

    /** The same, but a category keeping its own slug is not colliding with itself. */
    private String freeSlugExcept(String projectId, String name, String ownSlug) {
        return Slugs.unique(name, candidate ->
            !candidate.equals(ownSlug) && categoryRepository.existsByProjectIdAndSlug(projectId, candidate));
    }

}
