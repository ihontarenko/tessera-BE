package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.ProjectTypeDefaultScheme;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.domain.SwimlaneStrategy;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.ProjectTypeDefaultSchemeRepository;
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
 * branch. The one thing the type does decide — whether the board renders the whole project or only the
 * active sprint (ADR-0012) — is read from {@link ProjectTypeDefaultScheme}, the same data-driven preset
 * that already supplies the schemes.
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
    private final ProjectTypeDefaultSchemeRepository projectTypeDefaultSchemeRepository;
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
     * one board per project.
     */
    @Transactional
    public Board provision(Project project) {
        return boardRepository.findByProjectId(project.getId()).orElseGet(() -> seed(project));
    }

    /**
     * The scope strategy the project's type preset dictates. A type with no preset row falls back to
     * {@code ALL_ISSUES} — the behaviour that predates sprints, and the safe reading of "we don't know
     * that this project plans in sprints".
     */
    private BoardScopeStrategy scopeStrategyFor(Project project) {
        return projectTypeDefaultSchemeRepository.findById(project.getType())
            .map(ProjectTypeDefaultScheme::getBoardScopeStrategy)
            .orElse(BoardScopeStrategy.ALL_ISSUES);
    }

    private Board seed(Project project) {
        Board board = boardRepository.save(Board.builder()
            .id(idGenerator.get())
            .projectId(project.getId())
            .name(DEFAULT_BOARD_NAME)
            .swimlaneStrategy(SwimlaneStrategy.NONE)
            .scopeStrategy(scopeStrategyFor(project))
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
