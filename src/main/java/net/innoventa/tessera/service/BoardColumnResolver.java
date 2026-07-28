package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The board-render core (ADR-0010): resolve which {@link BoardColumn} an issue's status belongs in.
 * A pure function of {@code (status, the board's columns, the board's explicit status→column map)} —
 * an explicit {@code BoardColumnStatus} mapping wins; otherwise the column designated
 * {@code fallbackForCategory == status.category}. With a default board (zero explicit mappings) every
 * issue resolves purely by category, and because exactly one fallback column per category always
 * exists, resolution is total — no issue falls off the board.
 * <p>
 * Kept a stateless component (no injected repositories) so it is the narrow unit-test seam the spec
 * calls out, and so the caller supplies already-prefetched inputs rather than this triggering a
 * per-card query.
 */
@Component
public class BoardColumnResolver {

    /**
     * The column id an issue with {@code status} belongs in, or {@code null} if no column can hold it
     * (which the "one fallback per category" invariant prevents on a well-formed board).
     *
     * @param status         the issue's status (carries its {@link StatusCategory})
     * @param columns        the board's columns, in any order
     * @param statusToColumn explicit mappings, {@code statusId -> columnId}, prefetched for the board
     */
    public String resolveColumnId(Status status, List<BoardColumn> columns, Map<String, String> statusToColumn) {
        if (status == null) {
            return null;
        }

        String explicitColumnId = statusToColumn.get(status.getId());
        if (explicitColumnId != null) {
            return explicitColumnId;
        }

        return columns.stream()
            .filter(column -> column.getFallbackForCategory() == status.getCategory())
            .map(BoardColumn::getId)
            .findFirst()
            .orElse(null);
    }

}
