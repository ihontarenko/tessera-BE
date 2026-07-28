package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, String> {

    List<Sprint> findByProjectIdOrderByCreatedAtAsc(String projectId);

    List<Sprint> findByProjectIdAndStateOrderByCreatedAtAsc(String projectId, SprintState state);

    /**
     * Velocity's series, oldest first — ordered by when each sprint actually ran rather than when it was
     * created, since sprints are often planned out of the order they end up being started in. A closed
     * sprint always has a {@code startedAt}: closing requires running, and running requires starting.
     */
    List<Sprint> findByProjectIdAndStateOrderByStartedAtAsc(String projectId, SprintState state);

    /** The project's running sprint — the "at most one ACTIVE per project" invariant's read side. */
    Optional<Sprint> findFirstByProjectIdAndState(String projectId, SprintState state);

    long countByProjectId(String projectId);

}
