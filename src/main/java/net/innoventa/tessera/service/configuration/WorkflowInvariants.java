package net.innoventa.tessera.service.configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a proposed set of transitions would be wrong about — as a function, with nothing to load.
 *
 * <p>⚠️ <strong>The caller loads, this decides.</strong> No repository, no entity, no transaction: the
 * edges before, the edges after, the status names and how many issues sit in each go in, and violations
 * and warnings come out. That is the same seam {@code BoardColumnResolver} was extracted for, and for
 * the same reason — a rule mixed in with the query that feeds it can only be examined by running the
 * application.
 *
 * <h2>⚠️ One violation, and everything else is a warning</h2>
 *
 * <p>The distinction is not severity, it is whether the engine survives. A workflow with no create
 * transition makes {@code WorkflowResolver.initialStatus} throw and issue creation answer 500 for every
 * project on it — so zero or two of them refuses, always, whatever anybody intended. Everything else a
 * change can do is legitimate under some intent: a terminal status is a real thing, and it is
 * indistinguishable from a mistake except by asking.
 *
 * <p>So a warning is reported with its counts and then obeyed. Refusing them would mean the product
 * insisting it knows a team's process better than the team does, which is the failure this whole
 * cluster exists to end.
 */
public final class WorkflowInvariants {

    private WorkflowInvariants() {
    }

    /**
     * One edge, reduced to what a rule needs.
     *
     * @param fromStatusId null for the create transition — the one edge with no source
     */
    public record Edge(String id, String name, String fromStatusId, String toStatusId) {
    }

    /** Something that must not be saved, with the reason a person can act on. */
    public record Violation(String rule, String message) {
    }

    /**
     * Something worth knowing before saving.
     *
     * @param count how many issues it is about — the difference between a note and a decision
     */
    public record Warning(String rule, String message, long count) {
    }

    /** The whole answer. {@code permits} is the only question the write path asks. */
    public record Verdict(List<Violation> violations, List<Warning> warnings) {

        public boolean permits() {
            return violations.isEmpty();
        }

        /** Every violation as one sentence, for the refusal's prose half. */
        public String describeViolations() {
            return String.join(" ", violations.stream().map(Violation::message).toList());
        }
    }

    public static final String ONE_CREATE_TRANSITION = "oneCreateTransition";
    public static final String NO_DUPLICATE_EDGE     = "noDuplicateEdge";
    public static final String STATUS_LEFT_TERMINAL  = "statusLeftTerminal";

    /**
     * Judge a proposed set of edges against the set it replaces.
     *
     * <p>Both are needed, and only for the warnings: "this status has no way out" describes half the
     * terminal statuses in any healthy workflow, while "this status has just <em>lost</em> its way out"
     * describes a decision somebody is making right now. Reporting the first would train everybody to
     * click past the second.
     *
     * @param current        the workflow's edges as stored — empty when the workflow is being created
     * @param resulting      the edges as they would be after the change
     * @param statusNames    status id → name, for the messages; an id absent from it is named by its id
     * @param issuesByStatus how many issues hold each status, for the warnings' counts
     */
    public static Verdict check(
        List<Edge> current,
        List<Edge> resulting,
        Map<String, String> statusNames,
        Map<String, Long> issuesByStatus) {

        List<Violation> violations = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();

        checkExactlyOneCreateTransition(resulting, violations);
        checkNoNewDuplicateEdges(current, resulting, statusNames, violations);
        collectNewlyTerminalStatuses(current, resulting, statusNames, issuesByStatus, warnings);

        return new Verdict(List.copyOf(violations), List.copyOf(warnings));
    }

    /**
     * ⚠️ The one hard rule. {@code findFirstByWorkflowIdAndFromStatusIdIsNull} tolerates two today and
     * picks arbitrarily, which means two identical installations can create issues into different
     * statuses; and zero makes it throw. Neither is a state anybody can be allowed to save.
     */
    private static void checkExactlyOneCreateTransition(List<Edge> resulting, List<Violation> violations) {
        List<Edge> creates = resulting.stream().filter(edge -> edge.fromStatusId() == null).toList();

        if (creates.size() == 1) {
            return;
        }

        violations.add(new Violation(ONE_CREATE_TRANSITION, creates.isEmpty()
            ? "A workflow needs exactly one create transition — the edge with no source, naming the "
              + "status a new issue lands in. Without it, creating an issue on this workflow fails."
            : "A workflow may have only one create transition, and this would leave "
              + creates.size() + ". Which status a new issue lands in cannot be two answers."));
    }

