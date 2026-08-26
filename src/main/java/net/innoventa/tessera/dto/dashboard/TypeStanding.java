package net.innoventa.tessera.dto.dashboard;

/**
 * How many open issues are of one kind — what the boards are actually made of.
 *
 * <h2>⚠️ A different question from {@link StatusStanding}, on the same population</h2>
 *
 * <p>Standing says <em>where</em> the open work sits; this says <em>what</em> it is. A hundred issues
 * spread evenly across To Do and In Review is one picture; a hundred issues of which seventy are bugs
 * is a different one, and no arrangement of statuses can show it.
 *
 * <h2>⚠️ Only the kinds something is actually of</h2>
 *
 * <p>{@code StatusStanding} reports empty statuses as zeros on purpose — "nothing is in review" is
 * worth saying. This deliberately does not, and the difference is the catalogue: statuses are the few a
 * project moves work through, while the issue-type catalogue is global and holds every kind any project
 * ever configured. Zero-filling it would print a dozen rows reading {@code Milestone 0}, {@code Spike
 * 0}, {@code Risk 0} for kinds this installation has never once raised — noise that buries the rows
 * that mean something.
 *
 * @param type    the type's name as the catalogue holds it
 * @param iconKey the key the interface draws it by — ⚠️ carried rather than resolved to a colour here,
 *                because what a Bug looks like is one decision and it belongs on the side that draws
 *                it. Null for a type with no icon configured, which the interface has a fallback for
 * @param count   open, unarchived issues of that type in the caller's projects
 */
public record TypeStanding(String type, String iconKey, long count) {
}
