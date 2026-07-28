package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.configuration.ConfigurationResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeResponse;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import net.innoventa.tessera.dto.configuration.WorkflowResponse;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeResponse;
import net.innoventa.tessera.dto.link.LinkTypeResponse;
import net.innoventa.tessera.service.ConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only exposure of the global configuration catalogs and schemes to any authenticated caller.
 * The aggregate {@code GET /api/configuration} is what the UI loads once; the per-catalog endpoints
 * exist for callers that need just one list.
 */
@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping
    public ConfigurationResponse configuration() {
        return configurationService.configuration();
    }

    @GetMapping("/issue-types")
    public List<IssueTypeResponse> issueTypes() {
        return configurationService.issueTypes();
    }

    @GetMapping("/priorities")
    public List<PriorityResponse> priorities() {
        return configurationService.priorities();
    }

    @GetMapping("/status-categories")
    public List<StatusCategory> statusCategories() {
        return configurationService.statusCategories();
    }

    @GetMapping("/link-types")
    public List<LinkTypeResponse> linkTypes() {
        return configurationService.linkTypes();
    }

    @GetMapping("/statuses")
    public List<StatusResponse> statuses() {
        return configurationService.statuses();
    }

    @GetMapping("/resolutions")
    public List<ResolutionResponse> resolutions() {
        return configurationService.resolutions();
    }

    @GetMapping("/workflows")
    public List<WorkflowResponse> workflows() {
        return configurationService.workflows();
    }

    @GetMapping("/issue-type-schemes")
    public List<IssueTypeSchemeResponse> issueTypeSchemes() {
        return configurationService.issueTypeSchemes();
    }

    @GetMapping("/workflow-schemes")
    public List<WorkflowSchemeResponse> workflowSchemes() {
        return configurationService.workflowSchemes();
    }

}
