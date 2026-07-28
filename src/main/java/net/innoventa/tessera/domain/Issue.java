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

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(length = 4000)
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
