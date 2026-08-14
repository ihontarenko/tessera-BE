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

}
