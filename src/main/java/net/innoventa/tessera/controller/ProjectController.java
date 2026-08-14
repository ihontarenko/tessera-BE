package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.dto.project.UpdateProjectRequest;
import net.innoventa.tessera.service.ProjectIssueTypeService;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.ProjectService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectIssueTypeService projectIssueTypeService;

    /**
     * ⚠️ <strong>A bare declaration, and it is the honest one.</strong> There is no project to be scoped
     * to yet, and Tessera has never gated creating one on anything but being signed in — so a permission
     * here would be a new rule wearing a refactor's clothes. When an installation wants one, it is a
     * constant in {@code Permissions}, a line in the document, and this annotation.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAccess
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(jwt, request);
    }

    /**
     * ⚠️ <strong>Not scope-confined, deliberately — this is how a caller finds out which projects
     * exist.</strong> A listing gated at {@code @PROJECT} would need a project named in the request to be
     * refused about, which is the one thing the caller is asking. The narrow permission gates and the
     * visibility scope filters: the answer is every project this member may browse, and nothing else.
     */
    @GetMapping
    @RequiresAccess
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return projectService.list(jwt);
    }

    @GetMapping("/{projectId}")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return projectService.get(jwt, projectId);
    }

    /**
     * The issue types this project may create. Project-scoped on purpose: the global
     * {@code /api/configuration} catalog still lists every type that exists, which is the wrong list to
     * raise an issue from once a project's scheme narrows it.
     */
    @GetMapping("/{projectId}/issue-types")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
    public ProjectIssueTypesResponse issueTypes(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return projectIssueTypeService.listCreatableIssueTypes(jwt, projectId);
    }

    @PutMapping("/{projectId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_PROJECT, scope = Scopes.PROJECT)
    public ProjectResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(jwt, projectId, request);
    }

}
