package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.membership.AddProjectMemberRequest;
import net.innoventa.tessera.dto.membership.ProjectMemberResponse;
import net.innoventa.tessera.dto.membership.SetMemberRolesRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.Roles;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.security.access.TesseraScope;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessDisclosure;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is in a project, and what role they hold there.
 *
 * <h2>⚠️ There is one home for a grant now, and this is not it</h2>
 *
 * <p>This used to keep its own tables — {@code project_memberships} joined to {@code project_roles},
 * with {@code project_permission_overrides} beside them — and <strong>dual-write</strong> every change
 * into the engine as well. Two stores, one truth, and a reconciliation nobody could see. The tables are
 * gone (V000014); a membership is an {@code access_role_assignments} row and this class reads and
 * writes it through the engine's own ports.
 *
 * <p>The practical consequence is that a role is addressed <strong>by name</strong> —
 * {@code PROJECT_DEVELOPER}, the name the policy document writes and the access screen shows — rather
 * than by a surrogate identifier from a table that no longer exists. The per-project picker and the
 * installation-wide screen finally say the same word for the same thing.
 *
 * <h2>⚠️ Personal permission overrides are gone, deliberately</h2>
 *
 * <p>A per-person allow or deny inside one project was a second way to answer "what may this person
 * do", and it answered differently from the roles every other screen shows. It also put a power in the
 * project administrator's hands that the role model deliberately withholds: granting somebody a
 * permission their role does not carry, one project at a time, invisibly to whoever maintains the
 * roles. What a role carries is edited in one place, installation-wide, by somebody holding
 * {@code access:administer} — and that is now the only place any permission comes from.
 *
 * <p>Nothing is lost that a role cannot express: needing a fourth combination of permissions is an
 * argument for a fourth role, which everybody can see, rather than an exception buried in one project.
 */
@Service
@RequiredArgsConstructor
public class ProjectMembershipService {

    /** How a membership written by this screen identifies itself among the engine's rows. */
    private static final String SOURCE = "MEMBERSHIP";

