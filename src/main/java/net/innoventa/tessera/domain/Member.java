package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;
import org.jmouse.storage.jpa.StoredFile;

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

    /**
     * Which of the two columns below carries this member's face, or that neither does.
     *
     * <p>⚠️ The three fields are one value in three columns, and the database states that invariant
     * ({@code members_check_avatar_shape}). Setting them apart is how they drift, so they are only ever
     * set together through {@link #wearsInitials()}, {@link #wearsPreset(String)} and
     * {@link #wearsPicture(StoredFile)}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_kind", nullable = false, length = 16)
    @Builder.Default
    private AvatarKind avatarKind = AvatarKind.INITIALS;

    /** The seed a generated pixel face is drawn from, when {@link #avatarKind} is {@code PRESET}. */
    @Column(name = "avatar_preset", length = 64)
    private String avatarPreset;

    /**
     * The uploaded picture, when {@link #avatarKind} is {@code UPLOAD}.
     *
     * <p>Everything about the bytes — where they live, what they weigh, what they hash to, which
     * backend holds them — belongs to the library-owned registry. What stays on this row is only which
     * object this person's face is.
     *
     * <p>⚠️ EAGER, and it has to be: a member is rendered as a chip in fourteen different payloads
     * assembled outside a transaction, and a lazy proxy dereferenced there fails on the response rather
     * than on the query.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "avatar_file_id")
    private StoredFile avatarFile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Drop back to drawn initials, forgetting whichever face was chosen before. */
    public void wearsInitials() {
        avatarKind   = AvatarKind.INITIALS;
        avatarPreset = null;
        avatarFile   = null;
    }

    /** Wear a generated pixel face, drawn from {@code seed}. */
    public void wearsPreset(String seed) {
        avatarKind   = AvatarKind.PRESET;
        avatarPreset = seed;
        avatarFile   = null;
    }

    /**
     * Wear an uploaded picture.
     *
     * <p>The previous picture is not deleted here and must not be: content-addressed keys mean two
     * members who chose the same image share one object, so the bytes are reclaimed by the sweeper
     * asking who still points at them, never by whoever stopped.
     */
    public void wearsPicture(StoredFile picture) {
        avatarKind   = AvatarKind.UPLOAD;
        avatarPreset = null;
        avatarFile   = picture;
    }

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
