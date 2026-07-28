package net.innoventa.tessera.dto.backlog;

import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.PrioritySummary;

/**
 * One row of the backlog screen — everything needed to judge and order an issue without opening it
 * (spec, story 7). Every row is a planning unit (ADR-0014), so sub-tasks and epics never appear here.
 * <p>
 * {@code rank} rides along because the client ranks a drop between the two neighbours it can see, and
 * {@code open} because a sprint panel keeps showing an issue that has been completed inside it.
 */
public record BacklogIssueView(
    String id,
    String issueKey,
    String summary,
    IssueTypeSummary type,
    PrioritySummary priority,
    MemberSummary assignee,
    Double storyPoints,
    boolean open,
    String rank
) {
}
