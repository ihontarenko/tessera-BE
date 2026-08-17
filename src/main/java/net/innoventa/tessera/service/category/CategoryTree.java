package net.innoventa.tessera.service.category;

import net.innoventa.tessera.domain.Category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One project's categories, in memory, with the questions a flat list cannot answer.
 *
 * <p>⚠️ <strong>This is what stands in for a nested set.</strong> Innoventa stores {@code treeLeft} and
 * {@code treeRight} so the database can answer "the whole subtree" in one statement, and pays a
 * renumbering of the table on every move. A project's wiki has tens of categories: loading all of them
 * and walking the pointers is one query and no writes, and it leaves the schema something a person can
 * edit by hand.
 *
 * <p>A snapshot, deliberately — it is built from a list that has already been read and never touches a
 * repository. That is what makes it usable both for drawing the tree and for validating a move without
 * two different notions of what the tree currently is.
 */
public final class CategoryTree {

    private final Map<String, Category> byId;
    private final Map<String, List<Category>> childrenByParentId;
    private final List<Category> roots;

    private CategoryTree(List<Category> categories) {
        byId = new HashMap<>();
        childrenByParentId = new LinkedHashMap<>();
        roots = new ArrayList<>();

        List<Category> ordered = categories.stream()
            .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName))
            .toList();

        for (Category category : ordered) {
            byId.put(category.getId(), category);
        }

        for (Category category : ordered) {
            // ⚠️ A parent that is not in this snapshot is treated as absent rather than followed. It can
            // only mean a category from another project, which a project-scoped read has no business
            // rendering — and dropping the row entirely would hide a section rather than misplace it.
            if (category.getParentId() == null || !byId.containsKey(category.getParentId())) {
                roots.add(category);
            } else {
                childrenByParentId.computeIfAbsent(category.getParentId(), parentId -> new ArrayList<>())
                    .add(category);
            }
        }
    }

    public static CategoryTree of(List<Category> categories) {
        return new CategoryTree(categories);
    }

    public List<Category> roots() {
        return List.copyOf(roots);
    }

    public List<Category> childrenOf(String categoryId) {
        return List.copyOf(childrenByParentId.getOrDefault(categoryId, List.of()));
    }

    public List<Category> siblingsOf(String parentId) {
        return parentId == null ? roots() : childrenOf(parentId);
    }

    public boolean contains(String categoryId) {
        return byId.containsKey(categoryId);
    }

    /** The category with this id, or null where this snapshot does not hold it. */
    public Category find(String categoryId) {
        return categoryId == null ? null : byId.get(categoryId);
    }

    /** How deep this category sits, counting itself — a root is 1. */
    public int depthOf(Category category) {
        int depth = 1;
        String parentId = category.getParentId();

        while (parentId != null && byId.containsKey(parentId)) {
            depth++;
            parentId = byId.get(parentId).getParentId();
        }

        return depth;
    }

    /** How many levels this category has beneath it — a leaf is 1, itself. */
    public int heightOf(String categoryId) {
        int tallestChild = childrenOf(categoryId).stream()
            .mapToInt(child -> heightOf(child.getId()))
            .max()
            .orElse(0);

        return tallestChild + 1;
    }

    /**
     * Whether {@code candidateAncestorId} sits somewhere above {@code categoryId} — the question a move
     * has to ask about itself.
     *
     * <p>⚠️ Walking upward rather than downward, and it matters: the answer costs the depth of one branch
     * instead of the size of a subtree, and the loop terminates on the root even if the stored pointers
     * somehow formed a cycle, which a downward search would not.
     */
    public boolean isDescendant(String categoryId, String candidateAncestorId) {
        Set<String> visited = new HashSet<>();
        String walker = categoryId;

        while (walker != null && visited.add(walker)) {
            if (walker.equals(candidateAncestorId)) {
                return true;
            }

            Category category = byId.get(walker);
            walker = category == null ? null : category.getParentId();
        }

        return false;
    }

}
