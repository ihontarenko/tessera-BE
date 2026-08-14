package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.service.IssueSearchService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cross-project issue search (ticket 10) — the one issue read whose scope is the caller rather than
 * a project, which is why it has a controller of its own rather than a tenth method on
 * {@link IssueController}, where every route hangs off a project or an issue.
 *
 * <p>⚠️ <strong>A bare declaration, and the filtering is the authorization.</strong> The route takes an
 * optional {@code projectId} but is not <em>about</em> one — its subject is "everything I can see" — so
 * there is no place to be refused at. What confines the answer is the visibility scope: the search runs
 * over the projects this member may browse and no others, which is the same fact the permission axis
 * would have used, applied as a filter instead of as a refusal.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess
public class IssueSearchController {

    private final IssueSearchService issueSearchService;

    @GetMapping("/api/issues/search")
    public IssueSearchResponse search(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String statusId,
        @RequestParam(required = false) String assigneeMemberId,
        @RequestParam(defaultValue = "false") boolean openOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        return issueSearchService.search(jwt, text, projectId, statusId, assigneeMemberId, openOnly, page, size);
    }

}
