package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The join assigning a {@link Component} to an {@link Issue} (ticket 11). An issue may hold several;
 * unique per {@code (issue, component)}. The component must belong to the issue's project (enforced in
 * the service).
 */
@Entity
@Table(
    name = "issue_component",
    uniqueConstraints = @UniqueConstraint(name = "uq_issue_component", columnNames = {"issue_id", "component_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueComponent {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "component_id", nullable = false, length = 36)
    private String componentId;

}
