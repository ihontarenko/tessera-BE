package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named, reusable role (Administrator, Developer, Viewer) whose meaning is a set of
 * {@link Permission}s ({@link ProjectRolePermission}). The role → permission mapping is
 * <strong>global</strong> — a "Developer" grants the same permissions in every project; there is no
 * per-project PermissionScheme in Phase 1 (CONTEXT.md, "ProjectRole"; ADR-0001).
 */
@Entity
@Table(name = "project_roles", uniqueConstraints = @UniqueConstraint(name = "uq_project_roles_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProjectRole {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

}
