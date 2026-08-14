package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One board's columns and its explicit status mappings, loaded together — because
 * {@link net.innoventa.tessera.service.BoardColumnResolver} never wants one without the other.
 *
 * <p>The pair was being loaded, and then travelling, as two separate values in two services asking the
 * same question of the same board. Two things follow from making it one: the query lives in one place,
 * and the resolver's two arguments become one that has a name — a board's <em>mapping</em>, which is
 * what an administrator would call it.
 */
@Component
@RequiredArgsConstructor
public class BoardMapping {

    private final BoardColumnRepository       boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;

    /** A board's columns in display order, and the statuses explicitly pinned to them. */
    @Transactional(readOnly = true)
    public Mapping of(String boardId) {
        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(boardId);
        Map<String, String> statusToColumn = boardColumnStatusRepository.findByBoardId(boardId).stream()
            .collect(Collectors.toMap(BoardColumnStatus::getStatusId, BoardColumnStatus::getBoardColumnId));

        return new Mapping(columns, statusToColumn);
    }

    /**
     * What the board-render core needs to answer where a status goes (ADR-0010).
     *
     * @param statusToColumn explicit {@code statusId -> columnId} pins, which outrank the category
     *                       fallback; a default board has none
     */
    public record Mapping(List<BoardColumn> columns, Map<String, String> statusToColumn) {

        /** The column's name, or null where the status maps nowhere — which is the backlog (ADR-0016). */
        public String columnName(String columnId) {
            if (columnId == null) {
                return null;
            }

            return columns.stream()
                .filter(column -> column.getId().equals(columnId))
                .map(BoardColumn::getName)
                .findFirst()
                .orElse(null);
        }
    }
}
