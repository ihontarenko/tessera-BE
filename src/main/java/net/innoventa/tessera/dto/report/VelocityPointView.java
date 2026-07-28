package net.innoventa.tessera.dto.report;

/**
 * One closed sprint's contribution to the velocity chart (Phase-3 ticket 07): what the team signed up
 * for against what it actually delivered, side by side.
 * <p>
 * Both pairs come from the same projection the sprint report uses — there is deliberately no second
 * definition of "committed" or "completed" anywhere in the codebase, which is why a sprint's headline
 * and its bar in the velocity chart can never disagree.
 *
 * @param committedIssues how many issues were members when the sprint started
 * @param committedPoints their frozen estimates, an unestimated issue counted as zero
 * @param completedIssues how many were resolved by the close, including work pulled in mid-sprint
 * @param completedPoints their frozen estimates on the same terms
 */
public record VelocityPointView(
    String sprintId,
    String sprintName,
    int committedIssues,
    double committedPoints,
    int completedIssues,
    double completedPoints
) {
}
