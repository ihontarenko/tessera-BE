package net.innoventa.tessera.service.filter;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.filter.BoardFilterView;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The named quick filters the product ships (ADR-0008's "Authoring scope", Phase 2): the ADR's example
 * expressions, registered <strong>server-side</strong>.
 * <p>
 * The expressions live here rather than in the client, which is the whole point of retiring the Phase-2
 * stub. The board's toolbar renders whatever this catalog says and hands the chosen expressions back
 * through {@code ?filter=}; it no longer knows what "unassigned" means, so the definition of a filter
 * changes in one place and cannot drift between the two sides.
 * <p>
 * The first three are the stub's toggles, one-for-one — ADR-0008 nominates them as the acceptance
 * oracle, and {@code BoardFilterEvaluatorTest} holds them to it. The rest are the ADR's remaining
 * parameterless rows; the parameterised ones (a label, an epic key, a search term) are not toggles and
 * wait for Phase 4's saved-filter editor, where a member supplies the argument.
 */
@Service
@RequiredArgsConstructor
public class BuiltInBoardFilters {

    /**
     * Note the parentheses around {@code in}: jME binds {@code in} <em>looser</em> than {@code and}, so
     * the ADR's unbracketed spelling parses as {@code name in (['Highest','High'] and …)} and quietly
     * answers false. The brackets are load-bearing, not decoration.
     */
    private static final List<BoardFilterView> CATALOG = List.of(
        /*
         * The board's resting state, and the only entry that is not a narrowing. A board with no filter
         * chosen used to send no `filter=` at all, which worked but left the toolbar showing nine
         * toggles and no indication of what was on — "everything" was a state with no control
         * representing it. It is a filter now: selected by default, and exclusive, because "everything
         * and only my issues" is not a sentence. `true` is a legal jME predicate, so the server needs
         * no special case for it either.
         */
        new BoardFilterView(
            "all-issues",
            "board.filter.allIssues",
            "All issues",
            "true",
            true,
            true),
        BoardFilterView.of(
            "my-issues",
            "board.filter.myIssues",
            "My issues",
            "issue.assignee == currentMember"),
        BoardFilterView.of(
            "unassigned",
            "board.filter.unassigned",
            "Unassigned",
            "issue.assignee is null"),
        BoardFilterView.of(
            "unresolved",
            "board.filter.unresolved",
            "Unresolved",
            "issue.resolution is null"),
        BoardFilterView.of(
            "recently-updated",
            "board.filter.recentlyUpdated",
            "Recently updated",
            "issue.updatedAt >= (now | minusDays(2))"),
        BoardFilterView.of(
            "only-bugs",
            "board.filter.onlyBugs",
            "Only bugs",
            "issue.type.name == 'Bug'"),
        BoardFilterView.of(
            "hot-and-open",
            "board.filter.hotAndOpen",
            "Hot & open",
            "(issue.priority.name in ['Highest','High']) and issue.resolution is null"),
        BoardFilterView.of(
            "in-progress-mine",
            "board.filter.inProgressMine",
            "My active work",
            "issue.status.category == 'IN_PROGRESS' and issue.assignee == currentMember"),
        BoardFilterView.of(
            "blocked",
            "board.filter.blocked",
            "Blocked",
            "issue is blocked"),
        BoardFilterView.of(
            "stale",
            "board.filter.stale",
            "Stale",
            "issue.status.category != 'DONE' and issue.updatedAt < (now | minusDays(14))")
    );

    private final BoardFilterEvaluator boardFilterEvaluator;

    public List<BoardFilterView> catalog() {
        return CATALOG;
    }

    /**
     * A typo in a shipped filter should not wait for a member to click the toggle that reveals it. Every
     * catalog expression is parsed at startup, so a broken one fails the context rather than becoming a
     * {@code 400} in production.
     */
    @PostConstruct
    void requireCatalogParses() {
        CATALOG.forEach(filter -> boardFilterEvaluator.requireStorable(filter.expression()));
    }

}
