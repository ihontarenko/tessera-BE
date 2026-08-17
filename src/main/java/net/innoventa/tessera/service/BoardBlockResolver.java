package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.block.BlockStatus;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * {@code :::board TSSR} — where a project's work is standing, column by column (TSSR-18).
 *
 * <p>⚠️ <strong>It asks {@link BoardService} rather than counting issues itself</strong>, and that is
 * the one decision in this class. A column is not a status: it is a configured set of statuses plus a
 * category fallback, resolved by {@code BoardColumnResolver}, and a sprint-scoped board shows only the
 * running sprint. Re-deriving any of that here would produce numbers that agree with the board screen
 * until somebody changes a column mapping, and then disagree silently on a page nobody thinks to check.
 *
 * <p>The cost is that a block loads the whole board to count its columns. A page carries one of these,
 * not twenty, and being right is worth a query.
 */
@Component
@RequiredArgsConstructor
public class BoardBlockResolver implements PageBlockResolver {

    private final BoardService boardService;
    private final ProjectRepository projectRepository;
    private final ProjectAccess projectAccess;

    @Override
    public String directive() {
        return "board";
    }

    @Override
    public PageBlockView resolve(BlockRequest request) {
        String argument = request.argument();
        Project project = visibleProject(request.caller(), argument);

        if (project == null) {
            return PageBlockView.miss(directive(), argument, BlockStatus.NOT_FOUND);
        }

        // No filter: a filter narrows what one viewer is looking at, and a page is read by everybody.
        BoardResponse board = boardService.getBoard(request.caller(), project.getId(), null);

        List<PageBlockView.BoardBlock.Column> columns = board.columns().stream()
            .map(column -> new PageBlockView.BoardBlock.Column(column.name(), countIn(board.cards(), column)))
            .toList();

        return PageBlockView.of(directive(), argument,
            new PageBlockView.BoardBlock(project.getKey(), project.getName(), columns));
    }

    private static int countIn(List<BoardCardView> cards, BoardColumnView column) {
        return (int) cards.stream().filter(card -> column.id().equals(card.columnId())).count();
    }

    /** As {@link IssueBlockResolver}: not-yours and no-such-project are deliberately one answer. */
    private Project visibleProject(Member caller, String projectKey) {
        return projectRepository.findByKey(projectKey.trim().toUpperCase(Locale.ROOT))
            .filter(project -> projectAccess.holds(caller, project.getId(), Permissions.BROWSE_PROJECT))
            .orElse(null);
    }

}
