package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Grants a {@link Member} a {@link ProjectRole} in a {@link net.innoventa.tessera.domain.Project} —
 * the triple {@code (project, member, role)}. A member may hold several roles in one project (several
 * rows), and their permissions combine additively (CONTEXT.md, "ProjectMembership"). This is also the
 * visibility gate: a member sees a project because they have a membership in it — isolation is
 * membership-based, not tenant-based (ADR-0002). The table lands with the projects migration.
 */
@Entity
@Table(
    name = "project_memberships",
    uniqueConstraints = @UniqueConstraint(name = "uq_project_membership", columnNames = {"project_id", "member_id", "role_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProjectMembership {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
