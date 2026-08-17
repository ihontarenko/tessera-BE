package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.WikiPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A project's wiki pages.
 *
 * <p>⚠️ <strong>Every query names the project.</strong> A page id is a UUID and is never guessable, but
 * a route that looked one up without its project would resolve a page belonging to a project the caller
 * cannot see — and would then have to remember to check. Asking for both makes the check the query.
 */
public interface WikiPageRepository extends JpaRepository<WikiPage, String> {

    List<WikiPage> findByProjectIdOrderByTitleAsc(String projectId);

    Optional<WikiPage> findByIdAndProjectId(String id, String projectId);

    Optional<WikiPage> findByProjectIdAndSlug(String projectId, String slug);

    boolean existsByProjectIdAndSlug(String projectId, String slug);

    List<WikiPage> findByIdInOrderByTitleAsc(Collection<String> ids);

    /**
     * Pages whose title or prose contains this text, in one project.
     *
     * <p>⚠️ <strong>A {@code LIKE} over the mirror column, not a full-text index.</strong> A project's
     * wiki is tens of documents; the scan is cheaper than the index would be to keep, and it works the
     * same on both engines this product runs on. The moment it stops being true, this is the one method
     * that changes.
     */
    @Query("""
        SELECT page FROM WikiPage page
        WHERE page.projectId = :projectId
          AND (LOWER(page.title) LIKE LOWER(CONCAT('%', :text, '%'))
            OR LOWER(page.contentMarkdown) LIKE LOWER(CONCAT('%', :text, '%')))
        ORDER BY page.updatedAt DESC
        """)
    List<WikiPage> search(String projectId, String text);

}
