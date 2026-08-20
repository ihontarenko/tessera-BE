package net.innoventa.tessera.dto;

import org.jmouse.avatar.AvatarChoice;
import net.innoventa.tessera.domain.Member;

/**
 * A member's face, as the interface needs to draw it.
 *
 * <p>Everything the client must decide is decided here: it never sees a storage key, never assembles a
 * URL, and never learns that a registry exists. It reads {@code kind} and draws one of three things.
 *
 * <p>⚠️ Both value fields are always present in the payload and one of them is always null — Tessera
 * does not omit nulls (see {@code spring.jackson} in application.yml), because a field the DTO declares
 * and the response drops arrives as {@code undefined} and every {@code === null} written against it is
 * silently false.
 *
 * @param kind   which of the two below carries the answer, or that neither does
 * @param preset the seed a generated pixel face is drawn from — null unless {@code kind} is
 *               {@code PRESET}. Not a catalog key: the generator is total, so this is simply the string
 *               that was picked
 * @param url    where the uploaded picture's bytes are — null unless {@code kind} is {@code UPLOAD}.
 *               Addressed by the stored object rather than by the member, so replacing a picture
 *               produces a different URL and no cached copy can go stale; and readable by an
 *               {@code <img>} tag, which is the whole reason this is a URL and not an identifier — see
 *               {@code PublicAvatarController}
 */
public record MemberAvatarView(
    AvatarChoice kind,
    String preset,
    String url
) {

    /** The face of somebody who has never chosen one. */
    public static MemberAvatarView initials() {
        return new MemberAvatarView(AvatarChoice.INITIALS, null, null);
    }

    public static MemberAvatarView from(Member member) {
        return switch (member.getAvatarKind()) {
            case INITIALS -> initials();
            case PRESET -> new MemberAvatarView(AvatarChoice.PRESET, member.getAvatarPreset(), null);
            // ⚠️ Defensive rather than paranoid. The shape constraint makes a null file impossible for
            // this kind in the database — but a Member built in memory and not yet flushed has not met
            // that constraint, and rendering initials beats a null pointer inside a listing.
            case UPLOAD -> member.getAvatarFile() == null
                ? initials()
                : new MemberAvatarView(AvatarChoice.UPLOAD, null,
                                       PublicAvatarRoutes.of(member.getAvatarFile().getIdentifier()));
        };
    }

}
