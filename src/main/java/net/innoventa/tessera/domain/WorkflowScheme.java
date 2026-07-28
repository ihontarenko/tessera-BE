package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named mapping of {@link IssueType} → {@link Workflow} plus a default workflow (CONTEXT.md,
 * "Scheme"; ADR-0001). A {@code Project} points at one. Per-type overrides live in
 * {@link WorkflowSchemeItem}; issue types not explicitly mapped fall back to {@link #defaultWorkflowId}.
 * One of exactly two scheme entities (the other is {@link IssueTypeScheme}).
 */
@Entity
@Table(name = "workflow_schemes", uniqueConstraints = @UniqueConstraint(name = "uq_workflow_schemes_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class WorkflowScheme {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "default_workflow_id", nullable = false, length = 36)
    private String defaultWorkflowId;

    @Column(length = 255)
    private String description;

}
