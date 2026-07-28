package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueVersion;
import net.innoventa.tessera.domain.VersionLinkKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueVersionRepository extends JpaRepository<IssueVersion, String> {

    List<IssueVersion> findByIssueId(String issueId);

    List<IssueVersion> findByIssueIdAndLinkKind(String issueId, VersionLinkKind linkKind);

    List<IssueVersion> findByVersionId(String versionId);

    void deleteByIssueId(String issueId);

}
