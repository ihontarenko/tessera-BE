package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.WorkflowSchemeItem;
import net.innoventa.tessera.dto.configuration.ConfigurationResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeResponse;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import net.innoventa.tessera.dto.configuration.TransitionResponse;
import net.innoventa.tessera.dto.configuration.WorkflowResponse;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeResponse;
import net.innoventa.tessera.dto.link.LinkTypeResponse;
import net.innoventa.tessera.mapper.ConfigurationMapper;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.repository.TransitionRepository;
import net.innoventa.tessera.repository.WorkflowRepository;
import net.innoventa.tessera.repository.WorkflowSchemeItemRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only access to the global, reusable configuration (ADR-0001). Everything here is seeded by
 * migration; Phase 1 exposes it but writes none of it (catalog/scheme editing is a later,
 * {@code ADMIN}-gated phase). The join-backed shapes — workflows with their transitions, schemes with
 * their items — are batch-loaded and grouped in memory to avoid a query per row.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfigurationService {

    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final StatusRepository statusRepository;
    private final ResolutionRepository resolutionRepository;
    private final WorkflowRepository workflowRepository;
    private final TransitionRepository transitionRepository;
    private final IssueTypeSchemeRepository issueTypeSchemeRepository;
    private final IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeItemRepository workflowSchemeItemRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final ConfigurationMapper configurationMapper;

    public ConfigurationResponse configuration() {
        return new ConfigurationResponse(
            issueTypes(),
            priorities(),
            statusCategories(),
            statuses(),
            resolutions(),
            workflows(),
            issueTypeSchemes(),
            workflowSchemes()
        );
    }

    public List<IssueTypeResponse> issueTypes() {
        return configurationMapper.toIssueTypeResponses(issueTypeRepository.findAllByOrderByHierarchyLevelDescNameAsc());
    }

    public List<PriorityResponse> priorities() {
        return configurationMapper.toPriorityResponses(priorityRepository.findAllByOrderBySequenceAsc());
    }

    public List<StatusCategory> statusCategories() {
        return List.of(StatusCategory.values());
    }

    public List<LinkTypeResponse> linkTypes() {
        return linkTypeRepository.findAllByOrderByNameAsc().stream()
            .map(LinkTypeResponse::from)
            .toList();
    }

    public List<StatusResponse> statuses() {
        return configurationMapper.toStatusResponses(statusRepository.findAllByOrderByNameAsc());
    }

    public List<ResolutionResponse> resolutions() {
        return configurationMapper.toResolutionResponses(resolutionRepository.findAllByOrderByNameAsc());
    }

    public List<WorkflowResponse> workflows() {
        List<net.innoventa.tessera.domain.Workflow> workflows = workflowRepository.findAllByOrderByNameAsc();

        Map<String, List<Transition>> transitionsByWorkflow = transitionRepository
            .findByWorkflowIdIn(workflows.stream().map(net.innoventa.tessera.domain.Workflow::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(Transition::getWorkflowId));

        return workflows.stream()
            .map(workflow -> new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                transitionsByWorkflow.getOrDefault(workflow.getId(), List.of()).stream()
                    .map(transition -> new TransitionResponse(
                        transition.getId(),
                        transition.getName(),
                        transition.getFromStatusId(),
                        transition.getToStatusId()))
                    .toList()))
            .toList();
    }

    public List<IssueTypeSchemeResponse> issueTypeSchemes() {
        List<net.innoventa.tessera.domain.IssueTypeScheme> schemes = issueTypeSchemeRepository.findAllByOrderByNameAsc();

        Map<String, List<IssueTypeSchemeItem>> itemsByScheme = issueTypeSchemeItemRepository
            .findBySchemeIdInOrderBySequenceAsc(schemes.stream().map(net.innoventa.tessera.domain.IssueTypeScheme::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(IssueTypeSchemeItem::getSchemeId));

        return schemes.stream()
            .map(scheme -> new IssueTypeSchemeResponse(
                scheme.getId(),
                scheme.getName(),
                scheme.getDescription(),
                scheme.getDefaultIssueTypeId(),
                itemsByScheme.getOrDefault(scheme.getId(), List.of()).stream()
                    .map(IssueTypeSchemeItem::getIssueTypeId)
                    .toList()))
            .toList();
    }

    public List<WorkflowSchemeResponse> workflowSchemes() {
        List<net.innoventa.tessera.domain.WorkflowScheme> schemes = workflowSchemeRepository.findAllByOrderByNameAsc();

        Map<String, List<WorkflowSchemeItem>> itemsByScheme = workflowSchemeItemRepository
            .findBySchemeIdIn(schemes.stream().map(net.innoventa.tessera.domain.WorkflowScheme::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(WorkflowSchemeItem::getSchemeId));

        return schemes.stream()
            .map(scheme -> new WorkflowSchemeResponse(
                scheme.getId(),
                scheme.getName(),
                scheme.getDescription(),
                scheme.getDefaultWorkflowId(),
                itemsByScheme.getOrDefault(scheme.getId(), List.of()).stream()
                    .map(item -> new WorkflowSchemeResponse.Mapping(item.getIssueTypeId(), item.getWorkflowId()))
                    .toList()))
            .toList();
    }

}
