package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.Min;

/**
 * How many days a completed issue stays on the board (Phase-2 ticket 06). {@code null} keeps completed
 * issues forever; {@code 0} drops one as soon as it is done.
 */
public record SetDoneThresholdRequest(@Min(0) Integer days) {
}
