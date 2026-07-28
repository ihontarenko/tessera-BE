package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueLinkRepository extends JpaRepository<IssueLink, String> {

    List<IssueLink> findBySourceIssueId(String sourceIssueId);

    List<IssueLink> findByTargetIssueId(String targetIssueId);

    boolean existsBySourceIssueIdAndTargetIssueIdAndLinkTypeId(String sourceIssueId, String targetIssueId, String linkTypeId);

}
