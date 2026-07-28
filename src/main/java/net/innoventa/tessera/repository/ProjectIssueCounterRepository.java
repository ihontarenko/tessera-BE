package net.innoventa.tessera.repository;

import jakarta.persistence.LockModeType;
import net.innoventa.tessera.domain.ProjectIssueCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectIssueCounterRepository extends JpaRepository<ProjectIssueCounter, String> {

    /**
     * Read the counter row under a {@code SELECT … FOR UPDATE} write lock (portable across all three
     * dialects) so concurrent issue creation in the same project serialises on it — the heart of the
     * collision-free key allocation (ADR-0003).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT counter FROM ProjectIssueCounter counter WHERE counter.projectId = :projectId")
    Optional<ProjectIssueCounter> findByProjectIdForUpdate(@Param("projectId") String projectId);

}
