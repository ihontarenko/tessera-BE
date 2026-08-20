package net.innoventa.tessera.dto.dashboard;

import net.innoventa.tessera.domain.StatusCategory;

/**
 * One open issue and how long it has sat where it is.
 *
 * <h2>⚠️ The number a board cannot show</h2>
 *
 * <p>A card looks identical on its first day in a column and on its fortieth. "What is stuck" is the
 * question a standup actually asks, and it is the one question a board is structurally unable to answer
 * — so this is not a prettier version of the board, it is the missing half of it.
 *
 * @param days ⚠️ days in the CURRENT status, not the issue's age. An issue raised in March and moved
 *             yesterday is one day old here, and correctly so: the clock is on the sit, not on the work
 */
public record AgeingIssue(
    String issueKey,
    String summary,
    String status,
    StatusCategory category,
    long days
) {
}
