package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.report.SprintReportResponse;
import net.innoventa.tessera.dto.report.VelocityPointView;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.report.SprintReportService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The reports a project can be read for (Phase-3 tickets 06 and 07). Everything here is derived on read
 * from data ordinary use already wrote: there is no snapshot table behind these and no scheduled job
 * feeding them (ADR-0013), so a chart is correct retroactively and has no holes on the days nobody was
 * at the machine.
 * <p>
 * The two differ in what they are addressed by, which is exactly what they measure. A report is
 * <em>one sprint's</em> and lives under it; velocity is the <em>project's</em> and spans every sprint it
 * has closed, which is why the Reports tab's sprint selector drives the first and not the second.
 * <p>
 * Both are reads, gated as every project-scoped read is: non-member {@code 404}, member without
 * {@code BROWSE_PROJECT} {@code 403}. Any sprint of the project that has run can be asked for, closed or
 * running; a sprint that never started is refused with a {@code 409}.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
public class ReportController {

    private final SprintReportService sprintReportService;

    /** A sprint's report and its burndown in one response, because they are one load (ADR-0013). */
    @GetMapping("/api/projects/{projectId}/sprints/{sprintId}/report")
    public SprintReportResponse report(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String sprintId
    ) {
        return sprintReportService.getReport(jwt, projectId, sprintId);
    }

    /** Committed against completed per closed sprint, oldest first; empty until a sprint has closed. */
    @GetMapping("/api/projects/{projectId}/velocity")
    public List<VelocityPointView> velocity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId
    ) {
        return sprintReportService.getVelocity(jwt, projectId);
    }

}
