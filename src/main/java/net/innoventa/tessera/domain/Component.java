package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named area of a {@link Project} — Backend, UI, Infrastructure — with an optional {@code lead}
 * member, so issues can be grouped by part of the system (ticket 06). Project-scoped and unique by
 * name within its project; managed by a project administrator. Ids are stored flat (no JPA relations),
 * matching the sibling entities' style.
 */
@Entity
@Table(
    name = "components",
    uniqueConstraints = @UniqueConstraint(name = "uq_components_project_name", columnNames = {"project_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Component {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "lead_member_id", length = 36)
    private String leadMemberId;

    @Column(length = 255)
    private String description;

}
