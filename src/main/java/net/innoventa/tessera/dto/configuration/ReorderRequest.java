package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The whole order, never a move.
 *
 * <p>⚠️ <strong>Every id, in the order they should end up in.</strong> "Move this one to position 2" is
 * a smaller payload and a worse contract: two administrators reordering at once each apply a move to a
 * list the other has already changed, and the result is an order neither asked for. A whole list is
 * last-write-wins, which is at least an order somebody chose.
 *
 * <p>A list missing a row, or naming one twice, is refused rather than patched — a partial order is a
 * request that has lost something on the way, not an instruction.
 */
public record ReorderRequest(@NotEmpty List<String> orderedIds) {
}
