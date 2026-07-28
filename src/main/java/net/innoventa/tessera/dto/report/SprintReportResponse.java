package net.innoventa.tessera.dto.report;

import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.service.report.BurndownPoint;

import java.util.List;

/**
 * A sprint's report and its burndown in <strong>one</strong> payload, because they are one load: both
 * are derived from the same membership rows in the same pass (ADR-0013), and splitting them into two
 * endpoints would mean reading the sprint twice to draw one screen.
 * <p>
 * The burndown points are the projection's own value record rather than a re-declared copy of it — a
 * point is a date and three numbers, and a second definition of that would only be a second thing to
 * keep in step.
 *
 * @param completed  finished inside the sprint, whether committed at the start or pulled in later
 * @param incomplete still committed at the close and not finished — the work the completion dialog moved
 * @param removed    pulled out of the sprint mid-flight, which is de-scoping rather than failure and is
 *                   reported separately so the two are never confused
 */
public record SprintReportResponse(
    SprintSummary sprint,
    int committedIssues,
    double committedPoints,
    int completedIssues,
    double completedPoints,
    List<SprintReportIssueView> completed,
    List<SprintReportIssueView> incomplete,
    List<SprintReportIssueView> removed,
    List<BurndownPoint> burndown
) {
}
