package net.innoventa.tessera.dto.configuration;

import net.innoventa.tessera.domain.StatusCategory;

import java.util.List;

/**
 * What changing a status's category would do, answered before it is done.
 *
 * <p>The category is the field that carries meaning. It decides whether an issue in this status counts
 * as closed (ADR-0004) and which board column holds it by fallback (ADR-0010) — so moving it moves
 * cards and changes what work already sitting there is understood to be. That is allowed; it is
 * reported first.
 *
 * <p>⚠️ <strong>Nothing here writes to {@code issues}.</strong> A category change alters what a status
 * <em>means</em>, not what any issue holds, and back-filling resolutions to match would be inventing
 * answers on somebody else's behalf.
 *
 * @param issuesHolding      how many issues are in this status right now, across every project
 * @param cardsChangingColumn how many of those would render in a different board column afterwards
 * @param moves              per board, the column they leave and the column they arrive in — a board
 *                           whose cards do not move is left out
 * @param openIssuesEnteringDone when the new category is {@code DONE}: how many of those issues have no
 *                           resolution, and would therefore sit in the Done column while still being
 *                           open. Zero for every other target category
 */
public record StatusCategoryImpact(
    String statusId,
    String statusName,
    StatusCategory currentCategory,
    StatusCategory proposedCategory,
    long issuesHolding,
    long cardsChangingColumn,
    List<BoardMove> moves,
    long openIssuesEnteringDone
) {

    /**
     * One board's share of the change.
     *
     * @param fromColumnName where those cards render today, or null when the board maps the status
     *                       nowhere and the backlog is holding them (ADR-0016)
     * @param toColumnName   where they would render afterwards, null meaning the same
     */
    public record BoardMove(
        String projectId,
        String projectKey,
        String projectName,
        String boardId,
        String fromColumnName,
        String toColumnName,
        long issueCount
    ) {
    }
}
