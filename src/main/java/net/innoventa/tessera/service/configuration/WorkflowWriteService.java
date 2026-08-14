package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.BoardColumnStatus;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.Workflow;
import net.innoventa.tessera.dto.configuration.CreateWorkflowRequest;
import net.innoventa.tessera.dto.configuration.RenameRequest;
import net.innoventa.tessera.dto.configuration.TransitionRequest;
import net.innoventa.tessera.dto.configuration.TransitionResponse;
import net.innoventa.tessera.dto.configuration.WorkflowBoardImpact;
import net.innoventa.tessera.dto.configuration.WorkflowBoardImpact.StatusRef;
import net.innoventa.tessera.dto.configuration.WorkflowBoardImpact.UnmappedBoard;
import net.innoventa.tessera.dto.configuration.WorkflowChangeResponse;
import net.innoventa.tessera.dto.configuration.WorkflowRequest;
import net.innoventa.tessera.dto.configuration.WorkflowResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.CountByKey;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.repository.TransitionRepository;
import net.innoventa.tessera.repository.WorkflowRepository;
import net.innoventa.tessera.service.BoardColumnResolver;
import net.innoventa.tessera.service.configuration.WorkflowInvariants.Edge;
import net.innoventa.tessera.service.configuration.WorkflowInvariants.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writing workflows and their transitions — the part of the configuration that is a state machine
 * rather than a list.
 *
 * <p>The shape of the work is: load the edges, hand them and the proposed change to
 * {@link WorkflowInvariants}, refuse on a violation, write, and report what the change did to the boards
 * downstream. The decision itself is not here on purpose — see that class for why.
 *
 * <h2>⚠️ Editing is in place, and a workflow is shared</h2>
 *
 * <p>A workflow belongs to every project whose scheme names it, and there is no copy-on-write. Adding an
 * edge changes what everybody on it may do, on the next request. What is offered instead of safety by
 * copying is knowing beforehand: {@link ConfigurationUsage#projectsOnWorkflow} names them, and
 * {@link #boardImpact} says which of their boards would show work nowhere afterwards.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkflowWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowWriteService.class);

    private final WorkflowRepository   workflowRepository;
    private final TransitionRepository transitionRepository;
    private final StatusRepository     statusRepository;
    private final BoardRepository      boardRepository;
    private final BoardMapping         boardMapping;
    private final BoardColumnResolver  boardColumnResolver;
    private final ConfigurationUsage   configurationUsage;
    private final Statuses             statuses;
    private final Supplier<String>     idGenerator;

    // ── The workflow itself ───────────────────────────────────────────────────

    /**
     * A workflow and its create transition, together, in one transaction.
     *
     * <p>⚠️ There is no moment in between. A workflow with no create transition is a workflow that makes
     * issue creation throw for every project on it, so it must not be reachable — not even as a state
     * somebody is about to fix.
     */
    public WorkflowChangeResponse create(CreateWorkflowRequest request) {
        String name = CatalogRules.requireName(request.name(), "workflow");
        CatalogRules.requireNameAvailable(workflowRepository.existsByNameIgnoreCase(name), "workflow", name);

        Status initialStatus = requireStatus(request.initialStatusId());

        Workflow workflow = workflowRepository.save(Workflow.builder()
            .id(idGenerator.get())
            .name(name)
            .description(request.description())
            .build());

        transitionRepository.save(Transition.builder()
            .id(idGenerator.get())
            .workflowId(workflow.getId())
            .fromStatusId(null)
            .toStatusId(initialStatus.getId())
            .name(request.createTransitionNameOrDefault())
            .build());

        LOGGER.info("Workflow '{}' created; new issues on it land in '{}'", name, initialStatus.getName());

        return respond(workflow);
    }

    public WorkflowChangeResponse update(String workflowId, WorkflowRequest request) {
        Workflow workflow = requireWorkflow(workflowId);
        String name = CatalogRules.requireName(request.name(), "workflow");
        CatalogRules.requireNameAvailable(
            workflowRepository.existsByNameIgnoreCaseAndIdNot(name, workflowId), "workflow", name);

        if (!workflow.getName().equals(name)) {
            LOGGER.info("Workflow '{}' renamed to '{}'", workflow.getName(), name);
        }

        workflow.setName(name);
        workflow.setDescription(request.description());

        return respond(workflow);
    }

    /**
     * ⚠️ Refused while any scheme references it, and the refusal names the schemes <em>and their
     * projects</em> — the schemes are what the database would complain about, and the projects are what
     * an administrator actually recognises.
     */
    public void delete(String workflowId) {
        Workflow workflow = requireWorkflow(workflowId);

        CatalogRules.requireCatalogSurvives(workflowRepository.count() - 1, "workflow",
            "every project runs on one through its scheme, so an installation with none cannot create "
            + "an issue anywhere.");
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofWorkflow(workflowId), "workflow", workflow.getName());

        // Its edges go with it. Nothing else references a transition — an issue records the status it
        // reached, never the edge it took.
        transitionRepository.deleteByWorkflowId(workflowId);
        workflowRepository.delete(workflow);

        LOGGER.info("Workflow '{}' deleted with its transitions", workflow.getName());
    }

    // ── Its transitions ───────────────────────────────────────────────────────

    /**
     * Adds an edge — which is also how a status joins this workflow, since membership is derived from
     * the edges and stored nowhere.
     */
    public WorkflowChangeResponse addTransition(String workflowId, TransitionRequest request) {
        Workflow workflow = requireWorkflow(workflowId);
        String name = CatalogRules.requireName(request.name(), "transition");

        Status target = requireStatus(request.toStatusId());
        Status source = request.fromStatusId() == null ? null : requireStatus(request.fromStatusId());

        List<Transition> current = transitionRepository.findByWorkflowId(workflowId);
        Transition proposed = Transition.builder()
            .id(idGenerator.get())
            .workflowId(workflowId)
            .fromStatusId(source == null ? null : source.getId())
            .toStatusId(target.getId())
            .name(name)
            .build();

        List<Transition> resulting = new ArrayList<>(current);
        resulting.add(proposed);

        requirePermitted(judge(current, resulting));

        transitionRepository.save(proposed);

        LOGGER.info("Transition '{}' added to workflow '{}': {} → {}",
            name, workflow.getName(), source == null ? "(new issue)" : source.getName(), target.getName());

        return respond(workflow);
    }

    /** A label change, and nothing an invariant can be about — an edge's identity is its endpoints. */
    public WorkflowChangeResponse renameTransition(String workflowId, String transitionId, RenameRequest request) {
        Workflow workflow = requireWorkflow(workflowId);
        Transition transition = requireTransition(workflowId, transitionId);
        String name = CatalogRules.requireName(request.name(), "transition");

        LOGGER.info("Transition '{}' in workflow '{}' renamed to '{}'",
            transition.getName(), workflow.getName(), name);

        transition.setName(name);

        return respond(workflow);
    }

    public WorkflowChangeResponse removeTransition(String workflowId, String transitionId) {
        Workflow workflow = requireWorkflow(workflowId);
        Transition transition = requireTransition(workflowId, transitionId);

        List<Transition> current = transitionRepository.findByWorkflowId(workflowId);
        List<Transition> resulting = current.stream()
            .filter(candidate -> !candidate.getId().equals(transitionId))
            .toList();

        // ⚠️ Warnings are deliberately not consulted here. Leaving a status terminal is a legitimate
        // thing to do, so it is reported by `transitionRemovalImpact` before the screen offers Remove
        // and obeyed once it is pressed. Only a violation refuses.
        requirePermitted(judge(current, resulting));

        transitionRepository.delete(transition);

        LOGGER.info("Transition '{}' removed from workflow '{}'", transition.getName(), workflow.getName());

        return respond(workflow);
    }

    /** What removing this edge would warn about — the read the screen makes before offering Remove. */
    @Transactional(readOnly = true)
    public Verdict transitionRemovalImpact(String workflowId, String transitionId) {
        requireWorkflow(workflowId);
        requireTransition(workflowId, transitionId);

        List<Transition> current = transitionRepository.findByWorkflowId(workflowId);
        List<Transition> resulting = current.stream()
            .filter(candidate -> !candidate.getId().equals(transitionId))
            .toList();

        return judge(current, resulting);
    }

    // ── What it does to the boards ────────────────────────────────────────────

    /**
     * Which boards belonging to projects on this workflow now show some of its statuses nowhere.
     *
     * <p>The same resolution the board itself renders with (ADR-0010), asked about every status this
     * workflow can reach. A status that resolves to no column is not broken — the backlog holds it
     * (ADR-0016) — but a status that arrived there because a shared workflow grew an edge is a surprise,
     * and this is where somebody finds out.
     */
    @Transactional(readOnly = true)
    public WorkflowBoardImpact boardImpact(String workflowId) {
        List<Status> reachable = reachableStatuses(workflowId);
        List<Project> projects = configurationUsage.projectsOnWorkflow(workflowId);

        if (reachable.isEmpty() || projects.isEmpty()) {
            return WorkflowBoardImpact.none();
        }

        List<UnmappedBoard> unmapped = new ArrayList<>();

        for (Project project : projects) {
            boardRepository.findByProjectId(project.getId())
                .flatMap(board -> unmappedOn(project, board, reachable))
                .ifPresent(unmapped::add);
        }

        return new WorkflowBoardImpact(List.copyOf(unmapped));
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private Optional<UnmappedBoard> unmappedOn(Project project, Board board, List<Status> reachable) {
        BoardMapping.Mapping mapping = boardMapping.of(board.getId());

        List<StatusRef> homeless = reachable.stream()
            .filter(status -> !boardColumnResolver.rendersStatus(
                status, mapping.columns(), mapping.statusToColumn()))
            .map(status -> new StatusRef(status.getId(), status.getName()))
            .toList();

        return homeless.isEmpty()
            ? Optional.empty()
            : Optional.of(new UnmappedBoard(
                project.getId(), project.getKey(), project.getName(), board.getId(), homeless));
    }

    /**
     * Every status this workflow can put an issue in — the targets of its edges.
     *
     * <p>Only targets count, exactly as {@code WorkflowResolver.reachableCategories} decides it: a status
     * nothing leads to is one no issue can occupy, whatever edges leave it.
     */
    private List<Status> reachableStatuses(String workflowId) {
        Set<String> statusIds = transitionRepository.findByWorkflowId(workflowId).stream()
            .map(Transition::getToStatusId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        return statusRepository.findAllById(statusIds);
    }

    /** The pure decision, given the two edge sets and the two lookups it needs to phrase itself. */
    private Verdict judge(List<Transition> current, List<Transition> resulting) {
        Set<String> statusIds = new LinkedHashSet<>();
        current.forEach(edge -> collectStatusIds(edge, statusIds));
        resulting.forEach(edge -> collectStatusIds(edge, statusIds));

        return WorkflowInvariants.check(
            edges(current),
            edges(resulting),
            statuses.namesOf(statusIds),
            configurationUsage.counts().issuesByStatus());
    }

    private static void collectStatusIds(Transition edge, Set<String> into) {
        if (edge.getFromStatusId() != null) {
            into.add(edge.getFromStatusId());
        }

        into.add(edge.getToStatusId());
    }

    private static List<Edge> edges(List<Transition> transitions) {
        return transitions.stream()
            .map(transition -> new Edge(
                transition.getId(),
                transition.getName(),
                transition.getFromStatusId(),
                transition.getToStatusId()))
            .toList();
    }

    /** A violation is a {@code 409} carrying the whole verdict — the warnings travel too, as context. */
    private static void requirePermitted(Verdict verdict) {
        if (verdict.permits()) {
            return;
        }

        throw new BusinessRuleViolationException(verdict.describeViolations(), verdict);
    }

    private WorkflowChangeResponse respond(Workflow workflow) {
        List<TransitionResponse> transitions = transitionRepository.findByWorkflowId(workflow.getId()).stream()
            .map(transition -> new TransitionResponse(
                transition.getId(),
                transition.getName(),
                transition.getFromStatusId(),
                transition.getToStatusId()))
            .toList();

        return new WorkflowChangeResponse(
            new WorkflowResponse(workflow.getId(), workflow.getName(), workflow.getDescription(), transitions),
            boardImpact(workflow.getId()));
    }

    private Workflow requireWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
    }

    /**
     * The transition, <strong>and it belongs to this workflow</strong>.
     *
     * <p>The workflow is in the path, so an id from another one would otherwise be edited through a URL
     * that says it is not being.
     */
    private Transition requireTransition(String workflowId, String transitionId) {
        return transitionRepository.findById(transitionId)
            .filter(transition -> transition.getWorkflowId().equals(workflowId))
            .orElseThrow(() -> new ResourceNotFoundException(
                "Transition not found in this workflow: " + transitionId));
    }

    private Status requireStatus(String statusId) {
        return statuses.require(statusId);
    }

}
