package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.WorkflowScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowSchemeRepository extends JpaRepository<WorkflowScheme, String> {

    List<WorkflowScheme> findAllByOrderByNameAsc();

    /** Name collisions are a 409 with words rather than the unique constraint's 500 — see SchemeRules. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String schemeId);

    /** Every scheme whose fallback is this workflow — half of what refuses the workflow's deletion. */
    List<WorkflowScheme> findByDefaultWorkflowId(String workflowId);

}
