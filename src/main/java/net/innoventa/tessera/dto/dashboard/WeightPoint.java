package net.innoventa.tessera.dto.dashboard;

import java.time.LocalDate;

/**
 * One day weighed from both sides — estimate arriving against estimate leaving.
 *
 * <h2>⚠️ Its own chart, not a third series on the flow</h2>
 *
 * <p>{@link FlowPoint} carries the same day counted in issues. This is the same day measured in the
 * team's estimate units, and the two are not interchangeable: twelve issues raised and twelve resolved
 * is a week that broke even by count and may have doubled the backlog by weight. Putting a count and a
 * weight on one axis invites the reader to compare them, and a second y-axis makes it worse — two
 * scales cross wherever the author put them, and nothing on the plot says so.
 *
 * <h2>⚠️ Both sides, because a delivered figure alone answers nothing</h2>
 *
 * <p>An earlier shape here plotted only what was delivered, as a running total. It was rejected in
 * review for the reason that ought to have been obvious when it was designed: <em>a cumulative line
 * only ever goes up</em>. It has no reference, so a good week and a bad one differ by a slope the eye
 * does not read, and the honest answer to "what does this show" was "not much". A number means
 * something only against another number. This is that other number: raised weight is what the same
 * week put in, and the balance between the two is the entire content of the chart.
 *
 * <p>⚠️ <strong>Both are POSITIVE here.</strong> The chart draws one of them downward, which is a
 * rendering choice about where zero sits, not a fact about the day — a stored negative would leak that
 * choice into every other consumer of this record.
 *
 * @param date      the day
 * @param raised    the estimate of the issues raised that day
 * @param delivered the estimate of the issues resolved that day — ⚠️ <strong>both figures count only
 *                  issues that carried an estimate</strong>. An unestimated one contributes nothing
 *                  rather than a zero, and the summary says how many of those there were so neither
 *                  side is read as the whole of what happened
 */
public record WeightPoint(LocalDate date, double raised, double delivered) {
}
