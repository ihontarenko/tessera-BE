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
    /** What it is about, where somebody said — null for an ordinary remark (TSSR-25). */
    CommentTopicSummary topic,
    /**
     * The comment this one answers, or null when it stands on its own (TSSR-26).
     *
     * ⚠️ Flat, not a nested {@code replies} array. One shape, no recursion in the payload, and the
     * client groups — the same reasoning {@code PageBlockView} sets out. A nested response would also
     * turn "edit this reply" into a search through a tree.
     */
    String parentCommentId,
    String body,
    boolean editable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
