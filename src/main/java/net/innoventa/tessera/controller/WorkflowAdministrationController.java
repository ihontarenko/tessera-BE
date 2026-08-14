package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.CreateWorkflowRequest;
import net.innoventa.tessera.dto.configuration.RenameRequest;
import net.innoventa.tessera.dto.configuration.TransitionRequest;
import net.innoventa.tessera.dto.configuration.WorkflowBoardImpact;
import net.innoventa.tessera.dto.configuration.WorkflowChangeResponse;
import net.innoventa.tessera.dto.configuration.WorkflowRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.WorkflowInvariants.Verdict;
import net.innoventa.tessera.service.configuration.WorkflowWriteService;
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
 * Editing workflows and the transitions that are their shape.
 *
 * <p>Every write answers with the workflow <em>and</em> what the change did to the boards downstream of
 * it: a workflow is shared by every project whose scheme names it, so a response saying only "saved"
 * would leave the consequence for somebody to discover later.
 *
 * <p>⚠️ There is no route for adding a status to a workflow, and that is not an omission. A workflow's
 * statuses are derived from its transitions — no {@code workflow_statuses} table (ADR-0005) — so
 * "add a status" <em>is</em> {@link #addTransition} with that status as its target.
 */
@RestController
@RequestMapping("/api/admin/configuration/workflows")
@RequiredArgsConstructor
@RequiresAccess
public class WorkflowAdministrationController {

    private final WorkflowWriteService workflowWriteService;
    private final ConfigurationUsage   configurationUsage;

    /** The schemes referencing this workflow and the projects running on them — who a change reaches. */
    @GetMapping("/{workflowId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String workflowId) {
        return configurationUsage.ofWorkflow(workflowId);
    }

    /** Which boards currently show statuses of this workflow nowhere at all. */
    @GetMapping("/{workflowId}/board-impact")
    public WorkflowBoardImpact boardImpact(@PathVariable String workflowId) {
        return workflowWriteService.boardImpact(workflowId);
    }

    /**
     * What removing this edge would warn about — chiefly, a status that would be left with no way out
     * and how many issues are sitting in it.
     */
    @GetMapping("/{workflowId}/transitions/{transitionId}/removal-impact")
    public Verdict transitionRemovalImpact(
        @PathVariable String workflowId, @PathVariable String transitionId) {

        return workflowWriteService.transitionRemovalImpact(workflowId, transitionId);
    }

    /** A workflow and its create transition together — see the request for why they are inseparable. */
    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowChangeResponse create(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowWriteService.create(request);
    }

    @PutMapping("/{workflowId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowChangeResponse update(
        @PathVariable String workflowId, @Valid @RequestBody WorkflowRequest request) {

        return workflowWriteService.update(workflowId, request);
    }

    @DeleteMapping("/{workflowId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String workflowId) {
        workflowWriteService.delete(workflowId);
    }

    @PostMapping("/{workflowId}/transitions")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowChangeResponse addTransition(
        @PathVariable String workflowId, @Valid @RequestBody TransitionRequest request) {

        return workflowWriteService.addTransition(workflowId, request);
    }

    /** A label only. An edge <em>is</em> its endpoints, so retargeting one is removing it and adding another. */
    @PutMapping("/{workflowId}/transitions/{transitionId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowChangeResponse renameTransition(
        @PathVariable String workflowId,
        @PathVariable String transitionId,
        @Valid @RequestBody RenameRequest request) {

        return workflowWriteService.renameTransition(workflowId, transitionId, request);
    }

    @DeleteMapping("/{workflowId}/transitions/{transitionId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public WorkflowChangeResponse removeTransition(
        @PathVariable String workflowId, @PathVariable String transitionId) {

        return workflowWriteService.removeTransition(workflowId, transitionId);
    }

}
