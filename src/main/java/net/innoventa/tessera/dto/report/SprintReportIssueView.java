package net.innoventa.tessera.dto.report;

import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.StatusSummary;

/**
 * One line of a sprint report. {@code status} rides along because the interesting question about an
 * unfinished issue is how far it got, and {@code assignee} because the retrospective's next question is
 * always who was carrying it.
 * <p>
 * {@code storyPoints} is the estimate <strong>frozen when the issue joined the sprint</strong>, not the
 * issue's estimate today — the report says what was committed to, and a later re-estimate does not
 * rewrite that (ADR-0013).
 */
public record SprintReportIssueView(
    String id,
    String issueKey,
    String summary,
    IssueTypeSummary type,
    StatusSummary status,
    MemberSummary assignee,
    Double storyPoints
) {
}
