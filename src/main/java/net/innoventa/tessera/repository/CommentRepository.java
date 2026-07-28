package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    List<Comment> findByIssueIdOrderByCreatedAtAsc(String issueId);

}
