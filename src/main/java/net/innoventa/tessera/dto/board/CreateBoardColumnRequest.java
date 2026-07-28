package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotBlank;

/**
 * Add a column to a board (Phase-2 ticket 03, {@code ADMINISTER_PROJECT}). {@code position} is where the
 * new column is inserted (existing columns shift right); omitted or out-of-range appends at the end. A
 * new column starts with no {@code fallbackForCategory} and no explicit status mappings — assigning it
 * a category's fallback role is a separate, invariant-checked call ({@code PUT .../fallback}).
 */
public record CreateBoardColumnRequest(
    @NotBlank String name,
    Integer position,
    Integer minIssues,
    Integer maxIssues
) {
}
