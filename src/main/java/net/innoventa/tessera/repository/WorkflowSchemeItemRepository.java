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

}
