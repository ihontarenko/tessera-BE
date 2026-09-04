package net.innoventa.tessera.domain;

import java.time.LocalDate;

/**
 * How pressing an issue is <em>today</em>, derived from its three schedule dates and never stored.
 *
 * <h2>⚠️ One word, so that a screen and a client cannot disagree about it</h2>
 *
 * <p>An issue carries {@code queuedFor}, {@code redLine} and {@code deadline}. Three dates compared
 * against today is six comparisons and an order of precedence, and every reader who needs "is this
 * urgent" would write that comparison out again: the board card, the list row, the rail, the protocol.
 * They would drift — and the way this particular drift shows is a card painted amber beside a client
 * that calls the same issue overdue.
 *
 * <p>So the precedence is decided once, here, and everything downstream reads a word. A colour is a
 * lookup on it, an agent's answer is the same word, and adding a state means touching one file.
 *
 * <h2>⚠️ The order below IS the precedence, most pressing last</h2>
 *
 * <p>A commitment outranks a plan: an issue queued for tomorrow whose deadline was yesterday is
 * {@link #OVERDUE}, not {@link #SCHEDULED}. Anything else would let somebody hide a missed deadline by
 * pushing their own queue date forward, which is exactly the reassurance a schedule must not offer.
 */
public enum ScheduleState {

    /** No date of any kind — the issue is in the backlog and nothing has been said about when. */
    NONE,

    /** Every date it has is still ahead: planned, but not yet anybody's problem. */
    SCHEDULED,

    /** Queued for today or a day already past — this is the "up next" pile. */
    QUEUED,

    /** Past the red line, with the deadline still ahead. The warning, deliberately set early. */
    RED_LINE,

    /** Due today. Separate from {@link #OVERDUE} because it is still winnable. */
    DUE_TODAY,

    /** The deadline is behind us. */
    OVERDUE;

    /**
     * Which of these an issue is in on a given day.
     *
     * <p>⚠️ {@code today} is a parameter rather than a call to {@link LocalDate#now()} inside: a board
     * renders hundreds of cards from one request, and every one of them must be judged against the same
     * day — a render that straddles midnight would otherwise paint two cards differently for no reason
     * anybody could reproduce. It also makes this the one piece of the schedule that is trivially
     * testable.
     */
    public static ScheduleState of(LocalDate queuedFor, LocalDate redLine, LocalDate deadline, LocalDate today) {
        if (deadline != null && deadline.isBefore(today)) {
            return OVERDUE;
        }

        if (deadline != null && deadline.isEqual(today)) {
            return DUE_TODAY;
        }

        if (redLine != null && !redLine.isAfter(today)) {
            return RED_LINE;
        }

        if (queuedFor != null && !queuedFor.isAfter(today)) {
            return QUEUED;
        }

        if (queuedFor != null || redLine != null || deadline != null) {
            return SCHEDULED;
        }

        return NONE;
    }

}
