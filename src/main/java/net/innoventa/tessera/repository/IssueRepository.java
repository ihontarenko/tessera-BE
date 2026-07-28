package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, String> {

    List<Issue> findByProjectIdOrderByRankAsc(String projectId);

    Optional<Issue> findByIssueKey(String issueKey);

    List<Issue> findByParentIdOrderByRankAsc(String parentId);

    boolean existsByParentId(String parentId);

    /** The current maximum rank in a project, so a newly-created issue can be appended after it. */
    Optional<Issue> findFirstByProjectIdOrderByRankDesc(String projectId);

}
