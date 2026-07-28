package net.innoventa.tessera.service.report;

import java.time.LocalDateTime;

/**
 * One membership row as the projection reads it — a {@code SprintIssue} joined to its issue's resolution
 * timestamp, and nothing else. Every awkward reporting case is a shape of this record rather than a
 * scenario: added mid-sprint is a late {@code addedAt}, de-scoped is a {@code removedAt} inside the
 * window, unestimated is a null {@code storyPointsAtAdd}, and finished before the sprint even began is a
 * {@code resolvedAt} earlier than the start.
 *
 * @param issueId          identifies the issue; the projection never looks it up, it only passes it back
 *                         through the buckets for the service to hydrate
 * @param addedAt          when this membership began — reset when a removed issue is re-added, so a row
 *                         describes the issue's latest stint in this sprint and not its full churn
 * @param removedAt        when it ended, or null while the issue is still a member. Closing a sprint
 *                         deliberately leaves this null, since the row is the report rather than a state
 * @param storyPointsAtAdd the estimate frozen at {@code addedAt}. A later re-estimate does not move the
 *                         line (ADR-0013); null means unestimated and is worth zero
 * @param resolvedAt       when the issue was resolved, ever — {@code resolution IS NULL} is the only
 *                         definition of open (ADR-0004), and {@code resolvedAt} (ADR-0011) is when it
 *                         stopped being so
 */
public record SprintMemberFact(
    String issueId,
    LocalDateTime addedAt,
    LocalDateTime removedAt,
    Double storyPointsAtAdd,
    LocalDateTime resolvedAt
) {

    /** The frozen estimate as the report values it: a missing one is zero points, never a missing issue. */
    public double points() {
        return storyPointsAtAdd == null ? 0.0 : storyPointsAtAdd;
    }

}
