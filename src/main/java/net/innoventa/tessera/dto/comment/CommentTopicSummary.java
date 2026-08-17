package net.innoventa.tessera.dto.comment;

import net.innoventa.tessera.domain.CommentTopic;

/**
 * A comment's topic as the thread shows it — the name, resolved once on the server.
 *
 * <p>⚠️ <strong>The name travels, not just the id</strong>, for the same reason {@code StatusSummary}
 * carries one: the activity stream renders a label, and a bare id would make every screen that shows a
 * comment hold the whole catalog to turn it into a word.
 */
public record CommentTopicSummary(String id, String name, String iconKey, String color) {

    public static CommentTopicSummary from(CommentTopic commentTopic) {
        return commentTopic == null
            ? null
            : new CommentTopicSummary(
                commentTopic.getId(),
                commentTopic.getName(),
                commentTopic.getIconKey(),
                commentTopic.getColor());
    }

}
