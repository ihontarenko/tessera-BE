package net.innoventa.tessera.dto.sprint;

import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintState;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A sprint as every screen renders it. All three dates are nullable by design: a {@code FUTURE} sprint
 * is a named bucket with none of them, {@code endDate} and {@code startedAt} arrive together when it is
 * started, and {@code completedAt} at close.
 */
public record SprintSummary(
    String id,
    String projectId,
    String name,
    String goal,
    SprintState state,
    LocalDateTime startedAt,
    LocalDate endDate,
    LocalDateTime completedAt
) {

    public static SprintSummary from(Sprint sprint) {
        return new SprintSummary(
            sprint.getId(),
            sprint.getProjectId(),
            sprint.getName(),
            sprint.getGoal(),
            sprint.getState(),
            sprint.getStartedAt(),
            sprint.getEndDate(),
            sprint.getCompletedAt()
        );
    }

}
