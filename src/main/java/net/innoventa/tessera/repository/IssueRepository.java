package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, String> {

    List<Issue> findByProjectIdOrderByRankAsc(String projectId);

    /**
     * The cross-project search (ticket 10). {@code projectIds} is the set the caller may browse, resolved
     * before this is called and never widened here — every other argument only narrows it further, so a
     * filter can never become a way to see more (ADR-0008). Paged in the database rather than in memory:
     * "everything I can see" is the one query with no natural bound on its result.
     */
    @Query("""
        select issue from Issue issue
        where issue.projectId in :projectIds
          and (:projectId is null or issue.projectId = :projectId)
          and (:statusId is null or issue.statusId = :statusId)
          and (:assigneeMemberId is null or issue.assigneeMemberId = :assigneeMemberId)
          and (:text is null or lower(issue.summary) like :text or lower(issue.issueKey) like :text)
        """)
    Page<Issue> search(
        @Param("projectIds") Collection<String> projectIds,
        @Param("projectId") String projectId,
        @Param("statusId") String statusId,
        @Param("assigneeMemberId") String assigneeMemberId,
        @Param("text") String text,
        Pageable pageable
    );

    Optional<Issue> findByIssueKey(String issueKey);

    List<Issue> findByParentIdOrderByRankAsc(String parentId);

    boolean existsByParentId(String parentId);

    /** The current maximum rank in a project, so a newly-created issue can be appended after it. */
    Optional<Issue> findFirstByProjectIdOrderByRankDesc(String projectId);

}
