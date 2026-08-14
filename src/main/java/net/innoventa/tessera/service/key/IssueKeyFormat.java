package net.innoventa.tessera.service.key;

import java.util.Arrays;
import java.util.List;

/**
 * The key shapes a project may choose, and the pattern behind each.
 *
 * <p>⚠️ <strong>Every one of these is the same renderer with a different pattern.</strong> ADR-0003
 * made the strategy pluggable and Phase 1 shipped exactly one, so "pluggable" was a claim nothing
 * tested. What it turns out to buy is this list: four shapes, no branching, and a fifth that is a
 * member writing the pattern themselves.
 *
 * <p>⚠️ <strong>{@link #CUSTOM} has no pattern of its own</strong> — it takes the project's, which is
 * why it is the only one whose pattern is validated. The four shipped ones cannot be wrong.
 *
 * <p>Explicitly not here: a counter that restarts each period, so that {@code OPS-2026-1} begins again
 * every January. It breaks the single-counter invariant ADR-0003 rests on. The date tokens are
 * decorative and the sequence is always in the key.
 */
public enum IssueKeyFormat {

    /** {@code TIC-1} — what every project created before this ticket uses. */
    PREFIXED_SEQUENCE("${key}-${sequence}", "TIC-42"),

    /** {@code TIC-0042} — the same, sorted correctly by anything that sorts text. */
    PADDED_SEQUENCE("${key}-${sequence:0000}", "TIC-0042"),

    /** {@code OPS-2026-42} — the year an issue was raised, in the key. */
    DATE_PREFIXED("${key}-${year}-${sequence}", "OPS-2026-42"),

    /** {@code OPS-202608-42} — the month too, for work that is filed by the month it arrived in. */
    YEAR_MONTH_SEQUENCE("${key}-${year}${month}-${sequence}", "OPS-202608-42"),

    /** ⚠️ Takes the project's own pattern, and is the only one that can be written wrongly. */
    CUSTOM(null, null);

    private final String pattern;
    private final String example;

    IssueKeyFormat(String pattern, String example) {
        this.pattern = pattern;
        this.example = example;
    }

    /** The pattern this format renders, or null for {@link #CUSTOM}, which uses the project's. */
    public String pattern() {
        return pattern;
    }

    /** What it looks like, for a picker that would otherwise be five names nobody can tell apart. */
    public String example() {
        return example;
    }

    public static List<IssueKeyFormat> all() {
        return Arrays.asList(values());
    }

    public static boolean isKnown(String name) {
        return Arrays.stream(values()).anyMatch(format -> format.name().equals(name));
    }

}
