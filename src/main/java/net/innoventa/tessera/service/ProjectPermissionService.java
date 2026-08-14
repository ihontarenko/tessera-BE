package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.PermissionEffect;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.domain.ProjectPermissionOverride;
import net.innoventa.tessera.dto.membership.PermissionResponse;
import net.innoventa.tessera.dto.membership.RoleSummary;
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
 * ⚠️ <strong>Retired. It authorizes nothing, and it is kept for exactly one reason.</strong>
 *
 * <p>This was the project-scoped authorization resolver every guarded endpoint gated through —
 * {@code role permissions ∪ ALLOW overrides − DENY overrides}, deny winning, joined through
 * {@code project_roles} and {@code permissions}. Authorization is {@code jmouse-access}'s now: a route
 * declares what it needs with {@code @RequiresAccess} and the engine resolves it over {@code access_*}
 * rows. Every one of the thirty-eight {@code require*} call sites is gone.
 *
 * <p>What is left is a <strong>second opinion to compare against</strong>.
 * {@link net.innoventa.tessera.security.access.ParallelAuthorizationCheck} asks both models the same
 * questions at startup, over this installation's real data, and reports every answer they disagree
 * about — which is the only thing that turns "we replaced the authorization model" from a hope into a
 * fact. It cannot do that against a class that has been deleted.
 *
 * <p>⚠️ <strong>It goes with V000014</strong>, together with the tables it reads, the check that reads
 * it, and {@code LocalAuthorizationMirror}. Nothing new should call it; the two catalog methods below
 * are still read by {@code AuthorizationCatalogController}, whose pickers speak the identifiers those
 * tables hand out and move in the same change.
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

}
