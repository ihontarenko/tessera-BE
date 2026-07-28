package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One {@link IssueType} → {@link Workflow} mapping inside a {@link WorkflowScheme}. Types with no
 * explicit item here use the scheme's default workflow. Flat join entity with its own id.
 */
@Entity
@Table(
    name = "workflow_scheme_items",
    uniqueConstraints = @UniqueConstraint(name = "uq_workflow_scheme_item", columnNames = {"scheme_id", "issue_type_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class WorkflowSchemeItem {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "scheme_id", nullable = false, length = 36)
    private String schemeId;

    @Column(name = "issue_type_id", nullable = false, length = 36)
    private String issueTypeId;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

}
