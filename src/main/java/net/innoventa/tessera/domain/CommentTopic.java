package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * What a comment is <em>about</em> — Cannot Reproduce, Code Review, Root Cause. Global catalog with
 * <strong>no scheme</strong> (ADR-0001), like {@link Resolution}: every project labels its discussion
 * from the same list.
 *
 * <p>⚠️ <strong>It says what a comment is about, never what it is.</strong> A comment is not an
 * activity-log entry (ADR-0007) and a topic does not change that — the stream already tells a comment
 * apart from a field change, and this sits inside the comment half of that distinction.
 *
 * <p>⚠️ <strong>Optional, and it stays optional.</strong> Most comments are simply comments. A topic
 * every comment had to carry would be a control people got past by picking a lie, which is worse than
 * an unlabelled thread — so a comment holds a nullable FK here, and an installation with no topics at
 * all is coherent rather than broken.
 */
@Entity
@Table(name = "comment_topics",
    uniqueConstraints = @UniqueConstraint(name = "uq_comment_topics_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CommentTopic {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Which drawing the client uses, from a closed list — see {@code CommentTopicIcons}.
     *
     * <p>⚠️ <strong>A key, not a colour and not a drawing.</strong> The server cannot hold React
     * components, so this names what the topic <em>is</em> and the client decides what that looks like.
     */
    @Column(name = "icon_key", length = 64)
    private String iconKey;

    /**
     * A CSS colour the topic is drawn in, or {@code null} for the muted default.
     *
     * <p>⚠️ <strong>Stored, not derived from {@link #iconKey}.</strong> An issue type's colour comes
     * from its icon key through one client-side map; a topic's does not, because a topic carries the
     * two independently — which follows {@code Status.color} (TSSR-21), the decision already taken about
     * a stored colour, rather than inventing a third arrangement.
     */
    @Column(length = 16)
    private String color;

}
