package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.service.ProjectService;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.BoardService;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The board, as columns with what is sitting in each.
 *
 * <p><strong>Reshaped rather than returned.</strong> {@code BoardResponse} is a flat list of columns
 * beside a flat list of cards, each card carrying the column it belongs to — right for a client that
 * renders lanes and re-sorts them, and wrong for a model, which would have to do the join itself and
 * would sometimes get it wrong. What a person means by <em>read the board</em> is the nesting, so the
 * nesting is what this answers.
 *
 * <p>⚠️ Work-in-progress limits travel with the column. A model asked to move something into a full
 * column should be able to say so before it tries, and the number is meaningless without the count
 * beside it.
 */
@Component
@RequiredArgsConstructor
public class BoardTool implements ToolDefinition {

    private final BoardService   boardService;
    private final ProjectService projectService;
    private final ToolMembers    members;

    @Override
    public String toolName() {
        return "boards";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(list(), read());
    }

    /**
     * ⚠️ <strong>Every project has exactly one board, so this is thinner than its name suggests</strong>
     * — and it is still worth having. {@code Board.projectId} is unique (ADR-0015), so there is no
     * "which board" to choose and the useful question is a different one: <em>which of my projects plan
     * in sprints</em>. A model that knows that before it starts does not offer to move something into a
     * sprint that cannot exist.
     *
     * <p>Not scope-confined, for the reason {@code projects.list} is not: it is how a caller finds out
     * what there is.
     */
    private ToolAction list() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("list")
                .title("List boards")
                .description("Lists the board of every project this person belongs to, and whether each "
                           + "one is scoped to an active sprint or shows everything. Every project has "
                           + "exactly one board, so this is really the answer to 'which of my projects "
                           + "plan in sprints'.")
                .inputSchema(ArgumentSchema.none())
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .handler(this::handleList)
                .build();
    }

    private Object handleList(ToolInvocation invocation) {
        return projectService.list(members.actingSubject(invocation)).stream()
                .map(project -> {
                    Map<String, Object> described = new LinkedHashMap<>();

                    described.put("project", project.key());
                    described.put("scope",
                            project.boardScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT
                                    ? "the active sprint"
                                    : "every open issue");

                    return described;
                })
                .toList();
    }

    private ToolAction read() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("read")
                .title("Read a project's board")
                .description("Reads the board for a project: its columns in order, and the issues "
                           + "sitting in each. A sprint board shows the active sprint's issues only; "
                           + "a Kanban board shows everything open. Column limits are reported where a "
                           + "column has them, so an over-full column is visible before anything is "
                           + "moved into it.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .optionalString("filter",
                                "A board filter expression to mark matching cards, e.g. "
                              + "issue.assignee == currentMember. Omit to see everything unmarked."))
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .scopeConfined()
                .handler(this::handleRead)
                .build();
    }

    private Object handleRead(ToolInvocation invocation) {
        BoardResponse board = boardService.getBoard(
                members.actingSubject(invocation),
                invocation.scopeId(),
                invocation.optionalString("filter").orElse(null));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("board", board.name());

        if (board.activeSprint() != null) {
            answer.put("activeSprint", board.activeSprint().name());
        }

        answer.put("columns", board.columns().stream()
                .sorted(Comparator.comparingInt(BoardColumnView::position))
                .map(column -> describe(column, board.cards()))
                .toList());

        return answer;
    }

    private Map<String, Object> describe(BoardColumnView column, List<BoardCardView> allCards) {
        List<BoardCardView> here = allCards.stream()
                .filter(card -> column.id().equals(card.columnId()))
                .toList();

        Map<String, Object> described = new LinkedHashMap<>();

        described.put("name",  column.name());
        described.put("count", here.size());

        // Only where the board actually sets one. A null limit reported as "limit: null" reads to a
        // model as a limit it has to reason about.
        if (column.maxIssues() != null) {
            described.put("limit", column.maxIssues());
            described.put("overLimit", here.size() > column.maxIssues());
        }

        described.put("issues", here.stream().map(this::describe).toList());

        return described;
    }

    private Map<String, Object> describe(BoardCardView card) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",     card.issueKey());
        described.put("summary", card.summary());
        described.put("status",  card.status() == null ? null : card.status().name());

        if (card.assignee() != null) {
            described.put("assignee", card.assignee().displayName());
        }

        return described;
    }
}
