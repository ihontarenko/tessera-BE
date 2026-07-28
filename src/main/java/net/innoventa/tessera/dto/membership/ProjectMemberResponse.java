package net.innoventa.tessera.dto.membership;

import net.innoventa.tessera.dto.MemberSummary;

import java.util.List;

/**
 * A member's access in one project: who they are, the roles they hold (additive), and their
 * individual permission overrides. The effective-permission set is not embedded here — the UI shows
 * the inputs (roles + overrides); {@code ProjectResponse.myPermissions} carries the resolved set for
 * the current caller.
 */
public record ProjectMemberResponse(
    MemberSummary member,
    List<RoleSummary> roles,
    List<OverrideSummary> overrides
) {
}
