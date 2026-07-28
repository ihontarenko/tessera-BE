package net.innoventa.tessera.dto.backlog;

import net.innoventa.tessera.domain.BoardScopeStrategy;

import java.util.List;

/**
 * The whole backlog screen in one read: the running sprint's panel (absent when none is running), a
 * panel per planned sprint, and the product backlog. Every panel carries its own issues, count and
 * story-point total, so the screen renders from a single request and the client sums nothing.
 * <p>
 * {@code scopeStrategy} rides along because it is what decides the screen exists at all (ADR-0012) —
 * a client that finds {@code ALL_ISSUES} here knows the project does not plan in sprints without
 * having to ask the board.
 */
public record BacklogResponse(
    String projectId,
    BoardScopeStrategy scopeStrategy,
    BacklogPanelView activeSprint,
    List<BacklogPanelView> futureSprints,
    BacklogPanelView backlog
) {
}
