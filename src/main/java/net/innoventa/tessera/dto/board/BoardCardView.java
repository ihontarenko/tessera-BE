package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.issue.StatusSummary;

import java.time.LocalDateTime;

/**
 * One card on the board — an issue projected into what a card renders, plus the resolved
 * {@code columnId} it belongs in and the raw grouping keys the client needs for swimlanes and stub
 * quick-filters (Phase-2 tickets 04/05) without re-deriving them. {@code columnId} is resolved
 * server-side by the pure column-resolution function (explicit mapping wins, else category fallback,
 * ADR-0010), so the client never computes placement. {@code open} surfaces the
 * {@code resolution IS NULL} invariant (ADR-0004) for the "Unresolved" quick filter and styling.
 * <p>
 * {@code epicKey} is the nearest Epic ancestor's key ({@link net.innoventa.tessera.service.EpicResolver},
 * {@code null} when there is none) and {@code resolvedAt} the recorded completion time the client's
 * done-threshold measures against (ticket 06) — deliberately not {@code updatedAt}, so editing a done
 * issue does not resurrect it onto the board.
 */
public record BoardCardView(
    String id,
    String issueKey,
    String summary,
    IssueTypeSummary type,
    PrioritySummary priority,
    StatusSummary status,
    MemberSummary assignee,
    boolean open,
    String columnId,
    String rank,
    String assigneeId,
    String priorityId,
    String epicKey,
    LocalDateTime resolvedAt
) {
}
