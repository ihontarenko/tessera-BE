package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IssueComponentRepository extends JpaRepository<IssueComponent, String> {

    List<IssueComponent> findByIssueId(String issueId);

    /** Batched for the board's filter view — one query for a whole slice instead of one per issue. */
    List<IssueComponent> findByIssueIdIn(Collection<String> issueIds);

    List<IssueComponent> findByComponentId(String componentId);

    void deleteByIssueId(String issueId);

}
