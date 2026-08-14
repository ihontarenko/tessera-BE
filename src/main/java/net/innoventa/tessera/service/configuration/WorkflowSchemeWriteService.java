package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.WorkflowScheme;
import net.innoventa.tessera.domain.WorkflowSchemeItem;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeRequest;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeRequest.Mapping;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.WorkflowRepository;
import net.innoventa.tessera.repository.WorkflowSchemeItemRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Writing workflow schemes — which workflow an issue runs on, per type, with a fallback.
 *
 * <p>Written whole for the same reason issue-type schemes are, though the rule it protects is a
 * different one: a scheme's overrides and its default are read together on every transition, and a
 * payload that could set one without the other is a payload that can produce a scheme mapping a type to
 * a workflow that was deleted two requests ago.
 *
 * <p>⚠️ <strong>A type with no override is not missing anything.</strong> The default is the answer for
 * every type that has no entry, which is what makes a scheme with an empty mapping list a perfectly
 * ordinary scheme rather than a broken one. Overrides are the exception, and the model keeps them that
 * way rather than materialising a row per type.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkflowSchemeWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSchemeWriteService.class);

    private static final String KIND = "workflow scheme";

    private final WorkflowSchemeRepository     workflowSchemeRepository;
    private final WorkflowSchemeItemRepository workflowSchemeItemRepository;
    private final WorkflowRepository           workflowRepository;
    private final IssueTypeRepository          issueTypeRepository;
    private final ConfigurationUsage           configurationUsage;
    private final Supplier<String>             idGenerator;

    public WorkflowSchemeResponse create(WorkflowSchemeRequest request) {
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(
            workflowSchemeRepository.existsByNameIgnoreCase(name), KIND, name);

        List<Mapping> mappings = requireMappings(request);

        WorkflowScheme scheme = workflowSchemeRepository.save(WorkflowScheme.builder()
            .id(idGenerator.get())
            .name(name)
            .defaultWorkflowId(request.defaultWorkflowId())
            .description(request.description())
            .build());

        replaceItems(scheme.getId(), mappings);

        LOGGER.info("Workflow scheme '{}' created with {} per-type {}", name, mappings.size(),
            mappings.size() == 1 ? "override" : "overrides");

        return toResponse(scheme, mappings);
    }

    public WorkflowSchemeResponse update(String schemeId, WorkflowSchemeRequest request) {
        WorkflowScheme scheme = requireScheme(schemeId);
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(
            workflowSchemeRepository.existsByNameIgnoreCaseAndIdNot(name, schemeId), KIND, name);

        List<Mapping> mappings = requireMappings(request);

        if (!scheme.getDefaultWorkflowId().equals(request.defaultWorkflowId())) {
            LOGGER.info("Workflow scheme '{}' falls back to a different workflow — every type without an "
                        + "override changes what it may transition through, in {} projects", name,
                configurationUsage.projectsOnWorkflowScheme(schemeId).size());
        }

        scheme.setName(name);
        scheme.setDefaultWorkflowId(request.defaultWorkflowId());
        scheme.setDescription(request.description());
        replaceItems(schemeId, mappings);

        return toResponse(scheme, mappings);
    }

    public void delete(String schemeId) {
        WorkflowScheme scheme = requireScheme(schemeId);

        SchemeRules.requireKindSurvives(workflowSchemeRepository.count() - 1, KIND);
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofWorkflowScheme(schemeId), KIND, scheme.getName());

        workflowSchemeItemRepository.deleteAll(workflowSchemeItemRepository.findBySchemeId(schemeId));
        workflowSchemeRepository.delete(scheme);

        LOGGER.info("Workflow scheme '{}' deleted", scheme.getName());
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /** Both ends of every override resolved, and one override per type — see the unique constraint. */
    private List<Mapping> requireMappings(WorkflowSchemeRequest request) {
        List<Mapping> mappings = request.mappings() == null ? List.of() : request.mappings();

        requireWorkflow(request.defaultWorkflowId());
        SchemeRules.requireNoDuplicates(mappings.stream().map(Mapping::issueTypeId).toList(), "issue type");

        mappings.forEach(mapping -> {
            requireWorkflow(mapping.workflowId());

            if (!issueTypeRepository.existsById(mapping.issueTypeId())) {
                throw new BusinessRuleViolationException(
                    "This scheme maps an issue type that does not exist: " + mapping.issueTypeId());
            }
        });

        return mappings;
    }

    /** ⚠️ Replaced, not diffed — see {@code IssueTypeSchemeWriteService.replaceItems}. */
    private void replaceItems(String schemeId, List<Mapping> mappings) {
        workflowSchemeItemRepository.deleteAll(workflowSchemeItemRepository.findBySchemeId(schemeId));
        workflowSchemeItemRepository.flush();

        mappings.forEach(mapping -> workflowSchemeItemRepository.save(WorkflowSchemeItem.builder()
            .id(idGenerator.get())
            .schemeId(schemeId)
            .issueTypeId(mapping.issueTypeId())
            .workflowId(mapping.workflowId())
            .build()));
    }

    private void requireWorkflow(String workflowId) {
        if (!workflowRepository.existsById(workflowId)) {
            throw new BusinessRuleViolationException(
                "This scheme names a workflow that does not exist: " + workflowId);
        }
    }

    private WorkflowScheme requireScheme(String schemeId) {
        return workflowSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow scheme not found: " + schemeId));
    }

    private static WorkflowSchemeResponse toResponse(WorkflowScheme scheme, List<Mapping> mappings) {
        return new WorkflowSchemeResponse(
            scheme.getId(),
            scheme.getName(),
            scheme.getDescription(),
            scheme.getDefaultWorkflowId(),
            mappings.stream()
                .map(mapping -> new WorkflowSchemeResponse.Mapping(
                    mapping.issueTypeId(), mapping.workflowId()))
                .toList());
    }

}
