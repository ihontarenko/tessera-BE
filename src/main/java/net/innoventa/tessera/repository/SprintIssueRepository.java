package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.SprintIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprintIssueRepository extends JpaRepository<SprintIssue, String> {

    List<SprintIssue> findBySprintId(String sprintId);

    /** Every membership across a set of sprints, for the whole-screen backlog read in one query. */
    List<SprintIssue> findBySprintIdIn(Collection<String> sprintIds);

    List<SprintIssue> findByIssueId(String issueId);

    /** The row {@code (sprint, issue)} identity addresses — the one a re-add revives. */
    Optional<SprintIssue> findBySprintIdAndIssueId(String sprintId, String issueId);

}
