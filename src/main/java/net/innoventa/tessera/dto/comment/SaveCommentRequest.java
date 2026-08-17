package net.innoventa.tessera.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add or edit a comment — its body, optionally what it is about, and optionally what it answers. Author
 * and timestamps are set by the service.
 *
 * <p>⚠️ <strong>One record for both create and edit, and the two fields behave differently under
 * it.</strong>
 *
 * <ul>
 *   <li>{@code topicId} is <strong>replaced</strong>, so an edit that omits it clears the topic. That is
 *       how everything in this product updates — a field given is a field replaced — but it is a trap
 *       for a client that only meant to change the words: the composer has to send the topic it is
 *       showing, every time.
 *   <li>{@code parentCommentId} is <strong>ignored on edit</strong> (TSSR-26). Re-parenting is not an
 *       edit anybody asked for, and refusing to model it removes a whole class of accident — including
 *       the one where rewording a reply quietly promotes it to the top of the thread.
 * </ul>
 */
public record SaveCommentRequest(
    @NotBlank @Size(max = 4000) String body,
    @Size(max = 36) String topicId,
    @Size(max = 36) String parentCommentId
) {

    /** For the callers that never reply — the MCP tool, and anything posting a top-level remark. */
    public SaveCommentRequest(String body, String topicId) {
        this(body, topicId, null);
    }

}
