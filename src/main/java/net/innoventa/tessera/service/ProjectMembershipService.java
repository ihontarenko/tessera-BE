package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Permission;
import net.innoventa.tessera.domain.PermissionEffect;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.domain.ProjectPermissionOverride;
import net.innoventa.tessera.domain.ProjectRole;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.membership.AddProjectMemberRequest;
import net.innoventa.tessera.dto.membership.OverrideSummary;
import net.innoventa.tessera.dto.membership.ProjectMemberResponse;
import net.innoventa.tessera.dto.membership.RoleSummary;
import net.innoventa.tessera.dto.membership.SetMemberRolesRequest;
import net.innoventa.tessera.dto.membership.SetPermissionOverrideRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PermissionRepository;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.repository.ProjectPermissionOverrideRepository;
import net.innoventa.tessera.repository.ProjectRoleRepository;
import net.innoventa.tessera.security.access.LocalAuthorizationMirror;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Project membership and role/override administration. Every mutation here requires
 * {@code ADMINISTER_PROJECT} in the target project; viewing the people list requires only membership.
 * A last-administrator guard stops a project being left with no one who can administer it (which no
 * later action could recover without a database edit).
 */
@Service
@RequiredArgsConstructor
public class ProjectMembershipService {

    private static final String ADMINISTRATOR_ROLE_NAME = "Administrator";

