package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.PriorityRequest;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.ReorderRequest;
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

import java.util.List;

/**
 * Editing the priority catalog.
 *
 * <p>⚠️ <strong>Class-level bare, method-level gated.</strong> The usage read is open for the same reason
 * every configuration read is — a member without the permission sees the screen read-only rather than
 * broken — and each write declares {@code configuration:administer} at {@code GLOBAL}, because a
 * priority belongs to every project at once and no project's administrator can honestly own it.
 */
@RestController
@RequestMapping("/api/admin/configuration/priorities")
@RequiredArgsConstructor
@RequiresAccess
public class PriorityAdministrationController {

    private final FlatCatalogWriteService flatCatalogWriteService;
    private final ConfigurationUsage      configurationUsage;

    /** What holds this row — the same answer the refusal would give, shown before Delete is offered. */
    @GetMapping("/{priorityId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String priorityId) {
        return configurationUsage.ofPriority(priorityId);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public PriorityResponse create(@Valid @RequestBody PriorityRequest request) {
        return flatCatalogWriteService.createPriority(request);
    }

    @PutMapping("/{priorityId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public PriorityResponse update(@PathVariable String priorityId, @Valid @RequestBody PriorityRequest request) {
        return flatCatalogWriteService.updatePriority(priorityId, request);
    }

    @DeleteMapping("/{priorityId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String priorityId) {
        flatCatalogWriteService.deletePriority(priorityId);
    }

    /**
     * The order the picker shows, as a whole list.
     *
     * <p>Reordering is part of editing priorities rather than a separate feature: the sequence is what a
     * picker offers them in, so a catalog whose order cannot be changed is one whose most visible
     * property is fixed.
     */
    @PutMapping("/order")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public List<PriorityResponse> reorder(@Valid @RequestBody ReorderRequest request) {
        return flatCatalogWriteService.reorderPriorities(request.orderedIds());
    }

}
