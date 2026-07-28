package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotBlank;

/**
 * Rename a column and set its soft WIP limits (Phase-2 ticket 03, {@code ADMINISTER_PROJECT}). The
 * limits are stored and returned as-is; enforcement is purely visual on the client. Deliberately excludes
 * {@code fallbackForCategory} — changing which column backs a category is an invariant-checked swap, kept
 * behind its own endpoint ({@code PUT .../fallback}) rather than folded into this general-purpose edit.
 */
public record UpdateBoardColumnRequest(
    @NotBlank String name,
    Integer minIssues,
    Integer maxIssues
) {
}