    private final ProjectMembershipRepository membershipRepository;
    private final ProjectPermissionOverrideRepository overrideRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final PermissionRepository permissionRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final ProjectService projectService;
    private final ProjectAccess projectAccess;
    private final LocalAuthorizationMirror grants;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Jwt jwt, String projectId) {
        memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        // ADR-0002's isolation is the route's: BROWSE_PROJECT at the project, refused as a 404 for
        // anybody holding nothing there.
        return buildMemberResponses(projectId);
    }

    @Transactional
    public List<ProjectMemberResponse> addMember(Jwt jwt, String projectId, AddProjectMemberRequest request) {
        Member caller = requireAdministration(jwt, projectId);

        Member target = memberService.requireMember(request.memberId());
        request.roleIds().stream().distinct().forEach(roleId -> {
            requireRole(roleId);
            if (!membershipRepository.existsByProjectIdAndMemberIdAndRoleId(projectId, target.getId(), roleId)) {
                membershipRepository.save(ProjectMembership.builder()
                    .id(idGenerator.get())
                    .projectId(projectId)
                    .memberId(target.getId())
                    .roleId(roleId)
                    .build());
            }

            grants.assignRole(target.getId(), projectId, displayNameOf(roleId), caller.getId());
        });

        return buildMemberResponses(projectId);
    }

    @Transactional
    public ProjectMemberResponse setRoles(Jwt jwt, String projectId, String memberId, SetMemberRolesRequest request) {
        Member caller = requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);

        List<String> roleIds = request.roleIds().stream().distinct().toList();
        roleIds.forEach(this::requireRole);

        boolean losesAdministrator = !roleIds.contains(administratorRoleId());
        if (losesAdministrator && isOnlyAdministrator(projectId, target.getId())) {
            throw new BusinessRuleViolationException(
                "Cannot remove the Administrator role from the project's only administrator");
        }

        // Replace-the-set. Flush the delete before the inserts so a kept role does not collide with
        // its own about-to-be-deleted row (Hibernate flushes inserts before deletes otherwise).
        membershipRepository.deleteByProjectIdAndMemberId(projectId, target.getId());
        membershipRepository.flush();
        roleIds.forEach(roleId -> membershipRepository.save(ProjectMembership.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .memberId(target.getId())
            .roleId(roleId)
            .build()));

        // Replace-the-set on the engine's side too, and in the same order: everything back, then what
        // the request asked for. A role kept across the change is taken and re-given, which is one row
        // rewritten rather than two states to reason about.
        grants.unassignAllRoles(target.getId(), projectId);
        roleIds.forEach(roleId ->
            grants.assignRole(target.getId(), projectId, displayNameOf(roleId), caller.getId()));

        return buildMemberResponse(target.getId(), projectId);
    }

    @Transactional
    public void removeMember(Jwt jwt, String projectId, String memberId) {
        requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);

        if (isOnlyAdministrator(projectId, target.getId())) {
            throw new BusinessRuleViolationException(
                "Cannot remove the project's only administrator");
        }

        membershipRepository.deleteByProjectIdAndMemberId(projectId, target.getId());
        overrideRepository.deleteByProjectIdAndMemberId(projectId, target.getId());

        // ⚠️ Nothing cascades. A library table cannot foreign-key into this product's rows, so deleting
        // a membership leaves every grant it stood for behind unless something takes them back by hand.
        grants.clearEverythingFor(target.getId(), projectId);
    }

    @Transactional
    public ProjectMemberResponse setOverride(Jwt jwt, String projectId, String memberId, SetPermissionOverrideRequest request) {
        Member caller = requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);
        requirePermission(request.permissionId());

        // A business rule rather than an authorization check: an override on somebody who is not here
        // would sit in the table granting or denying nothing, and nobody would find it.
        if (!projectAccess.isMember(target, projectId)) {
            throw new BusinessRuleViolationException("Member is not part of this project: " + memberId);
        }

        // One override per (project, member, permission): replace any existing one.
        overrideRepository.deleteByProjectIdAndMemberIdAndPermissionId(projectId, target.getId(), request.permissionId());
        overrideRepository.flush();
        overrideRepository.save(ProjectPermissionOverride.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .memberId(target.getId())
            .permissionId(request.permissionId())
            .effect(request.effect())
            .build());

        grants.setOverride(target.getId(), projectId, permissionNameOf(request.permissionId()),
            request.effect() == PermissionEffect.ALLOW, caller.getId());

        return buildMemberResponse(target.getId(), projectId);
    }

    @Transactional
    public ProjectMemberResponse clearOverride(Jwt jwt, String projectId, String memberId, String permissionId) {
        requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);

        overrideRepository.deleteByProjectIdAndMemberIdAndPermissionId(projectId, target.getId(), permissionId);

        grants.clearOverride(target.getId(), projectId, permissionNameOf(permissionId));

        return buildMemberResponse(target.getId(), projectId);
    }

    /**
     * The caller, having proved they may administer this project.
     *
     * <p>⚠️ <strong>The permission check itself is on the route now</strong> — every method here is
     * reached through a handler carrying {@code @RequiresAccess(ADMINISTER_PROJECT, scope = PROJECT)},
     * and the engine has already refused before any of this runs. What survives is resolving the caller,
     * because an administration change records <em>who</em> made it, and asserting the project exists,
     * because a 404 is a better answer than a grant at a place nothing has.
     */
    private Member requireAdministration(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        return caller;
    }

    private List<ProjectMemberResponse> buildMemberResponses(String projectId) {
        Map<String, List<ProjectMembership>> membershipsByMember = membershipRepository.findByProjectId(projectId)
            .stream()
            .collect(Collectors.groupingBy(ProjectMembership::getMemberId));

        Map<String, ProjectRole> rolesById = loadRolesById();
        Map<String, Permission> permissionsById = loadPermissionsById();

        return membershipsByMember.keySet().stream()
            .map(memberId -> buildMemberResponse(memberId, projectId, rolesById, permissionsById))
            .sorted((first, second) -> nameOf(first).compareToIgnoreCase(nameOf(second)))
            .toList();
    }

    /** Single-member response (after a mutation) — loads the small catalogs once for this one member. */
    private ProjectMemberResponse buildMemberResponse(String memberId, String projectId) {
        return buildMemberResponse(memberId, projectId, loadRolesById(), loadPermissionsById());
    }

    private ProjectMemberResponse buildMemberResponse(
        String memberId,
        String projectId,
        Map<String, ProjectRole> rolesById,
        Map<String, Permission> permissionsById
    ) {
        Member member = memberService.requireMember(memberId);

        List<RoleSummary> roles = membershipRepository.findByProjectIdAndMemberId(projectId, memberId).stream()
            .map(ProjectMembership::getRoleId)
            .distinct()
            .map(rolesById::get)
            .filter(role -> role != null)
            .map(role -> new RoleSummary(role.getId(), role.getName()))
            .sorted((first, second) -> first.name().compareToIgnoreCase(second.name()))
            .toList();

        List<OverrideSummary> overrides = overrideRepository.findByProjectIdAndMemberId(projectId, memberId).stream()
            .map(override -> new OverrideSummary(
                override.getPermissionId(),
                permissionsById.containsKey(override.getPermissionId())
                    ? permissionsById.get(override.getPermissionId()).getName()
                    : override.getPermissionId(),
                override.getEffect()))
            .sorted((first, second) -> first.permissionName().compareToIgnoreCase(second.permissionName()))
            .toList();

        return new ProjectMemberResponse(MemberSummary.from(member), roles, overrides);
    }

    private Map<String, ProjectRole> loadRolesById() {
        return projectRoleRepository.findAll().stream()
            .collect(Collectors.toMap(ProjectRole::getId, Function.identity()));
    }

    private Map<String, Permission> loadPermissionsById() {
        return permissionRepository.findAll().stream()
            .collect(Collectors.toMap(Permission::getId, Function.identity()));
    }

    private String nameOf(ProjectMemberResponse response) {
        String displayName = response.member().displayName();
        return displayName != null ? displayName : response.member().id();
    }

    private boolean isOnlyAdministrator(String projectId, String memberId) {
        Set<String> administrators = membershipRepository.findByProjectId(projectId).stream()
            .filter(membership -> membership.getRoleId().equals(administratorRoleId()))
            .map(ProjectMembership::getMemberId)
            .collect(Collectors.toSet());

        return administrators.size() == 1 && administrators.contains(memberId);
    }

    private String administratorRoleId() {
        return projectRoleRepository.findByName(ADMINISTRATOR_ROLE_NAME)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator role not seeded"))
            .getId();
    }

    private void requireRole(String roleId) {
        if (!projectRoleRepository.existsById(roleId)) {
            throw new ResourceNotFoundException("Project role not found: " + roleId);
        }
    }

    private void requirePermission(String permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new ResourceNotFoundException("Permission not found: " + permissionId);
        }
    }

    /**
     * What {@code project_roles} calls a role, for the mirror that has to name it the engine's way.
     *
     * <p>⚠️ Transitional, like the mirror itself: the screens still speak the surrogate identifiers those
     * tables hand out, and V000014 replaces both with the name.
     */
    private String displayNameOf(String roleId) {
        return projectRoleRepository.findById(roleId).map(ProjectRole::getName).orElse(null);
    }

    private String permissionNameOf(String permissionId) {
        return permissionRepository.findById(permissionId).map(Permission::getName).orElse(null);
    }

}
