package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The data-driven mapping of a {@link ProjectType} to the schemes a new project of that type is
 * seeded with — the reason project provisioning needs no {@code if (type == TODO)} branch. One row per
 * type: SCRUM/KANBAN point at the default full schemes, TODO at the minimal single-type scheme + short
 * workflow preset (CONTEXT.md, "Project type"; ADR-0001). Adding a project type is a new row here, not
 * a code change.
 */
@Entity
@Table(name = "project_type_default_schemes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "type")
public class ProjectTypeDefaultScheme {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 16)
    private ProjectType type;

    @Column(name = "issue_type_scheme_id", nullable = false, length = 36)
    private String issueTypeSchemeId;

    @Column(name = "workflow_scheme_id", nullable = false, length = 36)
    private String workflowSchemeId;

    /**
     * The scope strategy a new project's board is provisioned with (ADR-0012) — {@code SCRUM →
     * ACTIVE_SPRINT}, everything else {@code ALL_ISSUES}. Data, so {@link net.innoventa.tessera.service.BoardProvisioner}
     * needs no {@code if (type == SCRUM)} branch either.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "board_scope_strategy", nullable = false, length = 16)
    private BoardScopeStrategy boardScopeStrategy;

}
