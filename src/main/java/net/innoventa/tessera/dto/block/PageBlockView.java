package net.innoventa.tessera.dto.block;

import java.time.LocalDate;
import java.util.List;

/**
 * One directive, answered (TSSR-18).
 *
 * <p>⚠️ <strong>A flat record with one nullable field per block kind</strong>, rather than a sealed
 * hierarchy or a {@code Map<String, Object>} payload. Jackson serialises it without a type discriminator
 * the client would have to switch on twice — once to parse and once to render — and the client reads
 * exactly the field named by {@code name}. A fourth block kind is a field here and a resolver; nothing
 * else in the chain learns about it.
 *
 * <p>⚠️ <strong>{@code status} is never an error.</strong> Documents outlive their subjects. Every way a
 * block can fail to render is one of these, and each renders as a visible notice — see
 * {@link BlockStatus}.
 */
public record PageBlockView(
    String name,
    String argument,
    BlockStatus status,
    IssueBlock issue,
    SprintBlock sprint,
    BoardBlock board
) {

    public static PageBlockView miss(String name, String argument, BlockStatus status) {
        return new PageBlockView(name, argument, status, null, null, null);
    }

    public static PageBlockView of(String name, String argument, IssueBlock issue) {
        return new PageBlockView(name, argument, BlockStatus.RESOLVED, issue, null, null);
    }

    public static PageBlockView of(String name, String argument, SprintBlock sprint) {
        return new PageBlockView(name, argument, BlockStatus.RESOLVED, null, sprint, null);
    }

    public static PageBlockView of(String name, String argument, BoardBlock board) {
        return new PageBlockView(name, argument, BlockStatus.RESOLVED, null, null, board);
    }

    /**
     * One issue, as a page shows it.
     *
     * <p>{@code open} is carried rather than derived from the status name, the same invariant every
     * other read in this product uses (ADR-0004) — it keeps working when somebody adds a status this
     * code has never heard of.
     *
     * <p>⚠️ <strong>Story points travel as the stored weight, not as the word.</strong> An estimation
     * scheme stores {@code XL} as {@code 8} (ADR-0019), and turning it back into the label needs the
     * project's scheme — a second read, per block, to relabel one number. A block says {@code 8 points},
     * which is what the burndown and the sprint report say too.
     */
    public record IssueBlock(
        String issueKey,
        String summary,
        String typeName,
        String statusName,
        String statusCategory,
        /** ⚠️ Null means "drawn from the category" (TSSR-21), never "no colour". */
        String statusColor,
        String priorityName,
        String assigneeName,
        /** ⚠️ The stored weight, not the word the team picked for it (ADR-0019) — see the record's note. */
        Double storyPoints,
        boolean open,
        String resolutionName
    ) {
    }

    /**
     * One sprint, as a page shows it.
     *
     * <p>⚠️ <strong>Points are counted from the sprint's current members, not from a snapshot.</strong>
     * Everything in this product's sprint reporting is derived on read (ADR-0013), and a block that
     * cached its own numbers would be the one place they could disagree with the sprint report on the
     * same screen.
     */
    public record SprintBlock(
        String projectKey,
        String name,
        String goal,
        String state,
        LocalDate endDate,
        int issueCount,
        int completedIssueCount,
        Double storyPoints,
        Double completedStoryPoints
    ) {
    }

    /** A board, as a page shows it: the columns and how much is standing in each. */
    public record BoardBlock(
        String projectKey,
        String projectName,
        List<Column> columns
    ) {
        public record Column(String name, int issueCount) {
        }
    }

}
