package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The join tying an {@link Issue} to a {@link Version}, discriminated by {@link VersionLinkKind} into
 * the affects-version and fix-version associations (ticket 11). Unique per
 * {@code (issue, version, kind)}; the version must belong to the issue's project.
 */
@Entity
@Table(
    name = "issue_version",
    uniqueConstraints = @UniqueConstraint(name = "uq_issue_version", columnNames = {"issue_id", "version_id", "link_kind"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueVersion {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "version_id", nullable = false, length = 36)
    private String versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_kind", nullable = false, length = 16)
    private VersionLinkKind linkKind;

}
