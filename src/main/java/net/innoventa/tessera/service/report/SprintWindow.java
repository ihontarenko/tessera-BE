package net.innoventa.tessera.service.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The span a sprint is reported over. All three are required: a sprint that never started has no report
 * to draw, and the endpoint refuses one rather than inventing an axis.
 *
 * @param startedAt when the sprint actually began, stamped server-side at the start
 * @param endDate   the day it was planned to end — the burndown's x-axis, drawn in full even when the
 *                  sprint closed before reaching it
 * @param closedAt  the moment the sprint stopped accumulating history: {@code completedAt} for a closed
 *                  sprint, and the current instant for one still running. Everything the report calls
 *                  "by the close" is measured against this, which is the whole reason a running sprint
 *                  and a finished one need no separate code path.
 */
public record SprintWindow(LocalDateTime startedAt, LocalDate endDate, LocalDateTime closedAt) {
}
