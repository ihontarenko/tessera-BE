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
    List<BoardCardView> cards
) {
}
