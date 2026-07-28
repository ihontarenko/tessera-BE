package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.domain.SwimlaneStrategy;

import java.util.List;

/**
 * The board-render payload (Phase-2 ticket 01): the board configuration — its ordered {@code columns}
 * (with WIP limits and category fallbacks), the board-wide {@code swimlaneStrategy} and
 * {@code hideDoneOlderThanDays} — plus a <strong>flat</strong> list of {@code cards}, each already
 * resolved to its {@code columnId}. The server returns a slice, not a grouping: the client does
 * swimlane grouping and stub quick-filtering over this flat list (ADR-0009/0010).
 */
public record BoardResponse(
    String boardId,
    String projectId,
    String name,
    SwimlaneStrategy swimlaneStrategy,
    Integer hideDoneOlderThanDays,
    List<BoardColumnView> columns,
    List<BoardCardView> cards
) {
}
