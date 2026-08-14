package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.dto.project.SchemeSummary;
import net.innoventa.tessera.dto.project.UpdateProjectRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.dto.configuration.EstimationSchemeResponse;
import net.innoventa.tessera.repository.EstimationSchemeItemRepository;
import net.innoventa.tessera.repository.EstimationSchemeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.MemberRepository;

import net.innoventa.tessera.repository.ProjectRepository;

import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.Roles;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.jpa.AccessAdministration;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.configuration.InstanceDefaults;
import net.innoventa.tessera.service.key.IssueKeyFormat;
import net.innoventa.tessera.service.key.IssueKeyPattern;
import net.innoventa.tessera.service.key.IssueKeyStrategies;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Project provisioning and administration. A new project is seeded with the full default schemes and
 * the creator is added as an Administrator member. Listing is membership-scoped (a member sees only
 * projects they belong to); editing requires {@code ADMINISTER_PROJECT}.
 * <p>
 * Creation used to resolve its schemes through a type -> preset table. With the project type gone
 * (ADR-0015) there is nothing to key that lookup on, so the two defaults are a stored setting —
 * {@link InstanceDefaults} — rather than a classification. Both are ordinary schemes a project may
 * change afterwards through {@link #update}; the setting is a starting point, nothing more.
 *
 * <p>⚠️ <strong>They were string constants in this file until ticket 06.</strong> That was fine while
 * schemes were seeded and read-only, and stopped being fine the moment a screen could delete one: a
 * constant pointing at a deleted row breaks project creation, and the break arrives at whoever next
 * creates a project rather than at the click that caused it.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** What a new project mints until somebody changes it — {@code TIC-1}, the shape Phase 1 shipped. */
    private static final String DEFAULT_KEY_STRATEGY = IssueKeyFormat.PREFIXED_SEQUENCE.name();

    private final ProjectRepository           projectRepository;


    private final IssueTypeSchemeRepository   issueTypeSchemeRepository;
    private final EstimationSchemeRepository  estimationSchemeRepository;
    private final EstimationSchemeItemRepository estimationSchemeItemRepository;
    private final WorkflowSchemeRepository    workflowSchemeRepository;
    private final MemberRepository            memberRepository;
    private final BoardRepository             boardRepository;
    private final MemberService               memberService;
    private final IssueKeyAllocator           issueKeyAllocator;
    private final BoardProvisioner            boardProvisioner;
    private final ProjectAccess               projectAccess;
    private final InstanceDefaults            instanceDefaults;
    private final IssueKeyStrategies          issueKeyStrategies;
    private final AccessAdministration        access;
    private final Supplier<String>            idGenerator;

    @Transactional
    public ProjectResponse create(Jwt jwt, CreateProjectRequest request) {
        return create(memberService.resolveMember(jwt), request);
    }

    /** The same, for a caller that is not an HTTP request — see {@link #list(Member)}. */
    @Transactional
    public ProjectResponse create(Member creator, CreateProjectRequest request) {

        if (projectRepository.existsByKey(request.key())) {
            throw new BusinessRuleViolationException("Project key already in use: " + request.key());
        }

        String leadMemberId = request.leadMemberId() != null
            ? memberService.requireMember(request.leadMemberId()).getId()
            : creator.getId();

        Project project = projectRepository.save(Project.builder()
            .id(idGenerator.get())
            .key(request.key())
            .name(request.name())
            .leadMemberId(leadMemberId)
            // ⚠️ Read from the settings row, not named here. Schemes are editable and deletable now
            // (ticket 06), and a constant naming one is a way to break project creation from the
            // configuration screen — with the break arriving at whoever next creates a project.
            .issueTypeSchemeId(requireIssueTypeScheme(instanceDefaults.issueTypeSchemeId()))
            .workflowSchemeId(requireWorkflowScheme(instanceDefaults.workflowSchemeId()))
            // ⚠️ May be null, which means the project does not estimate — a real answer, and the one a
            // fresh installation gives until somebody chooses a scale.
            .estimationSchemeId(requireEstimationScheme(instanceDefaults.estimationSchemeId()))
            .keyStrategy(DEFAULT_KEY_STRATEGY)
            .build());

        // Seed the per-project issue-key counter now, so issue creation never races to create it.
        issueKeyAllocator.initializeCounter(project.getId());

        // Every project gets a board on creation (ADR-0009), scoped by the answer the caller gave —
        // the single stored representation of "this project plans in sprints" (ADR-0015).
        boardProvisioner.provision(project, request.boardScopeStrategy());

        // ⚠️ The grant IS the membership. There is no longer a `project_memberships` row beside it to
        // keep in step — without this line the creator would own a project they cannot open, because
        // `@RequiresAccess` resolves from the engine's rows and nothing else does.
        access.assign(
            creator.getId(),
            Roles.PROJECT_ADMINISTRATOR,
            Targets.projectScope(project.getId()),
            "PROJECT_CREATED",
            creator.getId(),
            null);

        return toResponse(project, creator);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Jwt jwt) {
        return list(memberService.resolveMember(jwt));
    }

    /**
     * The same, for a caller that is not an HTTP request.
     *
     * <p>⚠️ <strong>The overload exists because the {@code Jwt} was never the question.</strong> Every
     * method here opened by turning one into a {@link Member} and then never looking at it again — so
     * the signature said "this is reachable from a controller and nowhere else", which was true by
     * accident rather than on purpose. A tool handler has a person's identifier and no token; so would
     * a scheduled job, an import, or a test. This is the shape underneath, and the {@code Jwt} version
     * is now the two-line adapter it always was.
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Member member) {
        // ⚠️ Asked of the grants, not of a membership table — so a project reached through a personal
        // grant or an installation-wide role appears here exactly as one reached through membership.
        // `visibleProjectIds` answers an empty list for somebody who browses everything, which is what
        // `browsesEveryProject` is for; that distinction lives in ProjectAccess rather than here.
        List<String> projectIds = projectAccess.browsesEveryProject(member)
            ? projectRepository.findAll().stream().map(Project::getId).toList()
            : projectAccess.visibleProjectIds(member);

        if (projectIds.isEmpty()) {
            return List.of();
        }

        return projectRepository.findByIdInOrderByKeyAsc(projectIds).stream()
            .map(project -> toResponse(project, member))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Jwt jwt, String projectId) {
        return get(memberService.resolveMember(jwt), projectId);
    }

    /** The same, for a caller that is not an HTTP request — see {@link #list(Member)}. */
    @Transactional(readOnly = true)
    public ProjectResponse get(Member member, String projectId) {

        // ⚠️ ADR-0002's isolation is the permission axis's now: a caller who holds nothing at this project
        // is refused with NOT_FOUND_OR_HIDDEN before this method runs, which is the same 404 the check
        // that used to be here produced — with nobody having to remember to write it.
        return toResponse(requireProject(projectId), member);
    }

    @Transactional
    public ProjectResponse update(Jwt jwt, String projectId, UpdateProjectRequest request) {
        Member member = memberService.resolveMember(jwt);
        Project project = requireProject(projectId);

        project.setName(request.name());
        project.setLeadMemberId(memberService.requireMember(request.leadMemberId()).getId());
        project.setIssueTypeSchemeId(requireIssueTypeScheme(request.issueTypeSchemeId()));
        project.setWorkflowSchemeId(requireWorkflowScheme(request.workflowSchemeId()));

        // ⚠️ Changing the scale rewrites NOTHING. Every estimate keeps the number it was stored with,
        // and any that no longer matches an option on the new scale renders as that number — the
        // documented cost of storing weights rather than labels (ADR-0019).
        project.setEstimationSchemeId(requireEstimationScheme(request.estimationSchemeId()));

        // ⚠️ Existing keys are never regenerated — this decides the shape of the NEXT one. The
        // settings screen shows an existing key beside the preview so the divergence is visible
        // before it happens rather than discovered afterwards.
        project.setKeyStrategy(requireKeyStrategy(request.keyStrategy()));
        project.setKeyPattern(requireKeyPattern(request.keyStrategy(), request.keyPattern()));

        return toResponse(project, member);
    }

    /** The project entity or {@code 404} — shared with the membership-administration service. */
    @Transactional(readOnly = true)
    public Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private String requireIssueTypeScheme(String schemeId) {
        return issueTypeSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue type scheme not found: " + schemeId))
            .getId();
    }

    private String requireWorkflowScheme(String schemeId) {
        return workflowSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow scheme not found: " + schemeId))
            .getId();
    }

    private String requireKeyStrategy(String keyStrategy) {
        issueKeyStrategies.resolve(keyStrategy);

        return keyStrategy;
    }

    /**
     * ⚠️ <strong>A custom pattern is validated to contain {@code ${sequence}}.</strong> The counter is
     * the sole source of uniqueness (ADR-0003) and nothing downstream checks a key twice, so a
     * pattern without it would quietly give several issues the same key.
     *
     * <p>Every other format ignores the stored pattern, so switching away from CUSTOM does not
     * require clearing it — and switching back finds it where it was left.
     */
    private static String requireKeyPattern(String keyStrategy, String keyPattern) {
        if (!IssueKeyFormat.CUSTOM.name().equals(keyStrategy)) {
            return keyPattern;
        }

        return IssueKeyPattern.requireSequence(keyPattern);
    }

    /** ⚠️ Null in, null out — "does not estimate" is an answer this method has to be able to give. */
    private String requireEstimationScheme(String schemeId) {
        if (schemeId == null) {
            return null;
        }

        return estimationSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Estimation scheme not found: " + schemeId))
            .getId();
    }

    private ProjectResponse toResponse(Project project, Member caller) {
        MemberSummary lead = memberRepository.findById(project.getLeadMemberId())
            .map(MemberSummary::from)
            .orElse(null);

        SchemeSummary issueTypeScheme = issueTypeSchemeRepository.findById(project.getIssueTypeSchemeId())
            .map(scheme -> new SchemeSummary(scheme.getId(), scheme.getName()))
            .orElse(null);

        SchemeSummary workflowScheme = workflowSchemeRepository.findById(project.getWorkflowSchemeId())
            .map(scheme -> new SchemeSummary(scheme.getId(), scheme.getName()))
            .orElse(null);

        // The whole scale, because every screen showing a story-point value needs the pairs to render a
        // stored weight as the word somebody picked.
        EstimationSchemeResponse estimationScheme = project.getEstimationSchemeId() == null ? null
            : estimationSchemeRepository.findById(project.getEstimationSchemeId())
                .map(scheme -> new EstimationSchemeResponse(
                    scheme.getId(),
                    scheme.getName(),
                    scheme.getDescription(),
                    estimationSchemeItemRepository
                        .findBySchemeIdOrderBySequenceAsc(scheme.getId()).stream()
                        .map(item -> new EstimationSchemeResponse.Item(item.getLabel(), item.getWeight()))
                        .toList()))
                .orElse(null);

        // ⚠️ What the interface gates on, and NEVER the authority. It exists so the board stops offering
        // a button the server is about to refuse; every one of these is still checked on the route.
        List<String> myPermissions = projectAccess.permissionsIn(caller, project.getId())
            .stream()
            .sorted()
            .toList();

        // Whether this project plans in sprints — and therefore whether it reads as Scrum or Kanban —
        // is a property of its board and of nothing else (ADR-0015).
        BoardScopeStrategy boardScopeStrategy = boardRepository.findByProjectId(project.getId())
            .map(Board::getScopeStrategy)
            .orElse(BoardScopeStrategy.ALL_ISSUES);

        return new ProjectResponse(
            project.getId(),
            project.getKey(),
            project.getName(),
            boardScopeStrategy,
            lead,
            issueTypeScheme,
            workflowScheme,
            estimationScheme,
            project.getKeyStrategy(),
            project.getKeyPattern(),
            myPermissions,
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

}
