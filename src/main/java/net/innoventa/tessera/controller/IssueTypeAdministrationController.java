package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.IssueTypeLevelImpact;
import net.innoventa.tessera.dto.configuration.IssueTypeRequest;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.IssueTypeWriteService;
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
 * Editing the issue-type catalog.
 *
 * <p>{@link #levelImpact} is this catalog's equivalent of the status category question: the hierarchy
 * level decides what may be a parent of what and what a sprint may plan (ADR-0014), so moving a type
 * between levels is answered before it is done.
 */
@RestController
@RequestMapping("/api/admin/configuration/issue-types")
@RequiredArgsConstructor
@RequiresAccess
public class IssueTypeAdministrationController {

    private final IssueTypeWriteService issueTypeWriteService;
    private final ConfigurationUsage    configurationUsage;

    @GetMapping("/{issueTypeId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String issueTypeId) {
        return configurationUsage.ofIssueType(issueTypeId);
    }

    /** Which existing parent/child pairs a proposed level would invalidate, and what it would unplan. */
    @GetMapping("/{issueTypeId}/level-impact")
    public IssueTypeLevelImpact levelImpact(
        @PathVariable String issueTypeId, @RequestParam("hierarchyLevel") int hierarchyLevel) {

        return issueTypeWriteService.levelImpact(issueTypeId, hierarchyLevel);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public IssueTypeResponse create(@Valid @RequestBody IssueTypeRequest request) {
        return issueTypeWriteService.create(request);
    }

    @PutMapping("/{issueTypeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public IssueTypeResponse update(
        @PathVariable String issueTypeId, @Valid @RequestBody IssueTypeRequest request) {

        return issueTypeWriteService.update(issueTypeId, request);
    }

    @DeleteMapping("/{issueTypeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String issueTypeId) {
        issueTypeWriteService.delete(issueTypeId);
    }

}
