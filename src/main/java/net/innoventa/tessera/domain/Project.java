package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The top-level container of work (ADR-0002 — nothing above it; no workspace/organization column).
 * Owns a short uppercase {@code key} (e.g. TIC) and a {@code lead}, and points at one
 * {@link IssueTypeScheme} and one {@link WorkflowScheme}. {@code keyStrategy}/{@code keyPattern} select
 * the per-project issue-key algorithm (ADR-0003); Phase 1 always seeds the default prefixed-sequence
 * strategy. Visibility is membership-based ({@link ProjectMembership}), not tenant-based.
 * <p>
 * There is deliberately no {@code type} column. Whether a project plans in sprints is
 * {@link Board#getScopeStrategy()} and nothing else, so no second field can contradict it (ADR-0015).
 */
@Entity
@Table(name = "projects", uniqueConstraints = @UniqueConstraint(name = "uq_projects_key", columnNames = "project_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Project {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** The short uppercase key, e.g. {@code TIC}. Column is {@code project_key} — "key" is reserved. */
    @Column(name = "project_key", nullable = false, length = 32)
    private String key;

    @Column(nullable = false, length = 128)
    private String name;

    /**
     * One emoji standing for this project, or null (TSSR-7).
     *
     * <p>⚠️ <strong>Sixteen characters to hold one glyph.</strong> An emoji is a grapheme cluster, not a
     * character: a flag is two code points, a family joined by zero-width joiners is seven. "Exactly one"
     * is enforced by {@link net.innoventa.tessera.service.ProjectIcon}, which counts clusters; the column
     * only has to be wide enough for the longest of them.
     *
     * <p>Null is the ordinary state — every screen falls back to the shared folder glyph — so nothing has
     * to be backfilled and no project is invalid without one.
     */
    @Column(length = 16)
    private String icon;

    @Column(name = "lead_member_id", nullable = false, length = 36)
    private String leadMemberId;

    @Column(name = "issue_type_scheme_id", nullable = false, length = 36)
    private String issueTypeSchemeId;

    @Column(name = "workflow_scheme_id", nullable = false, length = 36)
    private String workflowSchemeId;

    /**
     * How this project estimates, or null.
     *
     * ⚠️ <strong>Null is "this project does not estimate", not a scheme called None.</strong> The
     * story-points control disappears entirely rather than offering an empty select, and an unestimated
     * issue stays valid under every scheme — it is the empty selection, not an option (ADR-0019).
     */
    @Column(name = "estimation_scheme_id", length = 36)
    private String estimationSchemeId;

    /** Discriminator naming which {@code IssueKeyStrategy} bean this project uses (ADR-0003). */
    @Column(name = "key_strategy", nullable = false, length = 64)
    private String keyStrategy;

    /** Optional template the strategy interprets, e.g. {@code {key}-{sequence}}. */
    @Column(name = "key_pattern", length = 128)
    private String keyPattern;

    /**
     * Which WiQ section this project's wiki lives in, or null where nobody has chosen one yet
     * (WIQ-10; WIQ-1 §3).
     *
     * <p>⚠️ <strong>An identifier in ANOTHER SERVICE'S DATABASE.</strong> There is no foreign key and
     * there cannot be one: WiQ owns the tree, its categories are bare, and this is a consumer naming
     * its own root. Which also means the section can be deleted or moved out from under this row
     * without anything here noticing — so a root that no longer resolves is a STATE the screen handles,
     * not an error.
     *
     * <p>⚠️ <strong>Null is ordinary.</strong> Before one is chosen the wiki tab says "pick a category"
     * to an administrator and "the wiki is not configured" to everybody else — two empty states,
     * because those are two different situations and one of them is somebody's job.
     */
    @Column(name = "wiq_root_category_id", length = 36)
    private String wiqRootCategoryId;

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
