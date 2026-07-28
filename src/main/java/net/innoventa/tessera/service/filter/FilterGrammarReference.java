package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.dto.filter.FilterGrammarView;
import net.innoventa.tessera.dto.filter.FilterGrammarView.GrammarEntry;
import net.innoventa.tessera.dto.filter.FilterGrammarView.GrammarSection;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What a filter author is allowed to write, described once (ADR-0008).
 * <p>
 * This is the <strong>only</strong> place the accessor list is spelled out. {@link BoardFilterEvaluator}
 * builds its unknown-accessor message from {@link #accessorNames()} and the editor's help panel renders
 * these same sections, so the documentation cannot drift from what the engine actually resolves — a
 * cheat-sheet that lies is worse than none, because it sends the author looking for a bug in their
 * expression.
 * <p>
 * It stays honest only as far as it is maintained alongside {@link IssueFilterView} and
 * {@link IssueLinkTestExtension}; {@code FilterGrammarReferenceTest} holds it to that by evaluating
 * every example and checking the accessor list against the record's own components.
 */
@Service
public class FilterGrammarReference {

    private static final List<GrammarEntry> ACCESSORS = List.of(
        new GrammarEntry("issue.id", "The issue's internal id"),
        new GrammarEntry("issue.key", "The issue key, e.g. TIC-42"),
        new GrammarEntry("issue.summary", "The one-line title"),
        new GrammarEntry("issue.type.name", "Issue type name, e.g. 'Bug'"),
        new GrammarEntry("issue.type.hierarchyLevel", "Epic 1, standard 0, sub-task -1"),
        new GrammarEntry("issue.priority.name", "'Highest', 'High', 'Medium', …"),
        new GrammarEntry("issue.status.name", "The status' own name"),
        new GrammarEntry("issue.status.category", "'TO_DO', 'IN_PROGRESS' or 'DONE'"),
        new GrammarEntry("issue.resolution", "Null exactly when the issue is open"),
        new GrammarEntry("issue.resolution.name", "e.g. 'Done', 'Won't Do'"),
        new GrammarEntry("issue.assignee", "Compare against currentMember"),
        new GrammarEntry("issue.reporter", "Compare against currentMember"),
        new GrammarEntry("issue.epic.key", "The resolved Epic ancestor's key"),
        new GrammarEntry("issue.labels", "List of label names"),
        new GrammarEntry("issue.links", "List of {type, direction, issueKey}"),
        new GrammarEntry("issue.createdAt", "Instant — compare against now"),
        new GrammarEntry("issue.updatedAt", "Instant — compare against now"),
        new GrammarEntry("issue.resolvedAt", "Instant, null while open")
    );

    private static final List<GrammarEntry> VARIABLES = List.of(
        new GrammarEntry("issue", "The card being tested"),
        new GrammarEntry("currentMember", "You — the member running the filter"),
        new GrammarEntry("now", "The moment the board was loaded, one instant for every card")
    );

    private static final List<GrammarEntry> OPERATORS = List.of(
        new GrammarEntry("== !=", "Equality. Members compare by identity, so a renamed person still matches"),
        new GrammarEntry("< <= > >=", "Ordering, including instants"),
        new GrammarEntry("and or not", "Combine predicates"),
        new GrammarEntry("is null", "Absence — issue.resolution is null means the issue is open"),
        new GrammarEntry("x in [a, b]", "Membership in a literal list. WRAP IT IN BRACKETS when combining with `and`")
    );

    private static final List<GrammarEntry> FILTERS = List.of(
        new GrammarEntry("| lower", "Lowercase a string"),
        new GrammarEntry("| upper", "Uppercase a string"),
        new GrammarEntry("| instant", "Coerce to an instant (timestamps already are one)"),
        new GrammarEntry("| minusDays(n)", "n days before — now | minusDays(7)"),
        new GrammarEntry("| plusDays(n)", "n days after")
    );

    private static final List<GrammarEntry> TESTS = List.of(
        new GrammarEntry("is contains(text)", "Substring of a string"),
        new GrammarEntry("is hasAny([a, b])", "The list holds at least one of these — use this for labels"),
        new GrammarEntry("is hasAll([a, b])", "The list holds all of these"),
        new GrammarEntry("is blocked", "Something is blocking this issue"),
        new GrammarEntry("is blocks(key)", "This issue blocks the named one"),
        new GrammarEntry("is linkedTo(type)", "Linked by a type, either direction")
    );

    private static final List<GrammarEntry> EXAMPLES = List.of(
        new GrammarEntry(
            "issue.assignee == currentMember and issue.resolution is null",
            "My open issues"),
        new GrammarEntry(
            "issue.labels is hasAny(['regression'])",
            "Carries the regression label"),
        new GrammarEntry(
            "(issue.priority.name in ['Highest','High']) and issue.resolution is null",
            "Important and still open"),
        new GrammarEntry(
            "issue.summary | lower is contains('login')",
            "Summary mentions login — lowercase the term yourself"),
        new GrammarEntry(
            "issue.status.category != 'DONE' and issue.updatedAt < (now | minusDays(14))",
            "Unfinished and untouched for two weeks"),
        new GrammarEntry(
            "issue is blocked and issue.assignee == currentMember",
            "Mine, and held up by something else")
    );

    /**
     * The pitfalls that produce a wrong answer rather than an error — the ones an author cannot debug by
     * reading their expression, because it looks right.
     */
    private static final List<GrammarEntry> PITFALLS = List.of(
        new GrammarEntry(
            "Bracket `in` when combining",
            "`a in ['x'] and b` reads as `a in (['x'] and b)` and answers false. "
                + "Write `(a in ['x']) and b`."),
        new GrammarEntry(
            "Use hasAny for list membership",
            "`'x' in issue.labels` misbehaves when the issue has exactly one label. "
                + "Write `issue.labels is hasAny(['x'])`."),
        new GrammarEntry(
            "A filter must ask a yes/no question",
            "`issue.summary` is a value, not a question. Compare it or use `is`.")
    );

    private static final FilterGrammarView REFERENCE = new FilterGrammarView(List.of(
        new GrammarSection("variables", "What you can start from", VARIABLES),
        new GrammarSection("accessors", "What an issue knows", ACCESSORS),
        new GrammarSection("operators", "Comparing and combining", OPERATORS),
        new GrammarSection("filters", "Transforming a value with |", FILTERS),
        new GrammarSection("tests", "Asking a question with is", TESTS),
        new GrammarSection("examples", "Worked examples", EXAMPLES),
        new GrammarSection("pitfalls", "Easy to get wrong", PITFALLS)
    ));

    public FilterGrammarView reference() {
        return REFERENCE;
    }

    /** The bare accessor names, for the evaluator's "an issue does not have that" message. */
    public static List<String> accessorNames() {
        return ACCESSORS.stream().map(GrammarEntry::syntax).toList();
    }

    /** Just the example expressions, so a test can prove every one of them still evaluates. */
    static List<String> exampleExpressions() {
        return EXAMPLES.stream().map(GrammarEntry::syntax).toList();
    }

}
