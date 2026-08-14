package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.SprintIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * How many issues of a type are committed to a sprint — what an administrator has to see before
     * moving that type off hierarchy level 0, since only level 0 is what a sprint plans (ADR-0014).
     */
    @Query("select count(commitment) from SprintIssue commitment, Issue issue "
           + "where issue.id = commitment.issueId and issue.issueTypeId = :issueTypeId")
    long countCommitmentsOfIssueType(@Param("issueTypeId") String issueTypeId);

}
