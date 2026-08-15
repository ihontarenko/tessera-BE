package net.innoventa.tessera.dto;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SystemRole;

import java.util.List;

/**
 * The {@code GET /api/members/me} payload — the current member's identity and global tier, sourced
 * from Tessera's own {@code Member} row rather than the raw token, so the shell renders a name/avatar
 * that already reflects any local state.
 *
 * @param globalPermissions what this member holds <strong>installation-wide</strong> — the answer the
 *                          shell needs before anything is clicked, so an Administration entry is
 *                          rendered for whoever can use it rather than for everybody and refused.
 *                          ⚠️ Never the authority: every route it leads to declares its own
 *                          {@code @RequiresAccess} and refuses independently. It is deliberately not
 *                          the project permissions — those are per project and travel on
 *                          {@code ProjectResponse.myPermissions}, which is the only shape that can
 *                          answer "here but not there".
 */
public record CurrentMemberResponse(
    String id,
    String subject,
    String displayName,
    String email,
    MemberAvatarView avatar,
    SystemRole systemRole,
    List<String> globalPermissions
) {

    public static CurrentMemberResponse from(Member member, List<String> globalPermissions) {
        return new CurrentMemberResponse(
            member.getId(),
            member.getSubject(),
            member.getDisplayName(),
            member.getEmail(),
            MemberAvatarView.from(member),
            member.getSystemRole(),
            globalPermissions
        );
    }

}
