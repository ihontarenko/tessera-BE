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

    /**
     * Which agent did it, where one did — null for a person at the keyboard.
     *
     * <p>⚠️ <strong>Beside the actor, never instead of it.</strong> An agent acts <em>for</em> somebody,
     * so the change is still theirs and still turns up in everything that asks about them; this is the
     * second half of the sentence, not a replacement for the first.
     *
     * <p>⚠️ <strong>The name is a snapshot and carries no foreign key.</strong> History has to outlive
     * what it names — an agent discarded next year must not take "who did this" with it, and must not
     * become undeletable either.
     */
    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(name = "agent_name", length = 128)
    private String agentName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
