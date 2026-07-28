package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Resolves the Epic an issue belongs to (Phase-2 ticket 04) by walking the unified parent hierarchy
 * upward until it reaches a type at {@link #EPIC_HIERARCHY_LEVEL} — so a sub-task resolves through its
 * story to the epic, and an epic resolves to itself. An issue with no epic ancestor resolves to
 * {@code null}; the client gathers those into its "No epic" lane rather than dropping them.
 * <p>
 * Pure apart from the {@code ancestors} lookup it is handed, which is what keeps the board's hot path
 * query-free: {@link BoardService} passes a lookup backed by the already-loaded project slice, so a
 * whole board resolves without touching the repository.
 */
@Service
public class EpicResolver {

    /** The hierarchy level that <em>is</em> "Epic" — levels are data, not an enum (see {@link IssueType}). */
    static final int EPIC_HIERARCHY_LEVEL = 1;

    /** Epic keys for a whole slice, keyed by issue id; issues with no epic ancestor are simply absent. */
    public Map<String, String> resolveAll(
        Collection<Issue> issues,
        Map<String, IssueType> typesById,
        Function<String, Issue> ancestorLookup
    ) {
        Map<String, String> epicKeys = new HashMap<>();

        for (Issue issue : issues) {
            String epicKey = resolve(issue, typesById, ancestorLookup);
            if (epicKey != null) {
                epicKeys.put(issue.getId(), epicKey);
            }
        }

        return epicKeys;
    }

    /**
     * The key of the nearest Epic at or above {@code issue}, or {@code null} if the chain reaches the
     * top without one. The walk starts at the issue itself, so an Epic resolves to its own key and
     * lands in its own lane rather than in "No epic" — the reading that keeps every card somewhere
     * sensible.
     * <p>
     * Hierarchy levels strictly decrease downward ({@link IssueHierarchyService}), so a cycle is
     * impossible by construction — the visited set is belt-and-braces against corrupt data rather than
     * an expected case.
     */
    private String resolve(Issue issue, Map<String, IssueType> typesById, Function<String, Issue> ancestorLookup) {
        Set<String> visited = new HashSet<>();
        Issue current = issue;

        while (current != null && visited.add(current.getId())) {
            IssueType type = typesById.get(current.getIssueTypeId());
            if (type != null && type.getHierarchyLevel() == EPIC_HIERARCHY_LEVEL) {
                return current.getIssueKey();
            }
            current = current.getParentId() == null ? null : ancestorLookup.apply(current.getParentId());
        }

        return null;
    }

}