    private final MemberService        memberService;
    private final ProjectService       projectService;
    private final ProjectAccess        projectAccess;
    private final AccessAdministration access;
    private final AccessDisclosure     disclosure;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Jwt jwt, String projectId) {
        memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        // ADR-0002's isolation is the route's: BROWSE_PROJECT at the project, refused as a 404 for
        // anybody holding nothing there.
        return membersOf(projectId);
    }

    @Transactional
    public List<ProjectMemberResponse> addMember(Jwt jwt, String projectId, AddProjectMemberRequest request) {
        Member caller = requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(request.memberId());

        request.roleNames().stream().distinct().forEach(roleName -> {
            requireProjectRole(roleName);
            access.assign(target.getId(), roleName, Targets.projectScope(projectId), SOURCE, caller.getId(), null);
        });

        return membersOf(projectId);
    }

    /**
     * Replace the whole set of roles somebody holds here.
     *
     * <p>Everything back, then what the request asked for — a role kept across the change is taken and
     * re-given, which is one row rewritten rather than two states to reason about.
     */
    @Transactional
    public ProjectMemberResponse setRoles(
        Jwt jwt, String projectId, String memberId, SetMemberRolesRequest request) {

        Member caller = requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);

        List<String> roleNames = request.roleNames().stream().distinct().toList();
        roleNames.forEach(ProjectMembershipService::requireProjectRole);

        if (!roleNames.contains(Roles.PROJECT_ADMINISTRATOR) && isOnlyAdministrator(projectId, target.getId())) {
            throw new BusinessRuleViolationException(
                "Cannot remove the administrator role from the project's only administrator");
        }

        access.unassignAllAt(target.getId(), Targets.projectScope(projectId));
        roleNames.forEach(roleName ->
            access.assign(target.getId(), roleName, Targets.projectScope(projectId), SOURCE, caller.getId(), null));

        return memberOf(projectId, target.getId());
    }

    @Transactional
    public void removeMember(Jwt jwt, String projectId, String memberId) {
        requireAdministration(jwt, projectId);
        Member target = memberService.requireMember(memberId);

        if (isOnlyAdministrator(projectId, target.getId())) {
            throw new BusinessRuleViolationException("Cannot remove the project's only administrator");
        }

        access.unassignAllAt(target.getId(), Targets.projectScope(projectId));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    /**
     * Everybody holding a role in this project, with the roles they hold.
     *
     * <p>⚠️ <strong>Membership is not a row to look up any more — it is what the grants say.</strong>
     * Somebody appears here exactly when they hold a project role here, which is the same fact
     * every authorization decision is made from, so the screen cannot disagree with the engine.
     */
    private List<ProjectMemberResponse> membersOf(String projectId) {
        Map<String, List<String>> rolesByMember = new LinkedHashMap<>();

        disclosure.roleHoldings().stream()
            .filter(holding -> TesseraScope.PROJECT.equals(holding.at().type()))
            .filter(holding -> projectId.equals(holding.at().id()))
            .forEach(holding -> rolesByMember
                .computeIfAbsent(holding.subjectId(), memberId -> new java.util.ArrayList<>())
                .add(holding.roleName()));

        return rolesByMember.entrySet().stream()
            .map(entry -> describe(entry.getKey(), entry.getValue()))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(response -> response.member().displayName() == null
                ? response.member().id()
                : response.member().displayName()))
            .toList();
    }

    private ProjectMemberResponse memberOf(String projectId, String memberId) {
        return membersOf(projectId).stream()
            .filter(response -> response.member().id().equals(memberId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Member is not part of this project: " + memberId));
    }

    /**
     * A holding as the screen shows it, or null where the account behind it no longer exists.
     *
     * <p>A grant outliving its member is a row worth keeping — it is auditable, and the access screen
     * says so plainly — but a project's people list is not where somebody should meet it.
     */
    private ProjectMemberResponse describe(String memberId, List<String> roleNames) {
        Member member;

        try {
            member = memberService.requireMember(memberId);
        } catch (ResourceNotFoundException gone) {
            return null;
        }

        return new ProjectMemberResponse(MemberSummary.from(member), roleNames.stream().sorted().toList());
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private Member requireAdministration(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectAccess.require(caller, projectId, Permissions.ADMINISTER_PROJECT);

        return caller;
    }

    /**
     * ⚠️ One of the three a person may hold <em>in a project</em>.
     *
     * <p>The engine would happily assign {@code GLOBAL_ACCESS_ADMINISTRATOR} at a project scope — the
     * grammar's {@code assignable @GLOBAL} stops it being handed out installation-wide, not the other
     * way round — and this route must not be the way somebody acquires an installation-wide role.
     */
    private static void requireProjectRole(String roleName) {
        if (!Roles.PROJECT_ROLES.contains(roleName)) {
            throw new BusinessRuleViolationException(
                "'" + roleName + "' is not a role a project hands out. Choose one of: "
                + String.join(", ", Roles.PROJECT_ROLES) + ".");
        }
    }

    /**
     * ⚠️ The last administrator cannot be demoted or removed — the rule that keeps a project
     * administrable. It is asked of the grants, so a personal assignment counts exactly as a normal one.
     */
    private boolean isOnlyAdministrator(String projectId, String memberId) {
        List<String> administrators = disclosure.roleHoldings().stream()
            .filter(holding -> TesseraScope.PROJECT.equals(holding.at().type()))
            .filter(holding -> projectId.equals(holding.at().id()))
            .filter(holding -> Roles.PROJECT_ADMINISTRATOR.equals(holding.roleName()))
            .map(holding -> holding.subjectId())
            .distinct()
            .toList();

        return administrators.size() == 1 && administrators.contains(memberId);
    }

}
