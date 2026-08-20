package net.innoventa.tessera.dto.dashboard;

import net.innoventa.tessera.domain.StatusCategory;

/**
 * How many issues moved <em>into</em> one status inside the window.
 *
 * <p>⚠️ <strong>Movement, not standing.</strong> "Twelve issues are in review" and "twelve issues
 * entered review this week" are different sentences, and only the second says anything about the week.
 * An issue that entered a status twice is counted twice, because it moved twice.
 *
 * @param status   the status as the activity log recorded it — see {@code DashboardService} for why
 *                 that is a name rather than an identifier, and what a rename costs
 * @param category the bucket it belongs to today, or null where no status carries that name any more
 */
public record StatusMovement(String status, StatusCategory category, long count) {
}
