package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.SwimlaneStrategy;
import net.innoventa.tessera.dto.sprint.ActiveSprintView;

import java.util.List;

/**
 * The board-render payload (Phase-2 ticket 01): the board configuration — its ordered {@code columns}
 * (with WIP limits and category fallbacks), the board-wide {@code swimlaneStrategy} and
 * {@code hideDoneOlderThanDays} — plus a <strong>flat</strong> list of {@code cards}, each already
 * resolved to its {@code columnId}. The server returns a slice, not a grouping: the client does
 * swimlane grouping and stub quick-filtering over this flat list (ADR-0009/0010).
 * <p>
 * {@code scopeStrategy} says where that slice came from (ADR-0012) and {@code activeSprint} carries the
 * running sprint's context — name, goal, end date, days remaining — so the board header needs no second
 * request. Under {@code ALL_ISSUES} the sprint is always absent and the payload is exactly what it was
 * before sprints existed; under {@code ACTIVE_SPRINT} with nothing running, {@code cards} is empty and
 * the client shows a start-a-sprint empty state rather than an empty grid.
 */
public record BoardResponse(
    String boardId,
    String projectId,
    String name,
    SwimlaneStrategy swimlaneStrategy,
    BoardScopeStrategy scopeStrategy,
    Integer hideDoneOlderThanDays,
    ActiveSprintView activeSprint,
    List<BoardColumnView> columns,
    List<BoardCardView> cards,

    /**
     * Which cards satisfied the request's {@code filter} predicate (ADR-0008), or {@code null} when
     * the request carried none.
     * <p>
     * A filter narrows <em>what one viewer is looking at</em>, not what the board holds — so the
     * filtered cards are still sent and marked, rather than dropped. That is what keeps WIP counts
     * describing the stage instead of the viewer: a column at its limit does not stop being at its
     * limit because someone filtered down to their own issues. The predicate is still evaluated
     * server-side, which is the point of ADR-0008; only the discarding is left to the client, which
     * is the only place that knows whose view it is.
     */
    List<String> matchedCardIds
) {
}
