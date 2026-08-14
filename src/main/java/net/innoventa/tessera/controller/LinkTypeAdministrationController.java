package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.LinkTypeRequest;
import net.innoventa.tessera.dto.link.LinkTypeResponse;
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
 * Editing the link-type catalog.
 *
 * <p>⚠️ The one catalog with <strong>no last-row rule</strong>: an installation with no link types is
 * coherent, because a link is optional and zero of them simply means nobody links issues.
 */
@RestController
@RequestMapping("/api/admin/configuration/link-types")
@RequiredArgsConstructor
@RequiresAccess
public class LinkTypeAdministrationController {

    private final FlatCatalogWriteService flatCatalogWriteService;
    private final ConfigurationUsage      configurationUsage;

    @GetMapping("/{linkTypeId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String linkTypeId) {
        return configurationUsage.ofLinkType(linkTypeId);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public LinkTypeResponse create(@Valid @RequestBody LinkTypeRequest request) {
        return flatCatalogWriteService.createLinkType(request);
    }

    @PutMapping("/{linkTypeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public LinkTypeResponse update(@PathVariable String linkTypeId, @Valid @RequestBody LinkTypeRequest request) {
        return flatCatalogWriteService.updateLinkType(linkTypeId, request);
    }

    @DeleteMapping("/{linkTypeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String linkTypeId) {
        flatCatalogWriteService.deleteLinkType(linkTypeId);
    }

}
