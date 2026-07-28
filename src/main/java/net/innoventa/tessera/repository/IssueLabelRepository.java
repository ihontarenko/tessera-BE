package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, String> {

    List<IssueLabel> findByIssueId(String issueId);

    void deleteByIssueId(String issueId);

}
