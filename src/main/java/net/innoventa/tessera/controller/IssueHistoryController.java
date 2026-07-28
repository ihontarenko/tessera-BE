package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.activity.ActivityLogResponse;
import net.innoventa.tessera.service.IssueActivityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only issue change history (ticket 08) — the detail modal's History tab, newest-first.
 */
@RestController
@RequestMapping("/api/issues/{issueId}/history")
@RequiredArgsConstructor
public class IssueHistoryController {

    private final IssueActivityService issueActivityService;

    @GetMapping
    public List<ActivityLogResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        return issueActivityService.list(jwt, issueId);
    }

}
