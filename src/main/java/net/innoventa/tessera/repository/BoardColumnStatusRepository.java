package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.BoardColumnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardColumnStatusRepository extends JpaRepository<BoardColumnStatus, String> {

    /** Every explicit mapping of a board, prefetched in one lookup for the board-render path. */
    List<BoardColumnStatus> findByBoardId(String boardId);

    /** A status maps to at most one column per board (the unique constraint) — its current row, if any. */
    Optional<BoardColumnStatus> findByBoardIdAndStatusId(String boardId, String statusId);

    /** A column's own explicit mappings — cleared when the column is deleted. */
    List<BoardColumnStatus> findByBoardColumnId(String boardColumnId);

}
