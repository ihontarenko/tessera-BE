package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    List<Comment> findByIssueIdOrderByCreatedAtAsc(String issueId);

    long countByTopicId(String topicId);

    /** The answers to one comment, oldest first — a thread reads forwards even where the stream does not. */
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(String parentCommentId);

    /** How many go with it, said before a parent is deleted rather than after (TSSR-26). */
    long countByParentCommentId(String parentCommentId);

    /**
     * Every comment topic's usage in one query — the Administration page shows a count beside each.
     *
     * <p>⚠️ Untopiced comments are excluded rather than grouped under a null key. Most comments have no
     * topic, so that bucket would be the largest number on the screen and a fact about no row at all.
     */
    @Query("select new net.innoventa.tessera.repository.CountByKey(comment.topicId, count(comment)) "
           + "from Comment comment where comment.topicId is not null group by comment.topicId")
    List<CountByKey> countCommentsByTopic();

}
