package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {

    boolean existsByKey(String key);

    List<Project> findByIdInOrderByKeyAsc(List<String> ids);

    /** Which projects a scheme is in force in — the "used by Innoventa, Moneta" panel, and the refusal. */
    List<Project> findByWorkflowSchemeIdInOrderByKeyAsc(Collection<String> workflowSchemeIds);

    /** The same question of the other scheme kind — see {@code ConfigurationUsage}. */
    List<Project> findByIssueTypeSchemeIdInOrderByKeyAsc(Collection<String> issueTypeSchemeIds);

    /** And of the third — ⚠️ singular, because a project points at one estimation scale or at none. */
    List<Project> findByEstimationSchemeIdOrderByKeyAsc(String estimationSchemeId);

}
