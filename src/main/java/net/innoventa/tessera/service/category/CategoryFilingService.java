package net.innoventa.tessera.service.category;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.CategoryEntityType;
import net.innoventa.tessera.domain.EntityCategory;
import net.innoventa.tessera.domain.EntityCategoryId;
import net.innoventa.tessera.repository.CountByKey;
import net.innoventa.tessera.repository.EntityCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The polymorphic half of the tree: which thing is filed where (TSSR-15).
 *
 * <p><strong>Separate from {@link CategoryService} on purpose.</strong> That one owns the shape of the
 * tree — parents, order, depth, what may be deleted — and knows nothing about what is in it. This one
 * owns membership and knows nothing about the shape. A second kind of content arrives here and touches
 * nothing there, which is the entire argument for a membership table rather than a {@code category_id}
 * column on whatever gets filed.
 *
 * <p>⚠️ <strong>Filing nowhere is a state, not a missing value.</strong> Every page starts uncategorised
 * and many stay that way, so {@link #fileInto} with a null category is not an error — it is how a page
 * is taken back out of the tree.
 */
@Service
@RequiredArgsConstructor
public class CategoryFilingService {

    private final EntityCategoryRepository entityCategoryRepository;

    /**
     * Put one thing into one category, or take it out of the tree entirely.
     *
     * <p>⚠️ <strong>It does not check that the category is in the same project.</strong> That is the
     * caller's, and it has to be: this service is deliberately blind to what it files, so it cannot ask
     * a page which project it belongs to. {@link CategoryService#requireCategory} is where the check
     * lives, and every caller here goes through it first.
     */
    @Transactional
    public void fileInto(CategoryEntityType entityType, String entityId, String categoryId) {
        EntityCategoryId id = EntityCategoryId.of(entityType, entityId);

        if (categoryId == null) {
            // Looked up rather than deleted by key: taking an already-uncategorised page out of the tree
            // is a request that should succeed quietly, and `deleteById` has not always been willing to.
            entityCategoryRepository.findById(id).ifPresent(entityCategoryRepository::delete);
            return;
        }

        entityCategoryRepository.save(EntityCategory.builder()
            .id(id)
            .categoryId(categoryId)
            .build());
    }

    /** Where one thing is filed, if anywhere. */
    @Transactional(readOnly = true)
    public Optional<String> categoryOf(CategoryEntityType entityType, String entityId) {
        return entityCategoryRepository.findById(EntityCategoryId.of(entityType, entityId))
            .map(EntityCategory::getCategoryId);
    }

    /**
     * Where each of these is filed — one query for a whole list.
     *
     * <p>The absent ones are simply missing from the map rather than mapped to null, so a caller reads
     * "no entry" as "uncategorised" without a null check that could be forgotten.
     */
    @Transactional(readOnly = true)
    public Map<String, String> categoriesOf(CategoryEntityType entityType, Collection<String> entityIds) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> byEntityId = new HashMap<>();

        for (EntityCategory filing : entityCategoryRepository.findByIdEntityTypeAndIdEntityIdIn(entityType, entityIds)) {
            byEntityId.put(filing.getId().getEntityId(), filing.getCategoryId());
        }

        return byEntityId;
    }

    /** What is filed in one category, of one kind. */
    @Transactional(readOnly = true)
    public List<String> contentsOf(String categoryId, CategoryEntityType entityType) {
        return entityCategoryRepository.findByCategoryIdAndIdEntityType(categoryId, entityType).stream()
            .map(filing -> filing.getId().getEntityId())
            .toList();
    }

    /** How many of each kind sit in each category — one statement for the whole tree. */
    @Transactional(readOnly = true)
    public Map<String, Long> countsByCategory(Collection<String> categoryIds, CategoryEntityType entityType) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> counts = new HashMap<>();

        for (CountByKey row : entityCategoryRepository.countByCategory(categoryIds, entityType)) {
            counts.put(row.key(), row.count());
        }

        return counts;
    }

    /** Whether anything at all is filed here — asked before a category may be deleted. */
    @Transactional(readOnly = true)
    public boolean holdsAnything(String categoryId) {
        return entityCategoryRepository.existsByCategoryId(categoryId);
    }

}
