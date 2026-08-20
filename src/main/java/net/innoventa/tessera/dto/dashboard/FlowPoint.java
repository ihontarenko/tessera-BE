package net.innoventa.tessera.dto.dashboard;

import java.time.LocalDate;

/**
 * One day, from both sides: what was raised and what was resolved.
 *
 * <h2>⚠️ Two series, ONE axis</h2>
 *
 * <p>Both are counts of issues, so they belong on the same scale and are directly comparable — which is
 * the whole point. The day this gains a third measure in different units (hours, points, people), it
 * becomes a second chart rather than a second y-axis: two scales on one plot make two lines cross
 * wherever the author chose, and the reader has no way to know that.
 *
 * <p>The comparison is what neither number gives alone. "Twelve raised" is activity; "twelve raised and
 * three resolved" is a backlog growing, and that is a fact somebody acts on.
 *
 * @param date     the day
 * @param created  issues raised that day
 * @param resolved issues that gained a resolution that day — not the same issues, and deliberately not
 *                 matched up: pairing them would answer a question about individual issues (how long did
 *                 this take) that the ageing chart answers properly
 */
public record FlowPoint(LocalDate date, long created, long resolved) {
}
