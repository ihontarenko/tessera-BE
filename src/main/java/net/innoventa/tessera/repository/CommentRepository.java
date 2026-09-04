package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String>, JpaSpecificationExecutor<Comment> {

    List<Comment> findByIssueIdOrderByCreatedAtAsc(String issueId);

    /**
     * Every comment on any of these issues, in one read (TSSR-156).
     *
     * <p>⚠️ <strong>For the relevance search, which weighs a whole thread as one field.</strong> Asking
     * per issue is the same query four hundred times on the one screen that has the most rows — the
     * shape of every N+1 there has ever been, and invisible until somebody profiles it.
     */
    List<Comment> findByIssueIdInOrderByCreatedAtAsc(Collection<String> issueIds);

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
