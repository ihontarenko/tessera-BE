package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.WorkflowSchemeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowSchemeItemRepository extends JpaRepository<WorkflowSchemeItem, String> {

    List<WorkflowSchemeItem> findBySchemeId(String schemeId);

    List<WorkflowSchemeItem> findBySchemeIdIn(List<String> schemeIds);

    /** The per-type workflow override for a scheme, if any; absent means fall back to the default. */
    Optional<WorkflowSchemeItem> findBySchemeIdAndIssueTypeId(String schemeId, String issueTypeId);

    /** Every per-type override pointing at this workflow — half of what refuses the workflow's deletion. */
    List<WorkflowSchemeItem> findByWorkflowId(String workflowId);

    /** Every per-type override naming this issue type — part of what refuses the type's deletion. */
    List<WorkflowSchemeItem> findByIssueTypeId(String issueTypeId);

}
