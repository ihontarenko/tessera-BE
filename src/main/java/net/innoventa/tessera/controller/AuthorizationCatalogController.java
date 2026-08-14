package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.membership.PermissionResponse;
import net.innoventa.tessera.dto.membership.RoleSummary;
import net.innoventa.tessera.service.ProjectPermissionService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only exposure of the authorization catalogs — the project-role set and the permission set — so
 * the people screen can populate its role and permission pickers. A role is installation-wide, so
 * neither route has a project scope.
 *
 * <p>A bare {@code @RequiresAccess}: this is <em>vocabulary</em>, not who holds anything. Knowing that a
 * role called Developer exists discloses nothing about anybody, and gating it on
 * {@code ADMINISTER_PROJECT} would mean the picker was empty for exactly the person allowed to use it in
 * some other project.
 *
 * <p>⚠️ <strong>Still reading the retiring tables, deliberately.</strong> The identifiers these hand out
 * are {@code project_roles.id} and {@code permissions.id}, and the people screen posts them straight
 * back to {@code ProjectMembershipController} — so changing what they mean is one change with the screen
 * and not before it. Authorization itself no longer reads either table; see
 * {@code V000013__authorization_handover.sql}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@RequiresAccess
public class AuthorizationCatalogController {

    private final ProjectPermissionService projectPermissionService;

    @GetMapping("/project-roles")
    public List<RoleSummary> projectRoles() {
        return projectPermissionService.listRoles();
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return projectPermissionService.listPermissions();
    }

}
