package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.ScheduleState;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * When an issue is meant to happen: the three dates, plus the one word they add up to today.
 *
 * <h2>⚠️ The dates and the verdict travel together, and neither is enough alone</h2>
 *
 * <p>Sending only the dates would make every reader re-derive the precedence — a card, a row, a rail
 * and a protocol client each writing out six comparisons, and drifting. Sending only {@code state}
 * would answer "is this urgent" and lose "when", which is the thing somebody actually edits and the
 * thing a client has to echo back to change it.
 *
 * <p>⚠️ <strong>{@code state} is derived on read and stored nowhere.</strong> It changes at midnight
 * without anything being written, which is exactly what a stored copy could not do — a column saying
 * {@code DUE_TODAY} is wrong by morning unless something runs overnight to fix it, and something that
 * has to run overnight is something that can fail to.
 *
 * @param queuedFor          the day somebody means to pick it up — a plan, freely moved, cleared on
 *                           completion
 * @param redLine            the day it stops being comfortable — a warning set ahead of the commitment
 * @param deadline           the day it is due — a commitment to somebody else
 * @param state              how pressing all of that is today
 * @param daysUntilDeadline  whole days from today to the deadline; negative once it is past, null when
 *                           there is no deadline. ⚠️ Here because <strong>"in three days" is what both a
 *                           badge and a client actually want to say</strong>, and because date
 *                           arithmetic is not available where they would otherwise compute it: the
 *                           expression language's {@code plusDays} answers only for instants and returns
 *                           null for a date, and a comparison against null quietly matches everything. A
 *                           number needs none of that.
 */
public record IssueScheduleView(
    LocalDate queuedFor,
    LocalDate redLine,
    LocalDate deadline,
    ScheduleState state,
    Integer daysUntilDeadline
) {

    /** An issue that has said nothing about when — every listing's common case. */
    public static final IssueScheduleView EMPTY =
        new IssueScheduleView(null, null, null, ScheduleState.NONE, null);

    /**
     * The schedule of one issue, judged against a day.
     *
     * <p>⚠️ <strong>{@code today} is passed in rather than read here.</strong> A board renders hundreds
     * of cards from one request and every one of them has to be judged against the same day; a render
     * that straddled midnight would otherwise paint two cards differently for no reason a reader could
     * reproduce.
     */
    public static IssueScheduleView from(Issue issue, LocalDate today) {
        LocalDate queuedFor = issue.getQueuedFor();
        LocalDate redLine   = issue.getRedLine();
        LocalDate deadline  = issue.getDeadline();

        if (queuedFor == null && redLine == null && deadline == null) {
            return EMPTY;
        }

        return new IssueScheduleView(
            queuedFor,
            redLine,
            deadline,
            stateOf(issue, queuedFor, redLine, deadline, today),
            deadline == null ? null : (int) ChronoUnit.DAYS.between(today, deadline));
    }

    /**
     * ⚠️ <strong>A finished issue is never pressing, whatever its dates say.</strong> "Overdue" means the
     * work has missed its deadline <em>and has still not happened</em>; once it has, the issue is not
     * overdue — it was <em>late</em>, which is a different fact, belongs on a report, and is exactly the
     * thing a red badge on the Shipped screen would drown out. Without this, every completed issue that
     * ever had a deadline would glow red for the rest of the project.
     *
     * <p>⚠️ <strong>The dates still travel</strong> — only the verdict is withheld. What the commitment
     * was is worth reading beside when the work landed, and a report comparing the two needs both.
     */
    private static ScheduleState stateOf(
        Issue issue,
        LocalDate queuedFor,
        LocalDate redLine,
        LocalDate deadline,
        LocalDate today
    ) {
        if (issue.getResolutionId() != null) {
            return ScheduleState.NONE;
        }

        return ScheduleState.of(queuedFor, redLine, deadline, today);
    }

    /**
     * Whether there is a verdict to draw — what a badge asks before drawing itself.
     *
     * <p>⚠️ <strong>Reads {@code state}, so a finished issue counts as empty even when it carries
     * dates.</strong> That is the intended reading everywhere a badge or a protocol answer is concerned:
     * the schedule of completed work is history rather than something to act on. A caller that wants the
     * dates regardless reads them directly.
     */
    public boolean isEmpty() {
        return state == ScheduleState.NONE;
    }

}
