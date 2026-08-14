package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.StatusCategoryImpact;
import net.innoventa.tessera.dto.configuration.StatusRequest;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.StatusWriteService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing the status catalog.
 *
 * <p>The interesting route here is {@link #categoryImpact}: a status's category decides whether an issue
 * in it counts as closed (ADR-0004) and which board column holds it (ADR-0010), so the screen asks what
 * a change would do <em>before</em> it offers Save. The write itself does not re-ask — confirming is the
 * interface's job, and a server that refused an unconfirmed change would need to remember what somebody
 * had been shown.
 */
@RestController
@RequestMapping("/api/admin/configuration/statuses")
@RequiredArgsConstructor
@RequiresAccess
public class StatusAdministrationController {

    private final StatusWriteService statusWriteService;
    private final ConfigurationUsage configurationUsage;

    @GetMapping("/{statusId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String statusId) {
        return configurationUsage.ofStatus(statusId);
    }

    /**
     * How many issues hold this status and how many cards would change column if it were moved into
     * {@code category} — plus, for {@code DONE}, how many of those issues have no resolution and would
     * therefore be open in the Done column.
     */
    @GetMapping("/{statusId}/category-impact")
    public StatusCategoryImpact categoryImpact(
        @PathVariable String statusId, @RequestParam("category") StatusCategory category) {

        return statusWriteService.categoryImpact(statusId, category);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public StatusResponse create(@Valid @RequestBody StatusRequest request) {
        return statusWriteService.create(request);
    }

    @PutMapping("/{statusId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public StatusResponse update(@PathVariable String statusId, @Valid @RequestBody StatusRequest request) {
        return statusWriteService.update(statusId, request);
    }

    @DeleteMapping("/{statusId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String statusId) {
        statusWriteService.delete(statusId);
    }

}
