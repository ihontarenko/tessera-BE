package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.membership.AddProjectMemberRequest;
import net.innoventa.tessera.dto.membership.ProjectMemberResponse;
import net.innoventa.tessera.dto.membership.SetMemberRolesRequest;
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
 * Who is in a project, and what role they hold there.
 *
 * <p>The class declares {@code ADMINISTER_PROJECT} at the project, because that is what every mutation
 * here costs; the one read overrides it downwards. Writing it once and letting the read say what it
 * needs is the shape the annotation is for — the alternative, five identical lines and one different
 * one, is where somebody eventually pastes the wrong one.
 *
 * <p>⚠️ <strong>There is one home for a grant, and a membership IS one.</strong> Adding somebody here
 * writes an {@code access_role_assignments} row and nothing else — no local table beside it, no mirror
 * keeping two stores in step. A role is addressed by the name the policy document writes, which is why
 * this screen and the installation-wide one finally say the same word for the same thing.
 *
 * <p>⚠️ <strong>Personal allow and deny are not here any more.</strong> A per-person override inside one
 * project was a second answer to "what may this person do", given by somebody the role model
 * deliberately withholds that power from. It lives on {@code /admin/access}, behind
 * {@code access:administer}, where whoever grants it also maintains the roles it overrides.
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

    // ⚠️ There are no override routes any more, and their absence is the point. A per-person allow
    // or deny inside one project was a second answer to "what may this person do" — editable here,
    // but invisible to whoever maintains the roles everybody else is judged by. Permissions come
    // from roles, and roles are edited once, installation-wide. See ProjectMembershipService.

}
