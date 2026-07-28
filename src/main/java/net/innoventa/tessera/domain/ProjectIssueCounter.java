package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The per-project running number allocator (ADR-0003). One row per project holding {@code nextValue};
 * issue creation reads-and-increments it under a {@code PESSIMISTIC_WRITE} lock in the same
 * transaction that saves the {@link Issue}, giving portable, collision-free key allocation across all
 * three SQL dialects without {@code RETURNING}. A separate table (not a column on {@code projects}) so
 * the lock serialises only issue creation within a project, never edits to the project aggregate.
 */
@Entity
@Table(name = "project_issue_counter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "projectId")
public class ProjectIssueCounter {

    @Id
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "next_value", nullable = false)
    private int nextValue;

}
