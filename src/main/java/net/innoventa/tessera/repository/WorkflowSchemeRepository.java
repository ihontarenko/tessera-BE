package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.WorkflowScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowSchemeRepository extends JpaRepository<WorkflowScheme, String> {

    List<WorkflowScheme> findAllByOrderByNameAsc();

    /** Every scheme whose fallback is this workflow — half of what refuses the workflow's deletion. */
    List<WorkflowScheme> findByDefaultWorkflowId(String workflowId);

}
