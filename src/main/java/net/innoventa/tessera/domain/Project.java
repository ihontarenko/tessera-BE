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

    @Column(name = "lead_member_id", nullable = false, length = 36)
    private String leadMemberId;

    @Column(name = "issue_type_scheme_id", nullable = false, length = 36)
    private String issueTypeSchemeId;

    @Column(name = "workflow_scheme_id", nullable = false, length = 36)
    private String workflowSchemeId;

    /** Discriminator naming which {@code IssueKeyStrategy} bean this project uses (ADR-0003). */
    @Column(name = "key_strategy", nullable = false, length = 64)
    private String keyStrategy;

    /** Optional template the strategy interprets, e.g. {@code {key}-{sequence}}. */
    @Column(name = "key_pattern", length = 128)
    private String keyPattern;

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
