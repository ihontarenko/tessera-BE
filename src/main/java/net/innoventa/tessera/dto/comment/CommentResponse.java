package net.innoventa.tessera.dto.comment;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * A comment (ticket 13). {@code editable} tells the UI whether the current caller may edit/delete it
 * (their own comment, or a project administrator), so it can show or hide the controls.
 */
public record CommentResponse(
    String id,
    MemberSummary author,
    String body,
    boolean editable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
