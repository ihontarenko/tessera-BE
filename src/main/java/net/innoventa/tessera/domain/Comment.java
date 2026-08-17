package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A member's remark on an {@link Issue}. A separate first-class entity, deliberately <strong>not</strong>
 * an {@link ActivityLog} item (ADR-0007): a comment is discussion, not a field change. Carries its
 * author and created/updated timestamps; a member edits or deletes their own.
 *
 * <p>⚠️ <strong>One level of reply, and only one</strong> (TSSR-26). This was a flat list with no
 * threading at all (ticket 13); that decision was reversed, but only halfway and on purpose. What it was
 * protecting against is real — an issue tracker is not a forum, and unbounded nesting produces a shape
 * nobody can scan or quote from — so a reply answers a top-level comment and cannot itself be answered.
 * The depth cap is enforced in {@code CommentService}, not by convention.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Comment {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "author_member_id", nullable = false, length = 36)
    private String authorMemberId;

    @Column(nullable = false, length = 4000)
    private String body;

    /**
     * What this comment is about, where somebody said — null for an ordinary remark.
     *
     * <p>⚠️ <strong>Optional, and it stays optional</strong> (see {@link CommentTopic}). It makes a long
     * thread scannable; it does not reclassify the comment, which is still discussion rather than a
     * field change (ADR-0007).
     */
    @Column(name = "topic_id", length = 36)
    private String topicId;

    /**
     * The comment this one answers, or null when it stands on its own (TSSR-26).
     *
     * <p>⚠️ <strong>The comment it names must have no parent of its own</strong>, and must be on the same
     * issue. Both are checked in {@code CommentService.add}; neither can be expressed as a column
     * constraint, and a reply that quietly became a cross-issue link would be the worse of the two
     * failures.
     */
    @Column(name = "parent_comment_id", length = 36)
    private String parentCommentId;

    /**
     * Which agent wrote it, where one did — null for a person at the keyboard.
     *
     * <p>⚠️ <strong>Beside the author, never instead of it.</strong> The comment is still the person's:
     * they asked for it, they are answerable for it, and it belongs in everything that lists their
     * comments. This says how it got written.
     *
     * <p>⚠️ <strong>The name is a snapshot and carries no foreign key</strong> — see
     * {@link ActivityLog#getAgentName()} for the reasoning, which is the same.
     */
    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(name = "agent_name", length = 128)
    private String agentName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
