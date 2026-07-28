package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.membership.AddProjectMemberRequest;
import net.innoventa.tessera.dto.membership.ProjectMemberResponse;
import net.innoventa.tessera.dto.membership.SetMemberRolesRequest;
import net.innoventa.tessera.dto.membership.SetPermissionOverrideRequest;
import net.innoventa.tessera.service.ProjectMembershipService;
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
 * Every mutation requires {@code ADMINISTER_PROJECT} (enforced in the service); the deny-wins model
 * is exercised end-to-end whenever a guarded action here or elsewhere returns {@code 403}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMembershipController {

    private final ProjectMembershipService membershipService;

    @GetMapping
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
