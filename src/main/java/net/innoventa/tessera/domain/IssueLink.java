package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A typed relationship stored <strong>once</strong> with direction (ticket 12): source → target of a
 * {@link LinkType}. There is no duplicate reverse row — the target issue renders the type's inward
 * label from this same record. Unique per {@code (source, target, type)}.
 */
@Entity
@Table(
    name = "issue_links",
    uniqueConstraints = @UniqueConstraint(name = "uq_issue_link", columnNames = {"source_issue_id", "target_issue_id", "link_type_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueLink {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "source_issue_id", nullable = false, length = 36)
    private String sourceIssueId;

    @Column(name = "target_issue_id", nullable = false, length = 36)
    private String targetIssueId;

    @Column(name = "link_type_id", nullable = false, length = 36)
    private String linkTypeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
