package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The first-class local record of a person inside Tessera, keyed by the Identity JWT {@code sub}
 * claim. Auto-provisioned the first time a subject presents a token (see
 * {@code MemberService.resolveMember}), caching {@code displayName}/{@code email} from the token so
 * the UI renders names/avatars without calling Identity on every render. Every domain reference to a
 * person points at a {@code Member}, never at the raw subject string (see CONTEXT.md, "Member").
 * <p>
 * Identity owns authentication; Tessera owns authorization. {@link #systemRole} is the lightweight
 * global tier; project-scoped authority lives in {@code ProjectMembership} / {@code Permission}.
 */
@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(name = "uq_members_subject", columnNames = "subject"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Member {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** The validated JWT {@code sub} claim — the key a subject is provisioned/resolved by. */
    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 16)
    private SystemRole systemRole;

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
