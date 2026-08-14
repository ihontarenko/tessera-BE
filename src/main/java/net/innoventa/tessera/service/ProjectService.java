package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.domain.ProjectRole;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.dto.project.SchemeSummary;
import net.innoventa.tessera.dto.project.UpdateProjectRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.repository.ProjectRoleRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.LocalAuthorizationMirror;
import net.innoventa.tessera.security.access.ProjectAccess;
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
 * (ADR-0015) there is nothing to key that lookup on, so the defaults are simply named here. Both are
 * ordinary schemes a project may change afterwards through {@link #update}; naming them is a starting
 * point, not a classification. The lighter {@code scheme-issue-type-todo} / {@code workflow-todo} pair
 * survives in the catalog and is reachable the same way.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final String DEFAULT_KEY_STRATEGY = "PREFIXED_SEQUENCE";
    private static final String ADMINISTRATOR_ROLE_NAME = "Administrator";
    private static final String DEFAULT_ISSUE_TYPE_SCHEME_ID = "scheme-issue-type-default";
    private static final String DEFAULT_WORKFLOW_SCHEME_ID = "scheme-workflow-default";

    private final ProjectRepository           projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectRoleRepository       projectRoleRepository;
    private final IssueTypeSchemeRepository   issueTypeSchemeRepository;
    private final WorkflowSchemeRepository    workflowSchemeRepository;
    private final MemberRepository            memberRepository;
    private final BoardRepository             boardRepository;
    private final MemberService               memberService;
    private final IssueKeyAllocator           issueKeyAllocator;
    private final BoardProvisioner            boardProvisioner;
    private final ProjectAccess               projectAccess;
    private final LocalAuthorizationMirror    grants;
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
            .issueTypeSchemeId(requireIssueTypeScheme(DEFAULT_ISSUE_TYPE_SCHEME_ID))
            .workflowSchemeId(requireWorkflowScheme(DEFAULT_WORKFLOW_SCHEME_ID))
            .keyStrategy(DEFAULT_KEY_STRATEGY)
            .build());

        // Seed the per-project issue-key counter now, so issue creation never races to create it.
        issueKeyAllocator.initializeCounter(project.getId());

        // Every project gets a board on creation (ADR-0009), scoped by the answer the caller gave —
        // the single stored representation of "this project plans in sprints" (ADR-0015).
        boardProvisioner.provision(project, request.boardScopeStrategy());

        ProjectRole administrator = projectRoleRepository.findByName(ADMINISTRATOR_ROLE_NAME)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator role not seeded"));

        membershipRepository.save(ProjectMembership.builder()
            .id(idGenerator.get())
            .projectId(project.getId())
            .memberId(creator.getId())
            .roleId(administrator.getId())
            .build());

        // ⚠️ The grant that actually decides anything. Without this line the creator would own a project
        // they cannot open: `@RequiresAccess` resolves from the engine's rows, and a membership row is
        // no longer one of them.
        grants.assignRole(
            creator.getId(), project.getId(), ADMINISTRATOR_ROLE_NAME, creator.getId());

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
        List<String> projectIds = membershipRepository.findByMemberId(member.getId()).stream()
            .map(ProjectMembership::getProjectId)
            .distinct()
            .toList();

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
            project.getKeyStrategy(),
            project.getKeyPattern(),
            myPermissions,
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

}
