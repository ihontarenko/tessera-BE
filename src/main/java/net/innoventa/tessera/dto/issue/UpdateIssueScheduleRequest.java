package net.innoventa.tessera.dto.issue;

import java.time.LocalDate;

/**
 * Set when an issue is meant to happen, in one call.
 *
 * <h2>⚠️ A full replacement of all three, not a delta</h2>
 *
 * <p>The client sends the state it wants to end up with, exactly as the labels request does. A missing
 * date therefore <em>clears</em> that date rather than leaving it alone — which is the only reading
 * available, since JSON gives an absent field and an explicit {@code null} the same value on the way in,
 * and a request shape that could not express "clear the deadline" would be a schedule nobody could
 * cancel.
 *
 * <p>⚠️ So anything changing one date reads the issue first and sends the other two back unchanged. That
 * is what the interface's rail does, and what the protocol's schedule action does.
 *
 * <p>Requires {@code EDIT_ISSUE} — saying when work happens is editing the issue, not a permission of
 * its own. A second switch would be a second thing to grant, revoke and forget, gating something anybody
 * who may edit the issue can already express by other means.
 */
public record UpdateIssueScheduleRequest(
    LocalDate queuedFor,
    LocalDate redLine,
    LocalDate deadline
) {
}
