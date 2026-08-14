package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.issue.CreateIssueLinkRequest;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.IssueLinkService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue links (ticket 12). Both operations return the refreshed source/acting issue so the detail
 * modal re-renders its links section from one response. Requires {@code EDIT_ISSUE}.
 */
@RestController
@RequestMapping("/api/issues/{issueId}/links")
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.EDIT_ISSUE, scope = Scopes.PROJECT,
                resource = Issue.class, resourceId = "issueId")
public class IssueLinkController {

    private final IssueLinkService issueLinkService;

    @PostMapping
    public IssueResponse add(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody CreateIssueLinkRequest request
    ) {
        return issueLinkService.addLink(jwt, issueId, request);
    }

    @DeleteMapping("/{linkId}")
    public IssueResponse remove(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @PathVariable String linkId
    ) {
        return issueLinkService.removeLink(jwt, issueId, linkId);
    }

}
