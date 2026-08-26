package net.innoventa.tessera.dto.dashboard;

import java.util.List;

/**
 * What the dashboard draws, in one read.
 *
 * <h2>⚠️ Scoped to the caller by construction</h2>
 *
 * <p>Every number here is computed over the projects this member may browse and no others. That is not
 * a filter applied to a total — the totals are never computed — because an aggregate is exactly the
 * shape in which a tracker leaks: "43 issues" across an installation somebody can see three projects of
 * discloses the other forty without naming one of them.
 *
 * <h2>Six questions, and each chart answers exactly one</h2>
 *
 * <ul>
 *   <li><strong>Is the backlog growing?</strong> — {@code flowPerDay}, raised against resolved.
 *   <li><strong>Is the backlog growing in WEIGHT?</strong> — {@code weightPerDay}, the same week in the
 *       team's estimate units rather than in issues. It can disagree with the one above, and when it
 *       does, that disagreement is the finding: twelve issues in and twelve out is a week that broke
 *       even by count and may have doubled the backlog by weight.
 *   <li><strong>What moved?</strong> — {@code movedInto}, counted as moves rather than as issues.
 *   <li><strong>What is on the boards now?</strong> — {@code standing}, counted as issues.
 *   <li><strong>What KIND of work is it?</strong> — {@code byType}. Standing says where the open work
 *       sits; this says what it is, and no arrangement of statuses can show it.
 *   <li><strong>What is stuck?</strong> — {@code ageing}, days in the current status.
 *   <li><strong>What cannot move at all?</strong> — {@code blocked}, on the engine's own definition.
 * </ul>
 *
 * <p>Kept apart on purpose. Movement and standing are different questions, and answering one while
 * labelling it the other produces a chart that cannot change during a busy week that happens to end
 * where it started. {@code movedInto} and {@code standing} are that pair stated in full, which is why
 * they carry the same shape and separate names.
 *
 * <h2>One endpoint, and why it is not the search</h2>
 *
 * <p>The dashboard was built deliberately without an aggregate endpoint — it pointed at other screens
 * and read what those screens read. Charts are a different job: drawing a week of daily counts from the
 * search would mean fetching every issue raised that week to count them in the browser, and a progress
 * meter per project would mean fetching every issue in every project. Counting belongs where the rows
 * are.
 *
 * @param createdToday     issues raised since midnight, in the caller's projects
 * @param createdInWindow  issues raised inside the window
 * @param resolvedInWindow issues that gained a resolution inside the window
 * @param flowPerDay       one row per day of the window, oldest first, zeros included
 * @param estimatedCreatedInWindow  how many of {@code createdInWindow} carried an estimate
 * @param estimatedResolvedInWindow how many of {@code resolvedInWindow} carried an estimate — ⚠️ these
 *                         two together are the denominator the weight figures have to be read against.
 *                         Every weight on this screen is a number about the estimated issues only, and
 *                         without the fraction beside it a team that estimates half its work reads as a
 *                         team that did half as much
 * @param raisedPointsToday    estimate raised since midnight
 * @param deliveredPointsToday estimate finished since midnight — the headline is the difference, which
 *                         is what "today" did to the backlog's weight
 * @param weightPerDay     one row per day of the window, oldest first, zeros included, both sides
 *                         positive
 * @param movedInto        which statuses issues entered during the window, busiest first
 * @param standing         how many issues sit in each status right now, busiest first — open and
 *                         unarchived only, so the counts sum to {@code openTotal}
 * @param byType           how many open issues are of each kind, busiest first — the same population
 *                         as {@code standing}, so these sum to {@code openTotal} too. ⚠️ Unlike
 *                         {@code standing} it carries no zero rows; {@link TypeStanding} says why
 * @param projects         one row per browsable project, whether or not it holds any issues
 * @param ageing           the longest-sitting open issues, oldest first — capped, see {@code openTotal}
 * @param openTotal        how many open issues there are in total, so a capped chart can say so rather
 *                         than reading as the whole picture
 * @param blocked          issues that cannot start, longest-held first — capped the same way
 * @param blockedTotal     how many are blocked in total
 * @param days             the window actually used, so the screen can label itself honestly rather than
 *                         repeating what it asked for
 */
public record DashboardSummary(
    long createdToday,
    long createdInWindow,
    long resolvedInWindow,
    List<FlowPoint> flowPerDay,
    long estimatedCreatedInWindow,
    long estimatedResolvedInWindow,
    double raisedPointsToday,
    double deliveredPointsToday,
    List<WeightPoint> weightPerDay,
    List<StatusMovement> movedInto,
    List<StatusStanding> standing,
    List<TypeStanding> byType,
    List<ProjectProgress> projects,
    List<AgeingIssue> ageing,
    long openTotal,
    List<BlockedIssue> blocked,
    long blockedTotal,
    int days
) {
}
