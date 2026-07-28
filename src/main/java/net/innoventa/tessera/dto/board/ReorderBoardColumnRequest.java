package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Move a column to a new zero-based {@code position}; the board's other columns shift to keep the
 *  ordering compact (Phase-2 ticket 03, {@code ADMINISTER_PROJECT}). */
public record ReorderBoardColumnRequest(
    @NotNull @PositiveOrZero Integer position
) {
}
