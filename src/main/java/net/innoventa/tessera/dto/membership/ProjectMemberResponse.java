package net.innoventa.tessera.dto.membership;

import net.innoventa.tessera.dto.MemberSummary;

import java.util.List;

/**
 * A member's access in one project: who they are, and the roles they hold here (additive).
 *
 * <p>⚠️ <strong>Roles by name, and nothing else.</strong> This used to carry a third field of personal
 * permission overrides — a second answer to "what may this person do", editable per project and
 * invisible to whoever maintains the roles. Permissions come from roles now, and roles are edited in
 * one installation-wide place; see {@code ProjectMembershipService} for why that is the whole story.
 *
 * <p>The effective-permission set is still not embedded here — this shows the input, and
 * {@code ProjectResponse.myPermissions} carries the resolved set for the current caller.
 */
public record ProjectMemberResponse(
    MemberSummary member,
    List<String> roles
) {
}
