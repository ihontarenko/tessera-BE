package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named set of statuses plus the {@link Transition}s allowed between them (a state machine). Global
 * catalog entity; a {@code WorkflowScheme} maps issue types onto workflows. Enforcement is the
 * data-driven {@code TransitionService} reading this workflow's transition rows — not Spring
 * Statemachine (ADR-0005).
 */
@Entity
@Table(name = "workflows", uniqueConstraints = @UniqueConstraint(name = "uq_workflows_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Workflow {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

}
