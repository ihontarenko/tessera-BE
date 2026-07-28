package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueComponentRepository extends JpaRepository<IssueComponent, String> {

    List<IssueComponent> findByIssueId(String issueId);

    List<IssueComponent> findByComponentId(String componentId);

    void deleteByIssueId(String issueId);

}
