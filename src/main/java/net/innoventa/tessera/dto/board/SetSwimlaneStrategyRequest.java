package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotNull;
import net.innoventa.tessera.domain.SwimlaneStrategy;

/** How the board groups into horizontal lanes (Phase-2 ticket 04) — {@code NONE} is the flat board. */
public record SetSwimlaneStrategyRequest(@NotNull SwimlaneStrategy strategy) {
}
