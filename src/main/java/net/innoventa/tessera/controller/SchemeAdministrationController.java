package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.InstanceDefaultsRequest;
import net.innoventa.tessera.dto.configuration.InstanceDefaultsResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeRequest;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeResponse;
import net.innoventa.tessera.dto.configuration.SchemeMemberImpact;
import net.innoventa.tessera.dto.configuration.SchemeUsageReport;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeRequest;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.InstanceDefaults;
import net.innoventa.tessera.service.configuration.IssueTypeSchemeWriteService;
import net.innoventa.tessera.service.configuration.WorkflowSchemeWriteService;
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
 * Editing both scheme kinds, and the two defaults a new project starts on.
 *
 * <p>⚠️ <strong>One controller for both kinds, unlike the catalogs.</strong> The catalog controllers are
 * one per family because each family's writes differ — a status has a category question, an issue type
 * has a level one. Schemes have neither: both kinds are written whole, refuse for the same two reasons,
 * and are read by one screen that shows them side by side. Splitting them would produce two files whose
 * only difference is a noun.
 *
 * <p>The defaults live here rather than on their own controller for the same reason they exist at all:
 * they point at schemes, they are what refuses a scheme's deletion, and the screen that edits one edits
 * the other.
 *
 * <p>Reads are open to any signed-in caller, like every configuration read — see
 * {@link ConfigurationAdministrationController} for why. Writes cost
 * {@code configuration:administer} at {@code GLOBAL}.
 */
@RestController
@RequestMapping("/api/admin/configuration")
@RequiredArgsConstructor
@RequiresAccess
public class SchemeAdministrationController {

    private final IssueTypeSchemeWriteService issueTypeSchemeWriteService;
    private final WorkflowSchemeWriteService  workflowSchemeWriteService;
    private final ConfigurationUsage          configurationUsage;
    private final InstanceDefaults            instanceDefaults;

    // ── The blast radius, shown permanently ───────────────────────────────────

    /** Which projects are on which scheme, both kinds at once — the "used by" panel on every card. */
    @GetMapping("/scheme-usage")
    public SchemeUsageReport schemeUsage() {
        return configurationUsage.schemeUsage();
    }

    // ── Issue-type schemes ────────────────────────────────────────────────────

    @GetMapping("/issue-type-schemes/{schemeId}/usage")
    public ConfigurationUsageReport issueTypeSchemeUsage(@PathVariable String schemeId) {
        return configurationUsage.ofIssueTypeScheme(schemeId);
    }

    /** How much work of a type the projects on this scheme hold — asked before it is removed. */
    @GetMapping("/issue-type-schemes/{schemeId}/removal-impact")
    public SchemeMemberImpact removalImpact(
        @PathVariable String schemeId, @RequestParam("issueTypeId") String issueTypeId) {

        return issueTypeSchemeWriteService.removalImpact(schemeId, issueTypeId);
    }

    @PostMapping("/issue-type-schemes")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public IssueTypeSchemeResponse createIssueTypeScheme(@Valid @RequestBody IssueTypeSchemeRequest request) {
        return issueTypeSchemeWriteService.create(request);
    }

    @PutMapping("/issue-type-schemes/{schemeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public IssueTypeSchemeResponse updateIssueTypeScheme(
        @PathVariable String schemeId, @Valid @RequestBody IssueTypeSchemeRequest request) {

        return issueTypeSchemeWriteService.update(schemeId, request);
    }

    @DeleteMapping("/issue-type-schemes/{schemeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void deleteIssueTypeScheme(@PathVariable String schemeId) {
        issueTypeSchemeWriteService.delete(schemeId);
    }

    // ── Workflow schemes ──────────────────────────────────────────────────────

    @GetMapping("/workflow-schemes/{schemeId}/usage")
    public ConfigurationUsageReport workflowSchemeUsage(@PathVariable String schemeId) {
        return configurationUsage.ofWorkflowScheme(schemeId);
    }

    @PostMapping("/workflow-schemes")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowSchemeResponse createWorkflowScheme(@Valid @RequestBody WorkflowSchemeRequest request) {
        return workflowSchemeWriteService.create(request);
    }

    @PutMapping("/workflow-schemes/{schemeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowSchemeResponse updateWorkflowScheme(
        @PathVariable String schemeId, @Valid @RequestBody WorkflowSchemeRequest request) {

        return workflowSchemeWriteService.update(schemeId, request);
    }

    @DeleteMapping("/workflow-schemes/{schemeId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void deleteWorkflowScheme(@PathVariable String schemeId) {
        workflowSchemeWriteService.delete(schemeId);
    }

    // ── What a new project starts on ──────────────────────────────────────────

    @GetMapping("/defaults")
    public InstanceDefaultsResponse defaults() {
        return instanceDefaults.read();
    }

    /**
     * ⚠️ Changes what the <em>next</em> project is created on and nothing else. Every existing project
     * keeps the schemes it has — this is not a bulk edit wearing a settings screen's clothes.
     */
    @PutMapping("/defaults")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public InstanceDefaultsResponse setDefaults(@Valid @RequestBody InstanceDefaultsRequest request) {
        return instanceDefaults.update(request);
    }

}
