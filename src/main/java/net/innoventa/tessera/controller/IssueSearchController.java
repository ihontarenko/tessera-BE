package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.service.IssueSearchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cross-project issue search (ticket 10) — the one issue read whose scope is the caller rather than
 * a project, which is why it has a controller of its own rather than a tenth method on
 * {@link IssueController}, where every route hangs off a project or an issue.
 */
@RestController
@RequiredArgsConstructor
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
