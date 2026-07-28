package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Project;
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
 * configuration. Every project gets a board, with no branch on what kind of project it is.
 * <p>
 * The scope strategy is a <strong>parameter</strong> rather than something looked up here. It used to
 * be read from a type -> preset table through {@code Project.type}; with the type gone (ADR-0015) the
 * strategy is the stored fact itself, so the caller that knows the answer states it and this class
 * resolves nothing.
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
     * Provision {@code project}'s board if it has none, returning the existing or newly created board.
     * Safe to call unconditionally — the {@code project_id} unique constraint plus this guard keep it
     * one board per project. {@code scopeStrategy} applies only to a board actually seeded here; an
     * existing board keeps whatever it was last set to, which is what makes repeated calls harmless.
     */
    @Transactional
    public Board provision(Project project, BoardScopeStrategy scopeStrategy) {
        return boardRepository.findByProjectId(project.getId())
            .orElseGet(() -> seed(project, scopeStrategy));
    }

    private Board seed(Project project, BoardScopeStrategy scopeStrategy) {
        Board board = boardRepository.save(Board.builder()
            .id(idGenerator.get())
            .projectId(project.getId())
            .name(DEFAULT_BOARD_NAME)
            .swimlaneStrategy(SwimlaneStrategy.NONE)
            .scopeStrategy(scopeStrategy)
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
