package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.dto.project.UpdateProjectRequest;
import net.innoventa.tessera.service.ProjectIssueTypeService;
import net.innoventa.tessera.service.ProjectService;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(jwt, request);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return projectService.list(jwt);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return projectService.get(jwt, projectId);
    }

    /**
     * The issue types this project may create. Project-scoped on purpose: the global
     * {@code /api/configuration} catalog still lists every type that exists, which is the wrong list to
     * raise an issue from once a project's scheme narrows it.
     */
    @GetMapping("/{projectId}/issue-types")
    public ProjectIssueTypesResponse issueTypes(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return projectIssueTypeService.listCreatableIssueTypes(jwt, projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(jwt, projectId, request);
    }

}
