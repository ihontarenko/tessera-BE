package net.innoventa.tessera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The one row that answers "what does a new project start with".
 *
 * <p>⚠️ <strong>This used to be two string constants in {@code ProjectService}.</strong> Naming a seeded
 * scheme in Java was harmless while schemes were read-only; it stopped being harmless the moment a
 * screen could delete one. A constant pointing at a row somebody may remove is a way to break project
 * creation from the settings page, and the break arrives at whoever next creates a project rather than
 * at the click that caused it.
 *
 * <p>⚠️ <strong>Exactly one row, and the database says so</strong> — {@code CHECK (id = 'instance')},
 * not a convention this class hopes readers follow. Without it a second row is insertable and every
 * reader has to decide which one it meant.
 *
 * <p>Both columns are foreign keys, which is what makes "you cannot delete the instance default" true
 * rather than merely enforced-in-a-service. The service still refuses first, with a sentence saying
 * <em>which</em> default it is; the constraint is there for everything that is not the service.
 */
@Entity
@Table(name = "instance_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class InstanceSettings {

    /** The only identifier the table accepts — see the CHECK constraint in V000015. */
    public static final String ID = "instance";

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** The issue-type scheme a project is created on, until somebody changes the project. */
    @Column(name = "default_issue_type_scheme_id", nullable = false, length = 36)
    private String defaultIssueTypeSchemeId;

    /** The workflow scheme a project is created on, likewise. */
    @Column(name = "default_workflow_scheme_id", nullable = false, length = 36)
    private String defaultWorkflowSchemeId;

    /**
     * How a new project estimates, or null.
     *
     * ⚠️ <strong>Nullable, unlike the other two</strong>, and seeded null. An installation whose default
     * is "does not estimate" is coherent — nothing here knows how a team works — whereas a project with
     * no issue-type scheme is a project nothing can be raised in.
     */
    @Column(name = "default_estimation_scheme_id", length = 36)
    private String defaultEstimationSchemeId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
