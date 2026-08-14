package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.ResolutionRequest;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.FlatCatalogWriteService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing the resolution catalog — the reasons an issue may close.
 *
 * <p>Reads open, writes behind {@code configuration:administer} at {@code GLOBAL}, exactly as the
 * priorities controller explains.
 */
@RestController
@RequestMapping("/api/admin/configuration/resolutions")
@RequiredArgsConstructor
@RequiresAccess
public class ResolutionAdministrationController {

    private final FlatCatalogWriteService flatCatalogWriteService;
    private final ConfigurationUsage      configurationUsage;

    @GetMapping("/{resolutionId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String resolutionId) {
        return configurationUsage.ofResolution(resolutionId);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public ResolutionResponse create(@Valid @RequestBody ResolutionRequest request) {
        return flatCatalogWriteService.createResolution(request);
    }

    @PutMapping("/{resolutionId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public ResolutionResponse update(
        @PathVariable String resolutionId, @Valid @RequestBody ResolutionRequest request) {

        return flatCatalogWriteService.updateResolution(resolutionId, request);
    }

    @DeleteMapping("/{resolutionId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String resolutionId) {
        flatCatalogWriteService.deleteResolution(resolutionId);
    }

}
