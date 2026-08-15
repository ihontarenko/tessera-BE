package net.innoventa.tessera.dto;

import net.innoventa.tessera.domain.Member;

/**
 * The compact projection of a {@link Member} embedded wherever a person is referenced — project
 * lead, membership rows, issue assignee/reporter, comment author. Carries only what the UI
 * needs to render a name/avatar, never the raw subject in bulk listings.
 *
 * <p>⚠️ <strong>This factory is the single funnel.</strong> Fourteen response types embed a
 * {@code MemberSummary} and every one of them is built by calling {@link #from(Member)} — so a field
 * added here reaches all of them at once and no call site changes. That is why {@code avatar} is here
 * rather than only on {@code CurrentMemberResponse}: a face nobody but you can see is not a face.
 */
public record MemberSummary(
    String id,
    String displayName,
    String email,
    MemberAvatarView avatar
) {

    public static MemberSummary from(Member member) {
        return new MemberSummary(member.getId(), member.getDisplayName(), member.getEmail(),
                                 MemberAvatarView.from(member));
    }

}
