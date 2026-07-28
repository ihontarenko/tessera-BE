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

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Seeds a project's {@link Board} (ADR-0009): one board plus one column per {@link StatusCategory} the
 * project's workflows can actually reach, each a category fallback with no explicit status mappings
 * (ADR-0010) — so a freshly seeded board shows every issue by category with zero configuration. Every
 * project gets a board, with no branch on what kind of project it is.
 * <p>
 * The columns are <strong>derived, not fixed</strong>: a project on a To Do ↔ Done workflow opens with
 * two columns rather than a dead "In Progress" nothing can ever enter, while the full workflow still
 * yields the familiar three. Only newly seeded boards are shaped this way — columns are configurable,
 * and an existing board is never rewritten.
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
    private final WorkflowResolver workflowResolver;
    private final Supplier<String> idGenerator;

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
        for (StatusCategory category : defaultColumnCategories(project)) {
            boardColumnRepository.save(BoardColumn.builder()
                .id(idGenerator.get())
                .boardId(board.getId())
                .name(defaultColumnName(category))
                .position(position)
                .fallbackForCategory(category)
                .build());
            position++;
        }

        return board;
    }

    /**
     * The categories the seeded board gets a column for, in board order. A workflow leading nowhere at
     * all would otherwise seed a columnless board, so that degenerate case falls back to all three —
     * provisioning always produces something usable.
     */
    private Set<StatusCategory> defaultColumnCategories(Project project) {
        Set<StatusCategory> reachable = workflowResolver.reachableCategories(project);

        if (reachable.isEmpty()) {
            return EnumSet.allOf(StatusCategory.class);
        }

        return reachable;
    }

    private String defaultColumnName(StatusCategory category) {
        return switch (category) {
            case TODO -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case DONE -> "Done";
        };
    }

}
