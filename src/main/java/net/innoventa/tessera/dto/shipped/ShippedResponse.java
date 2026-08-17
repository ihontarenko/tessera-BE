package net.innoventa.tessera.dto.shipped;

import java.util.List;

/**
 * A project's finished work, sliced by time (TSSR-4) — the Shipped screen in one read.
 *
 * <p>{@code groupedBySprint} says which slicing the groups below carry, so the interface does not have
 * to re-derive it from the board's scope strategy and risk labelling a month as a sprint. A project that
 * plans in sprints is grouped by the sprint each issue was committed to; every other project by the
 * month it was resolved in.
 */
public record ShippedResponse(
    String projectId,
    boolean groupedBySprint,
    /** Newest first — the group somebody opens this screen to read is the one at the top. */
    List<ShippedGroupView> groups,
    /** How many of the issues below are archived, so the screen can say so without counting them again. */
    int archivedIssues
) {
}
