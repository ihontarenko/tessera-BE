package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, String> {

    List<Sprint> findByProjectIdOrderByCreatedAtAsc(String projectId);

    List<Sprint> findByProjectIdAndStateOrderByCreatedAtAsc(String projectId, SprintState state);

    /** The project's running sprint — the "at most one ACTIVE per project" invariant's read side. */
    Optional<Sprint> findFirstByProjectIdAndState(String projectId, SprintState state);

    long countByProjectId(String projectId);

}
