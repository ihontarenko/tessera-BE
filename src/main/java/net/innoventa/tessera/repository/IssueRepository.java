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
          and (:openOnly = false or issue.resolutionId is null)
          and (:text is null or lower(issue.summary) like :text or lower(issue.issueKey) like :text)
        """)
    Page<Issue> search(
        @Param("projectIds") Collection<String> projectIds,
        @Param("projectId") String projectId,
        @Param("statusId") String statusId,
        @Param("assigneeMemberId") String assigneeMemberId,
        /** Open is {@code resolution IS NULL} — the invariant, not a status name (ADR-0004). */
        @Param("openOnly") boolean openOnly,
        @Param("text") String text,
        Pageable pageable
    );

    Optional<Issue> findByIssueKey(String issueKey);

    List<Issue> findByParentIdOrderByRankAsc(String parentId);

    boolean existsByParentId(String parentId);

    /** The current maximum rank in a project, so a newly-created issue can be appended after it. */
    Optional<Issue> findFirstByProjectIdOrderByRankDesc(String projectId);

    // ── What holds a configuration row ────────────────────────────────────────
    //
    // Every count below answers one half of "you cannot delete this, here is what has it". They are
    // grouped queries rather than a count per row on purpose: the Administration screen shows a number
    // beside every status and every issue type at once, and a query per row is how a catalog of thirty
    // becomes sixty round trips.

    @Query("select new net.innoventa.tessera.repository.CountByKey(issue.statusId, count(issue)) "
           + "from Issue issue group by issue.statusId")
    List<CountByKey> countIssuesByStatus();

    @Query("select new net.innoventa.tessera.repository.CountByKey(issue.issueTypeId, count(issue)) "
           + "from Issue issue group by issue.issueTypeId")
    List<CountByKey> countIssuesByIssueType();

    @Query("select new net.innoventa.tessera.repository.CountByKey(issue.priorityId, count(issue)) "
           + "from Issue issue group by issue.priorityId")
    List<CountByKey> countIssuesByPriority();

    /** ⚠️ The null bucket is every open issue — {@code resolution IS NULL} is the invariant (ADR-0004). */
    @Query("select new net.innoventa.tessera.repository.CountByKey(issue.resolutionId, count(issue)) "
           + "from Issue issue group by issue.resolutionId")
    List<CountByKey> countIssuesByResolution();

    long countByStatusId(String statusId);

    /**
     * Issues in a status that are still open — {@code resolution IS NULL} is what open means (ADR-0004).
     * The sharp half of "you are about to make this status a Done one": these would sit in the Done
     * column and still be open, which reads to everybody as a bug in the board.
     */
    long countByStatusIdAndResolutionIdIsNull(String statusId);

    long countByIssueTypeId(String issueTypeId);

    long countByPriorityId(String priorityId);

    long countByResolutionId(String resolutionId);

    @Query("select count(distinct issue.projectId) from Issue issue where issue.statusId = :statusId")
    long countProjectsHoldingStatus(@Param("statusId") String statusId);

    @Query("select count(distinct issue.projectId) from Issue issue where issue.issueTypeId = :issueTypeId")
    long countProjectsHoldingIssueType(@Param("issueTypeId") String issueTypeId);

    @Query("select count(distinct issue.projectId) from Issue issue where issue.priorityId = :priorityId")
    long countProjectsHoldingPriority(@Param("priorityId") String priorityId);

    @Query("select count(distinct issue.projectId) from Issue issue where issue.resolutionId = :resolutionId")
    long countProjectsHoldingResolution(@Param("resolutionId") String resolutionId);

    /**
     * How many issues in this status each project holds — the input to "how many cards change column".
     * A board belongs to a project, so the answer has to be per project rather than one total.
     */
    @Query("select new net.innoventa.tessera.repository.CountByKey(issue.projectId, count(issue)) "
           + "from Issue issue where issue.statusId = :statusId group by issue.projectId")
    List<CountByKey> countIssuesInStatusByProject(@Param("statusId") String statusId);

    /**
     * Every parent/child issue-type pairing that exists, counted — the input to "changing this level
     * would invalidate N hierarchies".
     *
     * <p>An entity join rather than a mapped association: ids are stored flat throughout this schema
     * (no JPA relations), so the parent is joined on its identifier like any other row.
     */
    @Query("select new net.innoventa.tessera.repository.IssueTypePairCount("
           + "  parent.issueTypeId, child.issueTypeId, count(child)) "
           + "from Issue child join Issue parent on parent.id = child.parentId "
           + "group by parent.issueTypeId, child.issueTypeId")
    List<IssueTypePairCount> countParentChildTypePairs();

}
