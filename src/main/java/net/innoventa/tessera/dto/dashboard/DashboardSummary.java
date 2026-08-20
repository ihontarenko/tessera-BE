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
 * <h2>Four questions, and each chart answers exactly one</h2>
 *
 * <ul>
 *   <li><strong>Is the backlog growing?</strong> — {@code flowPerDay}, raised against resolved.
 *   <li><strong>What moved?</strong> — {@code movedInto}, counted as moves rather than as issues.
 *   <li><strong>What is stuck?</strong> — {@code ageing}, days in the current status.
 *   <li><strong>What cannot move at all?</strong> — {@code blocked}, on the engine's own definition.
 * </ul>
 *
 * <p>Kept apart on purpose. Movement and standing are different questions, and answering one while
 * labelling it the other produces a chart that cannot change during a busy week that happens to end
 * where it started.
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
 * @param movedInto        which statuses issues entered during the window, busiest first
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
    List<StatusMovement> movedInto,
    List<ProjectProgress> projects,
    List<AgeingIssue> ageing,
    long openTotal,
    List<BlockedIssue> blocked,
    long blockedTotal,
    int days
) {
}
