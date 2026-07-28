package net.innoventa.tessera.dto.backlog;

import jakarta.validation.constraints.NotBlank;

/**
 * Every drag on the backlog screen, in one request: move {@code issueKey} into {@code targetSprintId}
 * (or to the product backlog when it is null), ranking it between the two visible neighbours
 * ({@code beforeIssueKey} orders strictly before the issue, {@code afterIssueKey} strictly after —
 * either may be omitted for an end-of-list drop).
 * <p>
 * Membership and rank change in <strong>one transaction</strong>, mirroring the board's move and for
 * the same reason: two calls would expose a state where an issue is committed but unplaced. A drop
 * that stays in the same list is a rank-only reorder and needs {@code EDIT_ISSUE}; one that changes
 * lists needs {@code MANAGE_SPRINT}.
 */
public record BacklogMoveRequest(
    @NotBlank String issueKey,
    String targetSprintId,
    String beforeIssueKey,
    String afterIssueKey
) {
}
