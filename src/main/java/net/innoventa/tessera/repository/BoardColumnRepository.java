package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.StatusCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, String> {

    List<BoardColumn> findByBoardIdOrderByPositionAsc(String boardId);

    /** The column currently designated as the category's fallback, if any (ADR-0010's invariant). */
    Optional<BoardColumn> findByBoardIdAndFallbackForCategory(String boardId, StatusCategory category);

}
