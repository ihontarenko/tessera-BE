package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.issue.CreateIssueRequest;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.UpdateIssueRequest;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.CommentRepository;
import net.innoventa.tessera.repository.IssueComponentRepository;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.IssueVersionRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Issue creation, reading and editing (ticket 07), recording every change to the activity log
 * (ticket 08). Creation allocates a unique key under the per-project counter (ADR-0003), takes the
 * initial status from the issue's workflow (ADR-0005), records the caller as reporter, and appends a
 * LexoRank at the end of the list (ADR-0006). Hierarchy, workflow transitions, organization, links and
 * comments live in their own services; this owns the core fields.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    static final String FIELD_SUMMARY = "summary";
    static final String FIELD_DESCRIPTION = "description";
    static final String FIELD_PRIORITY = "priority";
    static final String FIELD_ASSIGNEE = "assignee";
    static final String FIELD_STORY_POINTS = "storyPoints";
    static final String FIELD_CREATED = "created";

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final IssueVersionRepository issueVersionRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final CommentRepository commentRepository;

    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final WorkflowResolver workflowResolver;
    private final RankService rankService;
    private final IssueKeyAllocator issueKeyAllocator;
    private final IssueHierarchyService issueHierarchyService;
    private final ActivityLogService activityLogService;
    private final IssueCatalog issueCatalog;
    private final IssueAssembler issueAssembler;
    private final Supplier<String> idGenerator;

    @Transactional
    public IssueResponse create(Jwt jwt, String projectId, CreateIssueRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Project project = projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.CREATE_ISSUE);

        requireIssueType(request.issueTypeId());
        requirePriority(request.priorityId());

        String assigneeMemberId = resolveAssignee(caller, projectId, request.assigneeMemberId());
        if (request.parentId() != null) {
            issueHierarchyService.validateParent(request.issueTypeId(), request.parentId(), projectId);
        }

        String workflowId = workflowResolver.resolveWorkflowId(project, request.issueTypeId());
        Status initialStatus = workflowResolver.initialStatus(workflowId);

        IssueKeyAllocator.Allocation allocation = issueKeyAllocator.allocate(project);

        Issue issue = issueRepository.save(Issue.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .sequence(allocation.sequence())
            .issueKey(allocation.issueKey())
            .summary(request.summary())
            .description(request.description())
            .issueTypeId(request.issueTypeId())
            .priorityId(request.priorityId())
            .statusId(initialStatus.getId())
            .resolutionId(null)
            .reporterMemberId(caller.getId())
            .assigneeMemberId(assigneeMemberId)
            .parentId(request.parentId())
            .storyPoints(request.storyPoints())
            .rank(nextRank(projectId))
            .build());

        activityLogService.record(issue.getId(), caller.getId(),
            activityLogService.changeSet().added(FIELD_CREATED, issue.getIssueKey()));

        return issueAssembler.detail(issue, project);
    }

    @Transactional(readOnly = true)
    public List<IssueRowResponse> list(
        Jwt jwt,
        String projectId,
        String statusId,
        String assigneeMemberId,
        String issueTypeId,
        String priorityId
    ) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        List<Issue> issues = issueRepository.findByProjectIdOrderByRankAsc(projectId).stream()
            .filter(issue -> statusId == null || statusId.equals(issue.getStatusId()))
            .filter(issue -> assigneeMemberId == null || assigneeMemberId.equals(issue.getAssigneeMemberId()))
            .filter(issue -> issueTypeId == null || issueTypeId.equals(issue.getIssueTypeId()))
            .filter(issue -> priorityId == null || priorityId.equals(issue.getPriorityId()))
            .toList();

        return issueAssembler.rows(issues);
    }

    @Transactional(readOnly = true)
    public IssueResponse get(Jwt jwt, String issueId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.requireVisible(caller.getId(), issue.getProjectId());

        return issueAssembler.detail(issue, project);
    }

    @Transactional
    public IssueResponse update(Jwt jwt, String issueId, UpdateIssueRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.EDIT_ISSUE);

        requirePriority(request.priorityId());

        boolean assigneeChanges = !Objects.equals(issue.getAssigneeMemberId(), request.assigneeMemberId());
        String assigneeMemberId = assigneeChanges
            ? resolveAssignee(caller, issue.getProjectId(), request.assigneeMemberId())
            : issue.getAssigneeMemberId();

        ActivityLogService.ChangeSet changes = activityLogService.changeSet()
            .compare(FIELD_SUMMARY, issue.getSummary(), request.summary())
            .compare(FIELD_DESCRIPTION, issue.getDescription(), request.description())
            .compare(FIELD_PRIORITY, issueCatalog.priorityName(issue.getPriorityId()), issueCatalog.priorityName(request.priorityId()))
            .compare(FIELD_ASSIGNEE, issueCatalog.memberName(issue.getAssigneeMemberId()), issueCatalog.memberName(assigneeMemberId))
            .compare(FIELD_STORY_POINTS, issueCatalog.storyPoints(issue.getStoryPoints()), issueCatalog.storyPoints(request.storyPoints()));

        issue.setSummary(request.summary());
        issue.setDescription(request.description());
        issue.setPriorityId(request.priorityId());
        issue.setAssigneeMemberId(assigneeMemberId);
        issue.setStoryPoints(request.storyPoints());

        activityLogService.record(issue.getId(), caller.getId(), changes);

        return issueAssembler.detail(issue, project);
    }

    @Transactional
    public void delete(Jwt jwt, String issueId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.DELETE_ISSUE);

        // Detach children so the hierarchy does not dangle, then remove every satellite that FK-references
        // this issue before the issue itself.
        issueRepository.findByParentIdOrderByRankAsc(issueId).forEach(child -> child.setParentId(null));

        issueLabelRepository.deleteByIssueId(issueId);
        issueComponentRepository.deleteByIssueId(issueId);
        issueVersionRepository.deleteByIssueId(issueId);
        issueLinkRepository.deleteAll(issueLinkRepository.findBySourceIssueId(issueId));
        issueLinkRepository.deleteAll(issueLinkRepository.findByTargetIssueId(issueId));
        commentRepository.deleteAll(commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId));
        activityLogService.deleteForIssue(issueId);

        issueRepository.delete(issue);
    }

    /** The issue entity or {@code 404} — shared with the issue sub-feature services. */
    @Transactional(readOnly = true)
    public Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    /** Setting an assignee additionally requires {@code ASSIGN_ISSUE}; the assignee must be a member. */
    private String resolveAssignee(Member caller, String projectId, String assigneeMemberId) {
        if (assigneeMemberId == null) {
            return null;
        }
        projectPermissionService.require(caller, projectId, Permissions.ASSIGN_ISSUE);
        return memberService.requireMember(assigneeMemberId).getId();
    }

    private String nextRank(String projectId) {
        return issueRepository.findFirstByProjectIdOrderByRankDesc(projectId)
            .map(last -> rankService.rankAfter(last.getRank()))
            .orElseGet(rankService::initialRank);
    }

    private void requireIssueType(String issueTypeId) {
        if (!issueTypeRepository.existsById(issueTypeId)) {
            throw new ResourceNotFoundException("Issue type not found: " + issueTypeId);
        }
    }

    private void requirePriority(String priorityId) {
        if (!priorityRepository.existsById(priorityId)) {
            throw new ResourceNotFoundException("Priority not found: " + priorityId);
        }
    }

}
