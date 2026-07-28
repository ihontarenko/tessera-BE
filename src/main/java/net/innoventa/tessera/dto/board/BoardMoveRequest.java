package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotBlank;

/**
 * Drag-and-drop a card (Phase-2 ticket 02): move {@code issueKey} to {@code targetColumnId}, ranking it
 * between the two named visible neighbours ({@code beforeIssueKey} orders strictly before the card,
 * {@code afterIssueKey} strictly after — either may be omitted for an end-of-list drop). A target column
 * equal to the card's current column is a rank-only reorder ({@code EDIT_ISSUE}); a different column also
 * transitions the issue through the workflow ({@code TRANSITION_ISSUE}), and {@code resolutionId} is
 * required only when that transition lands in a Done-category status.
 */
public record BoardMoveRequest(
    @NotBlank String issueKey,
    @NotBlank String targetColumnId,
    String beforeIssueKey,
    String afterIssueKey,
    String resolutionId
) {
}
