package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An individual, project-scoped grant or denial — {@code (project, member, permission, effect)} —
 * layered over the member's role permissions (CONTEXT.md, "ProjectPermissionOverride"). Effective
 * permissions = role permissions ∪ ALLOW overrides − DENY overrides, <strong>deny wins</strong>. At
 * most one override per {@code (project, member, permission)} (enforced by the unique constraint), so
 * an override cleanly replaces rather than stacks. The table lands with the projects migration.
 */
@Entity
@Table(
    name = "project_permission_overrides",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_permission_override",
        columnNames = {"project_id", "member_id", "permission_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProjectPermissionOverride {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "permission_id", nullable = false, length = 36)
    private String permissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PermissionEffect effect;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
