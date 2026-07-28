package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.membership.PermissionResponse;
import net.innoventa.tessera.dto.membership.RoleSummary;
import net.innoventa.tessera.service.ProjectPermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only exposure of the authorization catalogs — the project-role set and the permission set — so
 * the access-settings UI can populate its role/permission pickers. The role → permission map is global
 * in Phase 1, so these need no project scope.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
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
