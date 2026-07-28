package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A member's remark on an {@link Issue} — a flat list, no threaded replies (ticket 13). A separate
 * first-class entity, deliberately <strong>not</strong> an {@link ActivityLog} item (ADR-0007): a
 * comment is discussion, not a field change. Carries its author and created/updated timestamps; a
 * member edits or deletes their own.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Comment {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "author_member_id", nullable = false, length = 36)
    private String authorMemberId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
