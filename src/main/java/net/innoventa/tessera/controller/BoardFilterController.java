package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.filter.BoardFilterView;
import net.innoventa.tessera.dto.filter.FilterGrammarView;
import net.innoventa.tessera.service.filter.BuiltInBoardFilters;
import net.innoventa.tessera.service.filter.FilterGrammarReference;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The built-in board filters (ADR-0008). Not project-scoped: the catalog is the same everywhere, the
 * same way the issue-type and priority catalogs are, so it is read once and reused across projects.
 * <p>
 * It carries no project data — only names and predicates the product itself authored — so
 * authentication is the whole gate; there is nothing here to scope to a membership.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess
public class BoardFilterController {

    private final BuiltInBoardFilters builtInBoardFilters;
    private final FilterGrammarReference filterGrammarReference;

    @GetMapping("/api/board-filters")
    public List<BoardFilterView> builtInFilters() {
        return builtInBoardFilters.catalog();
    }

    /**
     * What a filter author may write — the editor's help panel. Served rather than written into the
     * client so it is generated from the same constants the evaluator reports errors against; a
     * cheat-sheet that has drifted from the engine costs an author more time than none at all.
     */
    @GetMapping("/api/board-filters/reference")
    public FilterGrammarView grammarReference() {
        return filterGrammarReference.reference();
    }

}
