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
import net.innoventa.tessera.service.IssueHierarchyService;
import net.innoventa.tessera.service.IssueOrganizationService;
import net.innoventa.tessera.service.IssueService;
import net.innoventa.tessera.service.TransitionService;
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
    public IssueResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody CreateIssueRequest request
    ) {
        return issueService.create(jwt, projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/issues")
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

    @GetMapping("/api/issues/{issueId}")
    public IssueResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        return issueService.get(jwt, issueId);
    }

    /**
     * The same issue addressed by key, which is what the issue page's URL carries (ticket 07). It sits
     * under a literal path segment rather than sharing {@code /api/issues/{id}} and guessing which of
     * the two an argument is — a route that means one thing cannot be got wrong.
     */
    @GetMapping("/api/issues/by-key/{issueKey}")
    public IssueResponse getByKey(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        return issueService.getByKey(jwt, issueKey);
    }

    @PutMapping("/api/issues/{issueId}")
    public IssueResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody UpdateIssueRequest request
    ) {
        return issueService.update(jwt, issueId, request);
    }

    @DeleteMapping("/api/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        issueService.delete(jwt, issueId);
    }

    @PostMapping("/api/issues/{issueId}/transitions")
    public IssueResponse transition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody TransitionIssueRequest request
    ) {
        return transitionService.transition(jwt, issueId, request);
    }

    @PutMapping("/api/issues/{issueId}/parent")
    public IssueResponse setParent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @RequestBody SetParentRequest request
    ) {
        return issueHierarchyService.setParent(jwt, issueId, request);
    }

    @PutMapping("/api/issues/{issueId}/organization")
    public IssueResponse updateOrganization(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @RequestBody UpdateIssueOrganizationRequest request
    ) {
        return issueOrganizationService.update(jwt, issueId, request);
    }

}
