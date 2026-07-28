package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, String> {

    List<IssueLabel> findByIssueId(String issueId);

    /** Batched for the board's filter view — one query for a whole slice instead of one per issue. */
    List<IssueLabel> findByIssueIdIn(Collection<String> issueIds);

    void deleteByIssueId(String issueId);

}
