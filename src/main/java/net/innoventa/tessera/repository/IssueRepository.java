package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, String>, JpaSpecificationExecutor<Issue> {

    List<Issue> findByProjectIdOrderByRankAsc(String projectId);

    /**
     * The project's issues that are still <em>in view</em> — everything above minus what has been put
     * away (TSSR-4). The board, the backlog and the project's issue list all read through this one
     * finder, which is what makes archiving leave every view at once instead of each screen growing its
     * own idea of what to hide.
     */
    List<Issue> findByProjectIdAndArchivedAtIsNullOrderByRankAsc(String projectId);

    /**
     * A project's finished work, newest first — what the Shipped screen is a view of.
     *
     * <p>Closed is {@code resolution IS NULL} inverted (ADR-0004), never a status name, and ordering is
     * by {@code resolvedAt} rather than {@code updatedAt}: this list answers <em>when did we deliver
     * it</em>, and a typo fixed last week must not push a March issue to the top. Archived and
     * unarchived alike — putting something away is what removes it from the other screens, not from the
     * record of what shipped.
     */
    List<Issue> findByProjectIdAndResolutionIdIsNotNullOrderByResolvedAtDesc(String projectId);

    /**
     * The cross-project search (ticket 10). {@code projectIds} is the set the caller may browse, resolved
     * before this is called and never widened here — every other argument only narrows it further, so a
     * filter can never become a way to see more (ADR-0008). Paged in the database rather than in memory:
     * "everything I can see" is the one query with no natural bound on its result.
     */
    /*
     * ⚠️ THE THREE JOINS ARE FOR ORDERING, NOT FOR FILTERING — nothing in the `where` mentions them.
     *
     * `issues` carries a priority, a type and a status as bare foreign keys, so `order by
     * issue.priorityId` orders by an opaque identifier: alphabetical over UUIDs, which reads as no order
     * at all. What "sort by priority" means is SEVERITY, and severity is `priorities.sequence` — a column
     * on the other table. Hence an ad-hoc join per catalogue, and hence `IssueSortOrder`'s paths being
     * `priority.sequence` / `type.hierarchyLevel` / `status.name`: those are these aliases.
     *
     * ⚠️ LEFT, not inner. Two of the three columns are `NOT NULL`, but an inner join would still make the
     * search silently drop any issue whose catalogue row was deleted underneath it — a filter appearing
     * where only an ordering was meant, which is the one thing a search must never do.
     */
    @Query(value = """
        select issue from Issue issue
          left join Priority priority on priority.id = issue.priorityId
          left join IssueType type on type.id = issue.issueTypeId
          left join Status status on status.id = issue.statusId
        where issue.projectId in :projectIds
          and (:projectId is null or issue.projectId = :projectId)
          and (:statusId is null or issue.statusId = :statusId)
          and (:assigneeMemberId is null or issue.assigneeMemberId = :assigneeMemberId)
          and (:openOnly = false or issue.resolutionId is null)
          and (:includeArchived = true or issue.archivedAt is null)
          and (:text is null or lower(issue.summary) like :text or lower(issue.issueKey) like :text)
        """,
        /*
         * ⚠️ Spelled out, without the joins. A derived count would carry all three of them for a number
         * that cannot depend on any — three extra scans per page of a search, on the one query that has
         * no natural bound.
         */
        countQuery = """
        select count(issue) from Issue issue
        where issue.projectId in :projectIds
          and (:projectId is null or issue.projectId = :projectId)
          and (:statusId is null or issue.statusId = :statusId)
          and (:assigneeMemberId is null or issue.assigneeMemberId = :assigneeMemberId)
          and (:openOnly = false or issue.resolutionId is null)
          and (:includeArchived = true or issue.archivedAt is null)
          and (:text is null or lower(issue.summary) like :text or lower(issue.issueKey) like :text)
        """)
    Page<Issue> search(
        @Param("projectIds") Collection<String> projectIds,
        @Param("projectId") String projectId,
        @Param("statusId") String statusId,
        @Param("assigneeMemberId") String assigneeMemberId,
        /** Open is {@code resolution IS NULL} — the invariant, not a status name (ADR-0004). */
        @Param("openOnly") boolean openOnly,
        /**
         * Archived issues are out of the answer unless asked for (TSSR-4). Search is the one place they
         * stay reachable — putting something away must not make it unfindable, or nobody would ever do it.
         */
        @Param("includeArchived") boolean includeArchived,
        @Param("text") String text,
        Pageable pageable
    );

    /**
     * The issues that sit on one end of a link — the gathering end by default, the gathered end when
     * {@code inward} (TSSR-45).
     *
     * <p>⚠️ <strong>"Register" is a shape, not a kind of issue.</strong> There is no hub entity and no
     * column saying an issue is one — TSSR-43 turned that option down deliberately — so this asks the only
     * question the schema can answer: does anything hang off this issue. An installation that never links
     * anything gets an empty page rather than a screen explaining a concept it does not use.
     *
     * <p>⚠️ <strong>Optionally one link type, and never a link type <em>name</em>.</strong> The caller
     * passes an identifier or nothing; which type means "gathers an effort" is the interface's default and
     * the reader's choice, not a fact this query knows. TSSR-40 is the receipt for why: {@code is blocked}
     * compared a name against the literal {@code "Blocks"} and stopped being true when somebody renamed
     * the row.
     *
     * <p>⚠️ <strong>One end at a time, and which end is the caller's question.</strong> A link is stored
     * once as {@code source → target} and reads as two different statements — <em>this issue gathers those</em>
     * and <em>this issue is gathered by those</em>. Mixing them produced a list where an issue appeared twice
     * for one link, once at each end, which is the same fact pretending to be two. So {@code inward} picks
     * the side rather than the answer holding both.
     */
    @Query("""
        select issue from Issue issue
        where issue.projectId in :projectIds
          and issue.archivedAt is null
          and exists (
            select link.id from IssueLink link
            where ((:inward = false and link.sourceIssueId = issue.id)
                or (:inward = true and link.targetIssueId = issue.id))
              and (:linkTypeId is null or link.linkTypeId = :linkTypeId)
          )
        """)
    Page<Issue> findRegisters(
        @Param("projectIds") Collection<String> projectIds,
        @Param("linkTypeId") String linkTypeId,
        /** Which end this issue is: {@code false} the one that gathers, {@code true} the one gathered. */
        @Param("inward") boolean inward,
        Pageable pageable
    );

    Optional<Issue> findByIssueKey(String issueKey);

    /**
     * Many keys at once — what a document's worth of `TES-42` mentions resolves through.
     *
     * <p>⚠️ The caller normalises to uppercase before calling. Keys are stored that way, and MySQL would
     * match either case while PostgreSQL would not — a difference that would make the same document
     * render differently on the two databases.
     */
    List<Issue> findByIssueKeyIn(List<String> issueKeys);

    /** The permanent identifier a stored reference carries — see {@code Issue.hash}. */
    Optional<Issue> findByHash(String hash);

    /**
     * Many of them at once, for the same reason {@link #findByIssueKeyIn} exists.
     *
     * <p>⚠️ A document mixes the two forms freely — a key somebody typed beside a permanent id a picker
     * inserted — so resolving one batch means asking both finders and merging, never choosing.
     */
    List<Issue> findByHashIn(List<String> hashes);

    /** Whether a drawn hash is already taken; the probe that keeps minting from colliding. */
    boolean existsByHash(String hash);

    List<Issue> findByParentIdOrderByRankAsc(String parentId);

    boolean existsByParentId(String parentId);

    /** The current maximum rank in a project, so a newly-created issue can be appended after it. */
    Optional<Issue> findFirstByProjectIdOrderByRankDesc(String projectId);

    /**
     * How long the project's longest rank is — the one measurement a rebalance is decided on
     * (ADR-0006). Null for a project with no issues yet.
     */
    @Query("select max(length(issue.rank)) from Issue issue where issue.projectId = :projectId")
    Integer findLongestRankLength(@Param("projectId") String projectId);

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

    /**
     * Issues of a type in a given set of projects — what removing a type from a scheme reports.
     *
     * <p>⚠️ Scoped to the projects <em>on the scheme</em> rather than counted installation-wide. The
     * whole point of the number is "how much work here would stop being creatable", and a Bug in a
     * project on a different scheme is not affected by this edit at all.
     */
    long countByIssueTypeIdAndProjectIdIn(String issueTypeId, Collection<String> projectIds);

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

    // ── The dashboard's aggregates (TSSR-0087) ──────────────────────────────────
    //
    // ⚠️ Every one of these takes the caller's browsable project ids and is meaningless without them.
    // An aggregate is the shape in which a tracker leaks: a total says how much exists without naming
    // any of it, which is exactly the thing project isolation is for.

    /**
     * When each issue in these projects was raised, since a moment, and what it was estimated at.
     *
     * <p>⚠️ <strong>Timestamps rather than a grouped count, on purpose.</strong> Bucketing by day in
     * SQL means a date function, and MySQL and PostgreSQL do not spell those the same; this codebase
     * targets both. A week of one installation's issues is a short list, and Java can put a
     * {@code LocalDateTime} in the right bucket in a way that is the same on both dialects and can be
     * read without knowing which one is underneath.
     *
     * <p>⚠️ <strong>One query behind two charts.</strong> The flow chart counts these rows and the
     * backlog-weight chart sums their estimates. See {@link IssueMoment} for why the estimate stays
     * nullable all the way to the caller.
     */
    @Query("select new net.innoventa.tessera.repository.IssueMoment(issue.createdAt, issue.storyPoints) "
           + "from Issue issue "
           + "where issue.projectId in :projectIds and issue.createdAt >= :from")
    List<IssueMoment> createdAtSince(
        @Param("projectIds") List<String> projectIds, @Param("from") LocalDateTime from);

    long countByProjectIdInAndResolvedAtGreaterThanEqual(List<String> projectIds, LocalDateTime from);

    /**
     * Each project's live issues, split into the three status categories — the progress meter, in one
     * query rather than one per project.
     *
     * <p>An entity join on the identifier, like {@link #countParentChildTypePairs}: ids are stored flat
     * throughout this schema, so the status is joined as a row rather than navigated as an association.
     *
     * <p>⚠️ Archived issues are excluded — see {@code ProjectProgress} for why counting them would make
     * every meter read as almost done for ever.
     */
    @Query("select new net.innoventa.tessera.repository.ProjectCategoryCount("
           + "  issue.projectId, status.category, count(issue)) "
           + "from Issue issue join Status status on status.id = issue.statusId "
           + "where issue.projectId in :projectIds and issue.archivedAt is null "
           + "group by issue.projectId, status.category")
    List<ProjectCategoryCount> countByProjectAndCategory(@Param("projectIds") List<String> projectIds);

    /**
     * When each issue in these projects was resolved, since a moment, and what it was estimated at.
     *
     * <p>⚠️ <strong>One query for two charts.</strong> The flow chart counts these rows — it is the
     * other half of {@link #createdAtSince} — and the backlog-weight chart sums their estimates. Asking
     * twice for the same population would be a second round trip that could return a different answer,
     * since somebody may resolve something between the two.
     *
     * <p>⚠️ The estimate is nullable and stays that way to the caller: an issue nobody estimated is not
     * an issue estimated at nothing, and {@link IssueMoment} explains what turns on the difference.
     */
    @Query("select new net.innoventa.tessera.repository.IssueMoment(issue.resolvedAt, issue.storyPoints) "
           + "from Issue issue "
           + "where issue.projectId in :projectIds and issue.resolvedAt >= :from")
    List<IssueMoment> resolvedAtSince(
        @Param("projectIds") List<String> projectIds, @Param("from") LocalDateTime from);

    /**
     * Every open issue in these projects — the population the ageing and blocked charts are drawn from.
     *
     * <p>⚠️ <strong>"Open" is no resolution, which is the canonical invariant</strong> (ADR-0004) rather
     * than a status name or a category: a team that finishes work in a status called anything at all
     * still resolves it. The archived filter beside it is belt and braces the same way {@code
     * IssueBlockers} explains — only a closed issue may be filed — but here it is cheap and it makes the
     * intent readable without the reader having to know that rule.
     */
    List<Issue> findByProjectIdInAndResolutionIdIsNullAndArchivedAtIsNull(List<String> projectIds);

}

