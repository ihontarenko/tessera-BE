package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A time-boxed commitment: the work a team signed up for, in order, between a start and an end
 * (ADR-0012). A sprint belongs to a <strong>project</strong>, not to a board — which keeps ADR-0009's
 * one-board-per-project rule intact and leaves cross-project sprint reporting open.
 * <p>
 * All three dates are nullable because a {@code FUTURE} sprint has none: it is a named bucket for
 * planning. {@code endDate} arrives in the start request (the burndown has no axis without it),
 * {@code startedAt} is stamped server-side at that moment, and {@code completedAt} at close. Which
 * issues are in it lives in {@link SprintIssue}, never as a pointer on {@link Issue} — one source of
 * truth for membership.
 * <p>
 * Ids are stored flat, no JPA relations, matching the sibling entities.
 */
@Entity
@Table(name = "sprints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Sprint {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    /** What the sprint is for, in the team's own words; optional. */
    @Column(length = 1000)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SprintState state;

    /** Stamped server-side when the sprint starts — never taken from the client. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** The day the sprint is planned to end; required to start, and the burndown's x-axis. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** When the sprint was actually closed, which may be before {@link #endDate}. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
