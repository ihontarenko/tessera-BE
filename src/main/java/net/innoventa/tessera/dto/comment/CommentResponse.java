package net.innoventa.tessera.dto.comment;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * A comment (ticket 13). {@code editable} tells the UI whether the current caller may edit/delete it
 * (their own comment, or a project administrator), so it can show or hide the controls.
 *
 * <p>{@code agentName} is the name of the agent that wrote it, where one did, and null where a person
 * did. ⚠️ It sits <em>beside</em> the author and never replaces one: the comment is still theirs, and a
 * response that swapped the person out for the tool would be answering a different question than the one
 * anybody asked.
 */
public record CommentResponse(
    String id,
    MemberSummary author,
    String agentName,
    String body,
    boolean editable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
