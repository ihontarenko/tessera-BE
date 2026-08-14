package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.membership.AddProjectMemberRequest;
import net.innoventa.tessera.dto.membership.ProjectMemberResponse;
import net.innoventa.tessera.dto.membership.SetMemberRolesRequest;
import net.innoventa.tessera.dto.membership.SetPermissionOverrideRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.ProjectMembershipService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Project access administration — members, their roles, and their individual permission overrides.
 *
 * <p>The class declares {@code ADMINISTER_PROJECT} at the project, because that is what every mutation
 * here costs; the one read overrides it downwards. Writing it once and letting the read say what it
 * needs is the shape the annotation is for — the alternative, five identical lines and one different
 * one, is where somebody eventually pastes the wrong one.
 *
 * <p>⚠️ <strong>The permission is on the route now, not in the service.</strong> The deny-wins model it
 * edits is the engine's, and every change made here is written into {@code access_*} as well as into the
 * retiring local tables — see {@code LocalAuthorizationMirror}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.ADMINISTER_PROJECT, scope = Scopes.PROJECT)
public class ProjectMembershipController {

    private final ProjectMembershipService membershipService;

    /** Who is in this project — an ordinary read, so browsing it is enough. */
    @GetMapping
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
    public List<ProjectMemberResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return membershipService.listMembers(jwt, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProjectMemberResponse> add(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody AddProjectMemberRequest request
    ) {
        return membershipService.addMember(jwt, projectId, request);
    }

    @PutMapping("/{memberId}/roles")
    public ProjectMemberResponse setRoles(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String memberId,
        @Valid @RequestBody SetMemberRolesRequest request
    ) {
        return membershipService.setRoles(jwt, projectId, memberId, request);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String memberId
    ) {
        membershipService.removeMember(jwt, projectId, memberId);
    }

    @PutMapping("/{memberId}/overrides")
    public ProjectMemberResponse setOverride(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String memberId,
        @Valid @RequestBody SetPermissionOverrideRequest request
    ) {
        return membershipService.setOverride(jwt, projectId, memberId, request);
    }

    @DeleteMapping("/{memberId}/overrides/{permissionId}")
    public ProjectMemberResponse clearOverride(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String memberId,
        @PathVariable String permissionId
    ) {
        return membershipService.clearOverride(jwt, projectId, memberId, permissionId);
    }

}
