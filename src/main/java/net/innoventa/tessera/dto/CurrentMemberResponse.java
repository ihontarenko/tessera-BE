package net.innoventa.tessera.dto;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SystemRole;

/**
 * The {@code GET /api/members/me} payload — the current member's identity and global tier, sourced
 * from Tessera's own {@code Member} row rather than the raw token, so the shell renders a name/avatar
 * that already reflects any local state.
 */
public record CurrentMemberResponse(
    String id,
    String subject,
    String displayName,
    String email,
    SystemRole systemRole
) {

    public static CurrentMemberResponse from(Member member) {
        return new CurrentMemberResponse(
            member.getId(),
            member.getSubject(),
            member.getDisplayName(),
            member.getEmail(),
            member.getSystemRole()
        );
    }

}
