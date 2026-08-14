package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.issue.CreateIssueRequest;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.SetParentRequest;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.dto.issue.UpdateIssueOrganizationRequest;
import net.innoventa.tessera.dto.issue.UpdateIssueRequest;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.security.access.target.IssueByKey;
import net.innoventa.tessera.service.IssueHierarchyService;
import net.innoventa.tessera.service.IssueOrganizationService;
import net.innoventa.tessera.service.IssueService;
import net.innoventa.tessera.service.TransitionService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Issues (tickets 07, 09, 10, 11). Creation and listing are project-scoped
 * ({@code /api/projects/{projectId}/issues}); everything about an existing issue hangs off
 * {@code /api/issues/{issueId}}. Identity comes off the token; scope and permissions are resolved from
 * the subject in the services, never trusted from the request body. Illegal transitions/hierarchy →
 * 409, missing permission → 403, unknown ids → 404 (via the global handler).
 */
@RestController
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final TransitionService transitionService;
    private final IssueHierarchyService issueHierarchyService;
    private final IssueOrganizationService issueOrganizationService;

    @PostMapping("/api/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAccess(permission = Permissions.CREATE_ISSUE, scope = Scopes.PROJECT)
    public IssueResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody CreateIssueRequest request
    ) {
        return issueService.create(jwt, projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/issues")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
    public List<IssueRowResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @RequestParam(required = false) String statusId,
        @RequestParam(required = false) String assigneeMemberId,
        @RequestParam(required = false) String issueTypeId,
        @RequestParam(required = false) String priorityId
    ) {
        return issueService.list(jwt, projectId, statusId, assigneeMemberId, issueTypeId, priorityId);
    }

    /**
     * ⚠️ <strong>The first route that names a resource</strong>, because it is the first that does not
     * spell its project into the URL. {@code IssueAccessTargetResolver} answers where the issue lives and
     * who reported it, and an identifier nothing resolves refuses as <em>no such row</em> rather than
     * passing as an unscoped call.
     */
    @GetMapping("/api/issues/{issueId}")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT, resource = Issue.class)
    public IssueResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        return issueService.get(jwt, issueId);
    }

    /**
     * The same issue addressed by key, which is what the issue page's URL carries (ticket 07). It sits
     * under a literal path segment rather than sharing {@code /api/issues/{id}} and guessing which of
     * the two an argument is — a route that means one thing cannot be got wrong.
     */
    @GetMapping("/api/issues/by-key/{issueKey}")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT,
                    resource = IssueByKey.class, resourceId = "issueKey")
    public IssueResponse getByKey(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        return issueService.getByKey(jwt, issueKey);
    }

    @PutMapping("/api/issues/{issueId}")
    @RequiresAccess(permission = Permissions.EDIT_ISSUE, scope = Scopes.PROJECT, resource = Issue.class)
    public IssueResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody UpdateIssueRequest request
    ) {
        return issueService.update(jwt, issueId, request);
    }

    @DeleteMapping("/api/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAccess(permission = Permissions.DELETE_ISSUE, scope = Scopes.PROJECT, resource = Issue.class)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        issueService.delete(jwt, issueId);
    }

    /**
     * ⚠️ <strong>The permission is the whole of what this annotation decides.</strong> Whether the
     * transition is <em>legal</em> — that the scheme allows it from here, that the resolution it needs is
     * present — is the workflow engine's, and stays exactly where it is. Those are domain refusals and
     * were never authorization; moving them here would be the mistake this cutover is otherwise avoiding.
     */
    @PostMapping("/api/issues/{issueId}/transitions")
    @RequiresAccess(permission = Permissions.TRANSITION_ISSUE, scope = Scopes.PROJECT, resource = Issue.class)
    public IssueResponse transition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody TransitionIssueRequest request
    ) {
        return transitionService.transition(jwt, issueId, request);
    }

    @PutMapping("/api/issues/{issueId}/parent")
    @RequiresAccess(permission = Permissions.EDIT_ISSUE, scope = Scopes.PROJECT, resource = Issue.class)
    public IssueResponse setParent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @RequestBody SetParentRequest request
    ) {
        return issueHierarchyService.setParent(jwt, issueId, request);
    }

    @PutMapping("/api/issues/{issueId}/organization")
    @RequiresAccess(permission = Permissions.EDIT_ISSUE, scope = Scopes.PROJECT, resource = Issue.class)
    public IssueResponse updateOrganization(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @RequestBody UpdateIssueOrganizationRequest request
    ) {
        return issueOrganizationService.update(jwt, issueId, request);
    }

}
