package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Flat join entity mapping a {@link ProjectRole} to a {@link Permission} — the (globally shared) set
 * of capabilities a role carries. Own id, matching the codebase's join style.
 */
@Entity
@Table(
    name = "project_role_permissions",
    uniqueConstraints = @UniqueConstraint(name = "uq_project_role_permission", columnNames = {"role_id", "permission_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProjectRolePermission {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    @Column(name = "permission_id", nullable = false, length = 36)
    private String permissionId;

}
