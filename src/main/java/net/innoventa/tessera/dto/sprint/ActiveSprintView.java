package net.innoventa.tessera.dto.sprint;

import net.innoventa.tessera.domain.Sprint;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The running sprint's context, as the board header renders it — so the header needs no second request.
 * <p>
 * {@code daysRemaining} is computed server-side against the caller's request date and is
 * <strong>signed</strong>: negative means the sprint is past its end date, which the header shows as
 * overdue rather than hiding. It is null when the sprint somehow carries no end date.
 */
public record ActiveSprintView(
    String id,
    String name,
    String goal,
    LocalDate endDate,
    Long daysRemaining
) {

    public static ActiveSprintView from(Sprint sprint, LocalDate today) {
        return new ActiveSprintView(
            sprint.getId(),
            sprint.getName(),
            sprint.getGoal(),
            sprint.getEndDate(),
            sprint.getEndDate() == null ? null : ChronoUnit.DAYS.between(today, sprint.getEndDate())
        );
    }

}
