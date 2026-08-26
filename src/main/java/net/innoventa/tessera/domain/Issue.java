package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The unit of work. Carries a fixed field set (Phase 1 — no custom fields): a per-project
 * {@code sequence} and the denormalised {@code issueKey} it is referenced by (both stored, ADR-0003),
 * a type/priority/status, an optional {@code resolution}, a reporter, an optional assignee, an
 * optional {@code parent} (the single unified hierarchy link, ticket 10), optional story points, and
 * a global {@code rank} ordering string (ADR-0006, mapped to column {@code lexo_rank} — MySQL reserves
 * {@code RANK}).
 * <p>
 * The canonical open/closed invariant lives here: an issue is <strong>open ⇔ {@code resolutionId} is
 * null</strong>, closed ⇔ it is set (ADR-0004) — never judged by status name. Ids are stored flat, no
 * JPA relations, matching the sibling entities.
 */
@Entity
@Table(
    name = "issues",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_issues_key", columnNames = "issue_key"),
        @UniqueConstraint(name = "uq_issues_hash", columnNames = "hash"),
        @UniqueConstraint(name = "uq_issues_project_sequence", columnNames = {"project_id", "sequence"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Issue {

    /**
     * The longest description this tracker accepts, in characters.
     *
     * <p>MySQL's {@code TEXT} holds 65535 <strong>bytes</strong> and a utf8mb4 character can take four
     * of them, so 16000 characters cannot overflow the column whatever alphabet they are written in
     * (16000 × 4 = 64000). The number is derived, not chosen — do not round it up without changing the
     * column type first.
     */
    public static final int MAXIMUM_DESCRIPTION_LENGTH = 16_000;

    /**
     * How many characters {@link #hash} is drawn with.
     *
     * <p>Six hex characters is about 16.7 million, which is a collision every few thousand issues rather
     * than every few — and minting probes the table anyway, so the number bounds how often it has to
     * draw twice, not whether a collision can happen. The column is {@code length 16} so this can grow
     * without a migration.
     */
    public static final int HASH_LENGTH = 6;

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** The raw per-project number; a rolled-back create may burn one — gaps are tolerated (ADR-0003). */
    @Column(name = "sequence", nullable = false)
    private int sequence;

    /** What everything references, e.g. {@code TIC-1}. Unique instance-wide, denormalised from strategy. */
    @Column(name = "issue_key", nullable = false, length = 64)
    private String issueKey;

    /**
     * The one identifier of this issue that nothing changes — six characters, drawn once at creation.
     *
     * <p>⚠️ <strong>The key is not this, and that is the whole reason this column exists.</strong> A key
     * is a project's key plus a counter: readable, quotable, and re-mintable. A reference stored
     * anywhere outside this database — in a wiki page, in another product's description — that carries
     * the key carries the half that can move, and breaks the day somebody re-mints one.
     *
     * <p>⚠️ <strong>Not updatable, and the annotation is the cheapest place to say so.</strong> A
     * {@code setHash} anything could reach is every stored reference eventually resolving to nothing,
     * discovered one page at a time months later.
     *
     * <p>⚠️ <strong>Resolved second, never first.</strong> Six hex characters is also a perfectly
     * ordinary issue key to a matcher that only looks at shape, so anything accepting either asks the
     * key first: a key is what people write, a hash is what a link carries.
     */
    @Column(name = "hash", nullable = false, length = 16, updatable = false)
    private String hash;

    @Column(nullable = false, length = 255)
    private String summary;

    /**
     * ⚠️ {@code TEXT}, not a bounded {@code VARCHAR} — a description holds prose, and the first thing
     * anybody put in one was a spec (TSSR-1).
     *
     * <p>The ceiling is {@link #MAXIMUM_DESCRIPTION_LENGTH}, enforced by the request DTOs rather than by
     * the column, because MySQL's {@code TEXT} counts <em>bytes</em> and validation counts characters —
     * a column-shaped limit would accept 20000 ASCII characters and refuse 20000 Cyrillic ones.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "issue_type_id", nullable = false, length = 36)
    private String issueTypeId;

    @Column(name = "priority_id", nullable = false, length = 36)
    private String priorityId;

    @Column(name = "status_id", nullable = false, length = 36)
    private String statusId;

    /** Null ⇔ open (ADR-0004). Set/cleared only by workflow transitions, never as a free field. */
    @Column(name = "resolution_id", length = 36)
    private String resolutionId;

    /**
     * When the issue entered a DONE-category status (ADR-0011); null while open. Set/cleared in the same
     * transition path as {@code resolutionId}. Powers the board's done-threshold hiding accurately —
     * unlike {@code updatedAt}, which any later edit resets.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * When somebody put this issue away (TSSR-4); null while it is still in view.
     *
     * <p><strong>A second axis, independent of {@code resolutionId}.</strong> Resolved says the work
     * finished; archived says it has stopped being interesting to look at. An archived issue leaves the
     * board, the backlog and the project's issue list at once, and is still found by search and still
     * listed on the Shipped screen — it is put away, not deleted.
     *
     * <p>⚠️ <strong>Only a closed issue can be archived</strong> ({@code resolutionId != null}), and
     * <strong>reopening un-archives</strong> — {@link net.innoventa.tessera.service.TransitionService}
     * clears this on the way out of a DONE status, in the same breath as the resolution. Without that,
     * an issue could be open and invisible at once, which is the one state nothing in the product could
     * explain.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** Who put it away — null exactly when {@link #archivedAt} is. */
    @Column(name = "archived_by_member_id", length = 36)
    private String archivedByMemberId;

    @Column(name = "reporter_member_id", nullable = false, length = 36)
    private String reporterMemberId;

    @Column(name = "assignee_member_id", length = 36)
    private String assigneeMemberId;

    /** The single unified parent link (ticket 10); sub-task-ness derives from the type hierarchy level. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "story_points")
    private Double storyPoints;

    /** The global LexoRank ordering string (ADR-0006). Column {@code lexo_rank} — MySQL reserves RANK. */
    @Column(name = "lexo_rank", nullable = false, length = 64)
    private String rank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
