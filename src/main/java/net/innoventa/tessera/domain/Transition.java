package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A permitted move from one {@link Status} to another within a {@link Workflow} ({@code from → to}).
 * A null {@code fromStatusId} is the initial/create transition — the status a newly-created issue of
 * this workflow lands in. The unit the data-driven workflow engine enforces: a status change passes
 * only if a matching edge exists here (ADR-0005). Ids are stored flat (no JPA relations), matching
 * the sibling codebases' style.
 */
@Entity
@Table(name = "transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Transition {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    /** Null means the initial (create) transition into {@link #toStatusId}. */
    @Column(name = "from_status_id", length = 36)
    private String fromStatusId;

    @Column(name = "to_status_id", nullable = false, length = 36)
    private String toStatusId;

    @Column(nullable = false, length = 64)
    private String name;

}
