package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * A project's category tree.
 *
 * <p>⚠️ <strong>Every read here is the whole project's tree</strong>, never one level of it. The tree is
 * tens of rows and the screen draws all of it, so a query per level would be a fan of round trips for a
 * shape that is assembled in memory anyway — see {@code CategoryTree}.
 */
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByProjectIdOrderBySortOrderAscNameAsc(String projectId);

    Optional<Category> findByProjectIdAndSlug(String projectId, String slug);

    boolean existsByProjectIdAndSlug(String projectId, String slug);

    /** Whether anything sits under this one — the first half of "may it be deleted". */
    boolean existsByParentId(String parentId);

}
