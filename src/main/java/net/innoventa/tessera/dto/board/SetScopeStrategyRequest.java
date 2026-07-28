package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotNull;
import net.innoventa.tessera.domain.BoardScopeStrategy;

/**
 * Which issues the board draws from (Phase-3 ticket 08) — and, by extension, whether this project does
 * Scrum at all (ADR-0012). {@code ALL_ISSUES} is the whole project; {@code ACTIVE_SPRINT} is the running
 * sprint, and brings the Backlog and Reports views with it.
 */
public record SetScopeStrategyRequest(@NotNull BoardScopeStrategy strategy) {
}
