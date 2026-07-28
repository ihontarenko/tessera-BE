package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.domain.SwimlaneStrategy;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Seeds a project's {@link Board} (ADR-0009): one board plus the three default columns — To Do /
 * In Progress / Done, one per {@link StatusCategory}, each a category fallback with no explicit status
 * mappings (ADR-0010) — so a freshly seeded board shows every issue by category with zero
 * configuration. Provisioning is deliberately <strong>type-agnostic</strong>: every project gets a
 * board (a TODO project too; its default view is just the checklist), never an {@code if (type == …)}
 * branch.
 * <p>
 * The single provisioning path shared by both project creation ({@link ProjectService}) and the
 * startup backfill of pre-existing projects ({@link BoardBackfill}). Idempotent: a project that already
 * has a board is left untouched, so the backfill can run every boot safely.
 */
@Component
@RequiredArgsConstructor
public class BoardProvisioner {

    private static final String DEFAULT_BOARD_NAME = "Board";

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final Supplier<String> idGenerator;

    /** The three default columns, in board order: one fallback column per status category. */
    private record DefaultColumn(String name, StatusCategory category) {
    }

    private static final List<DefaultColumn> DEFAULT_COLUMNS = List.of(
        new DefaultColumn("To Do", StatusCategory.TODO),
        new DefaultColumn("In Progress", StatusCategory.IN_PROGRESS),
        new DefaultColumn("Done", StatusCategory.DONE)
    );

    /**
     * Provision the board for {@code projectId} if it has none, returning the existing or newly created
     * board. Safe to call unconditionally — the {@code project_id} unique constraint plus this guard
     * keep it one board per project.
     */
    @Transactional
    public Board provision(String projectId) {
        return boardRepository.findByProjectId(projectId).orElseGet(() -> seed(projectId));
    }

    private Board seed(String projectId) {
        Board board = boardRepository.save(Board.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .name(DEFAULT_BOARD_NAME)
            .swimlaneStrategy(SwimlaneStrategy.NONE)
            .hideDoneOlderThanDays(null)
            .build());

        int position = 0;
        for (DefaultColumn definition : DEFAULT_COLUMNS) {
            boardColumnRepository.save(BoardColumn.builder()
                .id(idGenerator.get())
                .boardId(board.getId())
                .name(definition.name())
                .position(position)
                .fallbackForCategory(definition.category())
                .build());
            position++;
        }

        return board;
    }

}