    /**
     * At most one edge per {@code (from, to)} — <strong>among the duplicates this change would add</strong>.
     *
     * <p>The schema does not enforce it and the engine does not need it: a duplicate makes no move
     * illegal. What it does is put the same destination in the transition menu twice under two names,
     * which reads as two different things that do the same thing.
     *
     * <p>⚠️ <strong>A duplicate that is already stored is left alone, and that is the difference between
     * a rule and a trap.</strong> Judging the whole resulting set would mean any workflow that already
     * holds a duplicate — the schema has never stopped one — answers 409 to every edit, including the
     * edit that would remove it. The workflow would be permanently uneditable because of a row somebody
     * inserted before this rule existed. So the comparison is against what is there now: a pairing that
     * was already duplicated stays somebody's to clean up, and only a new collision refuses.
     */
    private static void checkNoNewDuplicateEdges(
        List<Edge> current, List<Edge> resulting, Map<String, String> statusNames, List<Violation> violations) {

        Set<String> alreadyDuplicated = duplicatedPairs(current);
        Set<String> introduced = new LinkedHashSet<>(duplicatedPairs(resulting));
        introduced.removeAll(alreadyDuplicated);

        introduced.forEach(pair -> violations.add(new Violation(NO_DUPLICATE_EDGE,
            "There is already a transition " + describePair(pair, statusNames) + ". One edge per pair of "
            + "statuses — a second one adds a duplicate entry to the transition menu and no new move.")));
    }

    /** Every {@code from→to} that appears more than once, as the raw identifier pair. */
    private static Set<String> duplicatedPairs(List<Edge> edges) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();

        for (Edge edge : edges) {
            String pair = edge.fromStatusId() + "→" + edge.toStatusId();

            if (!seen.add(pair)) {
                duplicated.add(pair);
            }
        }

        return duplicated;
    }

    /** The identifier pair as the two status names a person would recognise. */
    private static String describePair(String pair, Map<String, String> statusNames) {
        String[] ends = pair.split("→", 2);
        String from = "null".equals(ends[0]) ? null : ends[0];

        return nameOf(statusNames, from) + " → " + nameOf(statusNames, ends.length > 1 ? ends[1] : null);
    }

    /**
     * Statuses that had a way out and would not any more.
     *
     * <p>Carries the issue count because that is what turns the note into a decision: a terminal status
     * nothing is in costs nothing, and one holding forty issues has just stranded forty pieces of work
     * wherever they are.
     */
    private static void collectNewlyTerminalStatuses(
        List<Edge> current,
        List<Edge> resulting,
        Map<String, String> statusNames,
        Map<String, Long> issuesByStatus,
        List<Warning> warnings) {

        Set<String> hadAWayOut = sourcesOf(current);
        Set<String> hasAWayOut = sourcesOf(resulting);

        for (String statusId : hadAWayOut) {
            if (hasAWayOut.contains(statusId)) {
                continue;
            }

            long issues = issuesByStatus.getOrDefault(statusId, 0L);

            warnings.add(new Warning(STATUS_LEFT_TERMINAL,
                nameOf(statusNames, statusId) + " would have no way out of it, and "
                + issues + " issue(s) are in it. That is a legitimate terminal status and it is also "
                + "what a mistake looks like — nothing here can tell the two apart.",
                issues));
        }
    }

    /** Every status some edge leaves from — the create transition has no source and is not one. */
    private static Set<String> sourcesOf(List<Edge> edges) {
        return edges.stream()
            .map(Edge::fromStatusId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** A name where there is one, and the identifier where there is not — never an empty gap. */
    private static String nameOf(Map<String, String> statusNames, String statusId) {
        if (statusId == null) {
            return "(new issue)";
        }

        return statusNames.getOrDefault(statusId, statusId);
    }

}
