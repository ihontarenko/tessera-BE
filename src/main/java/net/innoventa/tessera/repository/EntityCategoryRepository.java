package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.CategoryEntityType;
import net.innoventa.tessera.domain.EntityCategory;
import net.innoventa.tessera.domain.EntityCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * What is filed where.
 *
 * <p>⚠️ <strong>Every method here names the {@link CategoryEntityType}</strong>, even the ones a single
 * kind of content would not need. A query that asked only about a category would answer with rows the
 * caller cannot interpret the moment a second kind exists — and the whole reason this table is
 * polymorphic is that a second kind is expected.
 */
public interface EntityCategoryRepository extends JpaRepository<EntityCategory, EntityCategoryId> {

    List<EntityCategory> findByIdEntityTypeAndIdEntityIdIn(CategoryEntityType entityType, Collection<String> entityIds);

    List<EntityCategory> findByCategoryIdAndIdEntityType(String categoryId, CategoryEntityType entityType);

    /** Whether anything at all is filed here — the second half of "may this category be deleted". */
    boolean existsByCategoryId(String categoryId);

    /**
     * How many of each kind sit in each of these categories.
     *
     * <p>One statement for the whole tree rather than one per node: a sidebar showing a count beside
     * every section would otherwise cost a query per section, which is the shape that makes a tree feel
     * slow at exactly the size it becomes useful.
     */
    @Query("""
        SELECT new net.innoventa.tessera.repository.CountByKey(
                   entityCategory.categoryId, COUNT(entityCategory))
        FROM EntityCategory entityCategory
        WHERE entityCategory.categoryId IN :categoryIds
          AND entityCategory.id.entityType = :entityType
        GROUP BY entityCategory.categoryId
        """)
    List<CountByKey> countByCategory(Collection<String> categoryIds, CategoryEntityType entityType);

}
