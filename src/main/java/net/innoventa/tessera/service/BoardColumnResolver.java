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
 * {@code fallbackForCategory == status.category}.
 * <p>
 * Resolution is <strong>partial by design</strong> (ADR-0016). ADR-0010 originally guaranteed a
 * fallback column per category so that nothing could fall off the board; a status may now be mapped
 * nowhere on purpose, and "no column" is a legal, meaningful answer rather than a malformed board.
 * What it means is that the board does not render the issue and the backlog does — the two are
 * complementary, so an issue is on one or the other, never both and never neither. Unmapping a status
 * is therefore the administrator's control over what the backlog contains.
 * <p>
 * Kept a stateless component (no injected repositories) so it is the narrow unit-test seam the spec
 * calls out, and so the caller supplies already-prefetched inputs rather than this triggering a
 * per-card query.
 */
@Component
public class BoardColumnResolver {

    /**
     * The column id an issue with {@code status} belongs in, or {@code null} when the board maps that
     * status nowhere — see the class note: that is a destination (the backlog), not a failure.
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

    /**
     * Does the board show an issue in {@code status} at all? The same resolution read as a yes/no —
     * named because "the board does not render it" is the question the backlog asks (ADR-0016), and a
     * null-check at each call site would say how the answer is computed rather than what it means.
     */
    public boolean rendersStatus(Status status, List<BoardColumn> columns, Map<String, String> statusToColumn) {
        return resolveColumnId(status, columns, statusToColumn) != null;
    }

}
