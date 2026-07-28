package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A named board filter a member kept (ADR-0008).
 * <p>
 * {@code expression} holds the <strong>predicate over one issue</strong> — {@code issue.assignee ==
 * currentMember} — and never the collection pipeline that evaluates it. That is what keeps two saved
 * filters composable as {@code (a) and (b)}, and what lets the same string later be handed to
 * {@code jmouse-jdbc} as a {@code WHERE} clause instead of having to be re-parsed out of a program.
 * <p>
 * Ids are stored flat, no JPA relations, matching the sibling entities.
 */
@Entity
@Table(
    name = "saved_filters",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_saved_filters_owner_name",
        columnNames = {"project_id", "owner_member_id", "name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SavedFilter {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** Null only for a {@link SavedFilterVisibility#GLOBAL} preset, which belongs to every project. */
    @Column(name = "project_id", length = 36)
    private String projectId;

    /**
     * The member who saved it — the only one who may change or delete it, whatever its visibility.
     * Null only for a {@link SavedFilterVisibility#GLOBAL} preset, which nobody owns and therefore
     * nobody may edit.
     */
    @Column(name = "owner_member_id", length = 36)
    private String ownerMemberId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 500)
    private String description;

    /** The jME predicate. Validated on write, so a stored filter always parses and returns a boolean. */
    @Column(nullable = false, length = 1024)
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SavedFilterVisibility visibility;

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
