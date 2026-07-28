package net.innoventa.tessera.service.report;

import java.util.List;

/**
 * Everything derivable about one sprint, from one pass over its membership rows — the burndown series,
 * the report's three buckets, and the headline totals. The buckets are <strong>disjoint and
 * exhaustive</strong> over the rows that were members during the window, so no issue is counted twice
 * and none quietly disappears.
 *
 * @param burndown        one point per day of the sprint's planned window
 * @param completed       still a member at the close, and resolved by then
 * @param incomplete      still a member at the close, and not resolved by then
 * @param removed         pulled out of the sprint between its start and its close
 * @param committedIssues how many issues were members when the sprint started
 * @param committedPoints their frozen estimates, unestimated counted as zero
 * @param completedIssues the size of {@link #completed} — which <em>includes</em> work pulled in
 *                        mid-sprint and finished, so committed-versus-completed compares a plan against
 *                        an outcome rather than a plan against itself
 * @param completedPoints the frozen estimates of {@link #completed}
 */
public record SprintProjection(
    List<BurndownPoint> burndown,
    List<SprintMemberFact> completed,
    List<SprintMemberFact> incomplete,
    List<SprintMemberFact> removed,
    int committedIssues,
    double committedPoints,
    int completedIssues,
    double completedPoints
) {
}
