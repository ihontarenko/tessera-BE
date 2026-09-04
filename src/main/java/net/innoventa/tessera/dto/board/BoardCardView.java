package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueScheduleView;
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
    /**
     * Whether something unresolved is holding this card up (TSSR-41).
     *
     * ⚠️ A flag, not the keys — unlike {@code IssueResponse.blockedBy}. A board is the screen where
     * somebody decides what to pick up, and that decision needs "not this one", not a list to read. The
     * keys are one click away on the issue itself, and fetching them for every card on a board would be
     * a query per card for something nobody reads at this size.
     */
    boolean blocked,
    /**
     * When the issue is meant to happen, and how pressing that is today.
     *
     * ⚠️ <strong>On a card, unlike {@code blockedBy}, and for the opposite reason.</strong> A blocker is
     * a list of other issues and costs a query per card, so the card carries a flag and the keys stay one
     * click away. A schedule is three columns already loaded with the row and a verdict derived from
     * them, so it costs nothing — and the board is precisely the screen where "what is due" decides what
     * somebody drags next.
     *
     * ⚠️ Never null: an unscheduled card carries the empty schedule rather than nothing.
     */
    IssueScheduleView schedule,
    LocalDateTime resolvedAt
) {
}
