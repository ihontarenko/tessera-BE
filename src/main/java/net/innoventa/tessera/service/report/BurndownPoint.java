package net.innoventa.tessera.service.report;

import java.time.LocalDate;

/**
 * One day of the burndown, measured at the end of that day.
 *
 * @param date      the calendar day. Weekends are not special-cased — a team that does not work Sunday
 *                 sees a flat Sunday, which is the truth rather than a gap
 * @param remaining unfinished work still committed at the end of the day, or <strong>null</strong> for a
 *                 day the sprint never reached — after an early close, or still in the future for a
 *                 running sprint. Null draws no point, where a repeated last value would read as a team
 *                 that stalled
 * @param ideal     the straight reference line from the committed total to zero at the end date. It is
 *                 drawn against what was committed at the start, so scope growth does not quietly move
 *                 the goalposts along with it
 * @param scope     everything committed on that day, finished or not — the line that makes a sprint that
 *                 grew visibly different from a team that slowed down
 */
public record BurndownPoint(LocalDate date, Double remaining, double ideal, double scope) {
}
