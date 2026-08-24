package net.innoventa.tessera.dto.dashboard;

import net.innoventa.tessera.domain.StatusCategory;

/**
 * How many issues are sitting in one status <em>right now</em>.
 *
 * <p>⚠️ <strong>Standing, not movement — the other half of {@link StatusMovement}.</strong> "Twelve
 * entered review this week" is a fact about the week and can be large while nothing is in review at
 * all; "twelve are in review" is a fact about this moment and says what the week has left behind. The
 * two charts sit side by side precisely because neither can be read off the other.
 *
 * <p>⚠️ <strong>An issue is counted once, in exactly one status.</strong> Movement counts moves, so an
 * issue that bounced back into review twice is two there; here it is wherever it is, and the counts sum
 * to the number of open issues rather than to anything larger.
 *
 * <p>⚠️ <strong>Only what is actually on a board is counted</strong> — unresolved and unarchived.
 * Including finished work would put every issue the installation has ever closed into one bar and make
 * the rest unreadable, which is the whole reason the board does not show it either.
 *
 * @param status   the status as the catalogue names it today — unlike {@link StatusMovement}, this is
 *                 read from the status an issue currently holds, so it is never a name the catalogue
 *                 has lost
 * @param category the bucket it belongs to, or null for an issue holding a status that has since been
 *                 deleted out from under it
 */
public record StatusStanding(String status, StatusCategory category, long count) {
}
