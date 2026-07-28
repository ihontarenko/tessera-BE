package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.StatusCategory;

import java.util.List;

/**
 * One column of the board configuration (ADR-0010). Carries the soft WIP limits (enforced only
 * visually on the client), {@code fallbackForCategory} so the client knows which category each column
 * is the default home for, and {@code explicitStatusIds} — the administrator overrides mapped directly
 * onto this column (Phase-2 ticket 03) — so the board-settings UI can render and edit them without a
 * separate lookup.
 */
public record BoardColumnView(
    String id,
    String name,
    int position,
    Integer minIssues,
    Integer maxIssues,
    StatusCategory fallbackForCategory,
    List<String> explicitStatusIds
) {

    public static BoardColumnView from(BoardColumn column, List<String> explicitStatusIds) {
        return new BoardColumnView(
            column.getId(),
            column.getName(),
            column.getPosition(),
            column.getMinIssues(),
            column.getMaxIssues(),
            column.getFallbackForCategory(),
            explicitStatusIds
        );
    }

}
