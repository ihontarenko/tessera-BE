package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One change event in an {@link Issue}'s history (ADR-0007) — the {@code (issue, actor, timestamp)}
 * header. A single edit that changes several fields is one {@code ActivityLog} with several
 * {@link ActivityLogItem} children, so the history reads as grouped events rather than a flat field
 * stream. Hand-rolled, recorded explicitly by each mutating service — not Hibernate Envers.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ActivityLog {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "actor_member_id", nullable = false, length = 36)
    private String actorMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
