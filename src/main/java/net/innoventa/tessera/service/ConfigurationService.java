package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.EstimationSchemeItem;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.WorkflowSchemeItem;
import net.innoventa.tessera.dto.configuration.ConfigurationResponse;
import net.innoventa.tessera.dto.configuration.EstimationSchemeResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeResponse;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.CommentTopicResponse;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import net.innoventa.tessera.dto.configuration.TransitionResponse;
import net.innoventa.tessera.dto.configuration.WorkflowResponse;
import net.innoventa.tessera.dto.configuration.WorkflowSchemeResponse;
import net.innoventa.tessera.dto.link.LinkTypeResponse;
import net.innoventa.tessera.mapper.ConfigurationMapper;
import net.innoventa.tessera.repository.EstimationSchemeItemRepository;
import net.innoventa.tessera.repository.EstimationSchemeRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.CommentTopicRepository;
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
    private final CommentTopicRepository commentTopicRepository;
    private final WorkflowRepository workflowRepository;
    private final TransitionRepository transitionRepository;
    private final IssueTypeSchemeRepository issueTypeSchemeRepository;
    private final IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeItemRepository workflowSchemeItemRepository;
    private final EstimationSchemeRepository estimationSchemeRepository;
    private final EstimationSchemeItemRepository estimationSchemeItemRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final ConfigurationMapper configurationMapper;

    public ConfigurationResponse configuration() {
        return new ConfigurationResponse(
            issueTypes(),
            priorities(),
            statusCategories(),
            statuses(),
            resolutions(),
            commentTopics(),
            workflows(),
            issueTypeSchemes(),
            workflowSchemes(),
            estimationSchemes()
        );
    }

    /**
     * Every estimation scale with its options, in one grouped read.
     *
     * <p>⚠️ Both halves of each option travel — the label a person picks and the weight the issue
     * stores. A client resolving a stored number back to a word needs the pairs, and there is nowhere
     * else to get them (ADR-0019).
     */
    public List<EstimationSchemeResponse> estimationSchemes() {
        List<net.innoventa.tessera.domain.EstimationScheme> schemes =
            estimationSchemeRepository.findAllByOrderByNameAsc();

        if (schemes.isEmpty()) {
            return List.of();
        }

        Map<String, List<EstimationSchemeItem>> itemsByScheme = estimationSchemeItemRepository
            .findBySchemeIdInOrderBySequenceAsc(schemes.stream()
                .map(net.innoventa.tessera.domain.EstimationScheme::getId)
                .toList())
            .stream()
            .collect(Collectors.groupingBy(EstimationSchemeItem::getSchemeId));

        return schemes.stream()
            .map(scheme -> new EstimationSchemeResponse(
                scheme.getId(),
                scheme.getName(),
                scheme.getDescription(),
                itemsByScheme.getOrDefault(scheme.getId(), List.of()).stream()
                    .map(item -> new EstimationSchemeResponse.Item(item.getLabel(), item.getWeight()))
                    .toList()))
            .toList();
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

    public List<CommentTopicResponse> commentTopics() {
        return configurationMapper.toCommentTopicResponses(commentTopicRepository.findAllByOrderByNameAsc());
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
