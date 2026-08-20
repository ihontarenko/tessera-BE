package net.innoventa.tessera.dto.dashboard;

import java.util.List;

/**
 * One issue that cannot move, and for how long.
 *
 * <h2>⚠️ The same definition the engine enforces, not a second one</h2>
 *
 * <p>Assembled from {@code IssueBlockers}, which is the single place that decides what "blocked" means —
 * an inward link whose type carries a blocking effect and whose far end is still open, at depth one, and
 * a warning is not a block. A dashboard that counted blocked issues its own way would disagree with the
 * board about the same card, which is precisely the failure that class exists to prevent.
 *
 * @param blockers ⚠️ keys only, never summaries. A blocker may sit in a project the reader cannot open;
 *                 a key is enough to go and ask about, a summary would be somebody else's backlog read
 *                 out to a stranger
 * @param days     since the earliest of those links was drawn — an issue held up by two things has been
 *                 held up since the first, so adding a second blocker does not restart the clock
 */
public record BlockedIssue(String issueKey, String summary, List<String> blockers, long days) {
}
