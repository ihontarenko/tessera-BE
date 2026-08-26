package net.innoventa.tessera.repository;

import java.time.LocalDateTime;

/**
 * Something happening to an issue, and what that issue was worth.
 *
 * <p>Deliberately says neither <em>what</em> happened nor <em>when</em> in its name: the same shape is
 * an issue being raised and an issue being resolved, and the query that produced it is what decides
 * which. Two records differing only in the word in front of {@code at} would be two places to change
 * the day somebody adds a third measure.
 *
 * <p>⚠️ <strong>One query per side, feeding two charts each.</strong> The flow chart counts these rows
 * and the backlog-weight chart sums their estimates — the same population asked about twice. Reading
 * it from the database twice would be a second round trip that could disagree with the first, since
 * somebody may raise or resolve something in between.
 *
 * <p>⚠️ <strong>{@code storyPoints} is null far more often than it is zero, and the difference is the
 * whole point.</strong> Null means nobody estimated the work; zero means somebody estimated it at
 * nothing. Summing them together as zero is arithmetically identical and editorially a lie — it reports
 * a team that does not estimate as a team that delivered nothing — which is why the weight chart counts
 * the nulls and says how many there were.
 */
public record IssueMoment(LocalDateTime at, Double storyPoints) {

    /** Whether anybody put a number on this work. */
    public boolean estimated() {
        return storyPoints != null;
    }
}
