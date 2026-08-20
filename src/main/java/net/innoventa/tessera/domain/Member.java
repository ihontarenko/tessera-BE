package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;
import org.jmouse.avatar.AvatarChoice;
import org.jmouse.avatar.AvatarOwner;
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
public class Member implements AvatarOwner {

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
    private AvatarChoice avatarKind = AvatarChoice.INITIALS;

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

    /**
     * Whether this row is a person or a client's standing identity (TSSR-32).
     *
     * <p>⚠️ <strong>Every listing that means people filters on this.</strong> An agent is a member so
     * that authorship is one reference with one face; it is not a member in the sense a picker means.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private MemberKind kind = MemberKind.PERSON;

    /**
     * Whose agent this is — the owner's member id, and null on a person.
     *
     * <p>⚠️ <strong>Record-keeping, and nothing that decides whether a call is allowed may read it.</strong>
     * It <em>looks</em> like an inheritance edge, which is exactly the trap: a permission resolved
     * through it would be a second permission model beside {@code jmouse-access}'s, agreeing with it
     * until the afternoon somebody changed one. Ownership for authorization is the library's
     * {@code Agent.ownerReference()}. See the ADR — WiQi got this wrong twice before it was written down.
     */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    /**
     * When the agent behind this row was switched off, or null while it is live.
     *
     * <p>⚠️ <strong>The non-negotiable of the whole epic.</strong> A comment points at its agent, so
     * deleting this row loses the author of every comment that agent ever wrote — silently, at the
     * moment somebody tidies up their connections. Discard retires; it never deletes.
     */
    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Drop back to drawn initials, forgetting whichever face was chosen before. */
    public void wearsInitials() {
        avatarKind   = AvatarChoice.INITIALS;
        avatarPreset = null;
        avatarFile   = null;
    }

    /** Wear a generated pixel face, drawn from {@code seed}. */
    public void wearsPreset(String seed) {
        avatarKind   = AvatarChoice.PRESET;
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
        avatarKind   = AvatarChoice.UPLOAD;
        avatarPreset = null;
        avatarFile   = picture;
    }

    // ── AvatarOwner ───────────────────────────────────────────────────────────
    //
    // What jmouse-avatars needs to see. The three writers above already had exactly the right shape —
    // one value in three columns, only ever set together — so adopting the library cost this seam and
    // nothing else. ⚠️ The module persists nothing: it mutates this row and the caller saves it, which
    // is why the invariant the database states stays the invariant this class states.

    @Override
    public String avatarOwnerId() {
        return id;
    }

    @Override
    public AvatarChoice avatarChoice() {
        return avatarKind;
    }

    @Override
    public String avatarSeed() {
        return avatarPreset;
    }

    @Override
    public StoredFile avatarFile() {
        return avatarFile;
    }

    /** Whether this row is a client's identity rather than a person's. */
    public boolean isAgent() {
        return kind == MemberKind.AGENT;
    }

    /** Whether the agent behind it has been switched off. Always false for a person. */
    public boolean isRetired() {
        return retiredAt != null;
    }

    /**
     * Switch the agent off without losing what it wrote.
     *
     * <p>⚠️ Idempotent, and it has to be: {@code discard} is reachable from a screen, from the protocol
     * and from removing a person, and the second caller must not move the date somebody is reading.
     */
    public void retire() {
        if (retiredAt == null) {
            retiredAt = LocalDateTime.now();
        }
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
