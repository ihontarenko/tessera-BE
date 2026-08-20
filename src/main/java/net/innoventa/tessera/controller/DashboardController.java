package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.dashboard.DashboardSummary;
import net.innoventa.tessera.service.DashboardService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard's aggregates.
 *
 * <p>⚠️ <strong>Bare {@code @RequiresAccess}, for the same reason the cross-project search carries
 * one.</strong> The request is not <em>about</em> a project — it is about whatever this reader can see —
 * so there is no project to be refused about, and a scope-confined annotation would need one named in a
 * request whose whole point is that it names none. The confinement is in the service, which asks
 * {@code BrowsableProjects} first and lets nothing be counted outside its answer.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@RequiresAccess
public class DashboardController {

    /** A week, because that is the window somebody means by "lately" without saying a number. */
    private static final String DEFAULT_DAYS = "7";

    private final DashboardService dashboardService;

    /**
     * @param days the window to look back over. Clamped rather than refused — see
     *             {@code DashboardService#summarize}; the response reports the window it actually used
     */
    @GetMapping("/summary")
    public DashboardSummary summary(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "days", defaultValue = DEFAULT_DAYS) int days
    ) {
        return dashboardService.summarize(jwt, days);
    }

}
