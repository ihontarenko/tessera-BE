package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.PermissionEffect;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.domain.ProjectPermissionOverride;
import net.innoventa.tessera.dto.membership.PermissionResponse;
import net.innoventa.tessera.dto.membership.RoleSummary;
import net.innoventa.tessera.exception.ForbiddenException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.repository.PermissionRepository;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.repository.ProjectPermissionOverrideRepository;
import net.innoventa.tessera.repository.ProjectRolePermissionRepository;
import net.innoventa.tessera.repository.ProjectRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The project-scoped authorization resolver and the reusable permission-check component every guarded
 * endpoint gates through. Effective permissions in a project are
 * {@code role permissions ∪ ALLOW overrides − DENY overrides}, with <strong>deny winning</strong> —
 * the exact resolution Moneta's {@code UserService.privilegeNames} uses, here scoped to a project
 * (CONTEXT.md, "ProjectPermissionOverride"; ADR-0003 of the local-authorization ticket).
 * <p>
 * Permissions are resolved per action rather than flattened onto the security principal because a
 * member holds a different set in each of their projects. A missing permission raises
 * {@link ForbiddenException} → {@code 403}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectPermissionService {

    private final ProjectMembershipRepository membershipRepository;
    private final ProjectRolePermissionRepository rolePermissionRepository;
    private final ProjectPermissionOverrideRepository overrideRepository;
    private final PermissionRepository permissionRepository;
    private final ProjectRoleRepository projectRoleRepository;

    /** The project-role catalog — the picker source for assigning roles. */
    public List<RoleSummary> listRoles() {
        return projectRoleRepository.findAllByOrderByNameAsc().stream()
            .map(role -> new RoleSummary(role.getId(), role.getName()))
            .toList();
    }

    /** The permission catalog — the picker source for setting overrides. */
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByNameAsc().stream()
            .map(permission -> new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription()))
            .toList();
    }

    /**
     * The set of permission <em>names</em> a member effectively holds in a project. A non-member with
     * no ALLOW override resolves to the empty set (and therefore cannot even browse).
     */
    public Set<String> effectivePermissions(String memberId, String projectId) {
        List<ProjectMembership> memberships = membershipRepository.findByProjectIdAndMemberId(projectId, memberId);
        List<String> roleIds = memberships.stream().map(ProjectMembership::getRoleId).distinct().toList();

        Set<String> effectivePermissionIds = new HashSet<>();
        if (!roleIds.isEmpty()) {
            rolePermissionRepository.findByRoleIdIn(roleIds)
                .forEach(rolePermission -> effectivePermissionIds.add(rolePermission.getPermissionId()));
        }

        for (ProjectPermissionOverride override : overrideRepository.findByProjectIdAndMemberId(projectId, memberId)) {
            if (override.getEffect() == PermissionEffect.ALLOW) {
                effectivePermissionIds.add(override.getPermissionId());
            } else {
                // DENY wins over any role grant and any ALLOW — unconditional removal.
                effectivePermissionIds.remove(override.getPermissionId());
            }
        }

        return permissionRepository.findAllById(effectivePermissionIds).stream()
            .map(net.innoventa.tessera.domain.Permission::getName)
            .collect(java.util.stream.Collectors.toSet());
    }

    public boolean hasPermission(String memberId, String projectId, String permissionName) {
        return effectivePermissions(memberId, projectId).contains(permissionName);
    }

    public boolean isMember(String memberId, String projectId) {
        return membershipRepository.existsByProjectIdAndMemberId(projectId, memberId);
    }

    /**
     * Read visibility for a project-scoped resource. A non-member cannot even know the project exists
     * (membership-based isolation, ADR-0002), so their access is a {@code 404}, not a {@code 403};
     * a member without {@code BROWSE_PROJECT} (e.g. denied by an override) gets a {@code 403}. The
     * single gate every read endpoint on a project's issues/comments/history/components/versions uses.
     */
    public void requireVisible(String memberId, String projectId) {
        if (!isMember(memberId, projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        if (!hasPermission(memberId, projectId, Permissions.BROWSE_PROJECT)) {
            throw new ForbiddenException("Missing permission '" + Permissions.BROWSE_PROJECT + "' in project " + projectId);
        }
    }

    /**
     * Gate an action: throw {@link ForbiddenException} ({@code 403}) unless the member holds
     * {@code permissionName} in the project. The reusable check every project-scoped mutation calls.
     */
    public void require(Member member, String projectId, String permissionName) {
        if (!hasPermission(member.getId(), projectId, permissionName)) {
            throw new ForbiddenException(
                "Missing permission '" + permissionName + "' in project " + projectId);
        }
    }

}
