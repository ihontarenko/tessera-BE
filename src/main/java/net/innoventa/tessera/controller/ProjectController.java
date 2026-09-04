package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.dto.project.IssueKeyPreview;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.dto.project.RekeyProjectRequest;
import net.innoventa.tessera.dto.project.RekeyProjectResponse;
import net.innoventa.tessera.dto.project.UpdateProjectRequest;
import net.innoventa.tessera.service.ProjectIssueTypeService;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.security.access.target.ProjectByKey;
import net.innoventa.tessera.service.IssueKeyPreviewService;
import net.innoventa.tessera.service.ProjectRekeyService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final IssueKeyPreviewService  issueKeyPreviewService;
    private final ProjectRekeyService     projectRekeyService;

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
     * The same project addressed by its key, which is what its page's URL carries.
     *
     * <p>It sits under a literal path segment rather than sharing {@code /api/projects/{id}} and
     * guessing which of the two an argument is — a route that means one thing cannot be got wrong. The
     * issue routes made the same choice for the same reason.
     *
     * <p>⚠️ <strong>The scope comes from the resource, not from the path.</strong> {@code PROJECT}
     * resolves its instance from a request parameter literally named {@code projectId}; there is none
     * here, so {@code ProjectByKey} is what tells the engine which project this is about.
     */
    @GetMapping("/by-key/{projectKey}")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT,
                    resource = ProjectByKey.class, resourceId = "projectKey")
    public ProjectResponse getByKey(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return projectService.getByKey(jwt, projectKey);
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

    /**
     * What the next key would look like under a format nobody has saved yet.
     *
     * <p>⚠️ <strong>{@code ADMINISTER_PROJECT}, not {@code BROWSE_PROJECT}</strong> — it is a
     * question only the settings screen asks, and answering it for anybody would let a member
     * probe how many issues a project holds by reading the sequence out of the preview.
     */
    @GetMapping("/{projectId}/key-preview")
    @RequiresAccess(permission = Permissions.ADMINISTER_PROJECT, scope = Scopes.PROJECT)
    public IssueKeyPreview keyPreview(
        @PathVariable String projectId,
        @RequestParam("keyStrategy") String keyStrategy,
        @RequestParam(value = "keyPattern", required = false) String keyPattern
    ) {
        return issueKeyPreviewService.preview(
            projectService.requireProject(projectId), keyStrategy, keyPattern);
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

    /**
     * Change the project's key, rewriting every issue key under it.
     *
     * <p>⚠️ <strong>Separate from {@code PUT} on purpose, and not because the payload differs.</strong>
     * Everything the update endpoint changes affects what this installation does next; this changes what
     * a link somebody wrote last year points at. Folding it into the ordinary save would make a rekey
     * something a screen could do by accident while saving a name — and it is the one project edit whose
     * whole design is that it cannot happen by accident.
     *
     * <p>{@code POST} rather than {@code PUT}: it is not idempotent in any sense a caller can rely on —
     * sending it twice succeeds once and is then refused, because the confirmation names a key that no
     * longer exists.
     */
    @PostMapping("/{projectId}/key")
    @RequiresAccess(permission = Permissions.ADMINISTER_PROJECT, scope = Scopes.PROJECT)
    public RekeyProjectResponse rekey(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody RekeyProjectRequest request
    ) {
        return projectRekeyService.rekey(jwt, projectId, request);
    }

}
