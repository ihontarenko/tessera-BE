package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.shipped.ShippedResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.ShippedService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a project has delivered (TSSR-4) — one read, grouped by sprint or by month depending on how the
 * project plans.
 *
 * <p>A controller of its own rather than a method on {@link net.innoventa.tessera.controller.BacklogController}
 * or {@link ReportController}: a backlog is what is left, a report is one sprint's arithmetic, and this
 * is the whole history of finished work. Gated as every project-scoped read is — a non-member gets a
 * {@code 404}, a member without {@code BROWSE_PROJECT} a {@code 403}.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
public class ShippedController {

    private final ShippedService shippedService;

    @GetMapping("/api/projects/{projectId}/shipped")
    public ShippedResponse shipped(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return shippedService.getShipped(jwt, projectId);
    }

}
