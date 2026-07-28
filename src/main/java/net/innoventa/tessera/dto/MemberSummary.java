package net.innoventa.tessera.dto;

import net.innoventa.tessera.domain.Member;

/**
 * The compact projection of a {@link Member} embedded wherever a person is referenced — project
 * lead, membership rows, (later) issue assignee/reporter, comment author. Carries only what the UI
 * needs to render a name/avatar, never the raw subject in bulk listings.
 */
public record MemberSummary(
    String id,
    String displayName,
    String email
) {

    public static MemberSummary from(Member member) {
        return new MemberSummary(member.getId(), member.getDisplayName(), member.getEmail());
    }

}
