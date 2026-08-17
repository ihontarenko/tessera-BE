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
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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
    private final PriorityRepository priorityRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final CommentRepository commentRepository;

    private final ProjectService projectService;
    private final ProjectIssueTypeService projectIssueTypeService;
    private final ProjectAccess projectAccess;
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
        return create(memberService.resolveMember(jwt), projectId, request);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse create(Member caller, String projectId, CreateIssueRequest request) {
        Project project = projectService.requireProject(projectId);

        // The scheme is a constraint, not a suggestion: the dialog offers only what it grants, and a
        // caller reaching the API directly is held to the same list. Before the key is allocated, so a
        // refused create never burns a number out of the project's sequence.
        projectIssueTypeService.requireCreatable(project, request.issueTypeId());
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

        return issueAssembler.detail(issue, project, caller);
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
        memberService.resolveMember(jwt);

        return list(projectId, statusId, assigneeMemberId, issueTypeId, priorityId);
    }

    /**
     * The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}.
     *
     * <p>⚠️ <strong>It takes no caller at all</strong>, unlike its neighbours, because it never used
     * one: the listing is the project's and the same for everybody who may see it. An unused parameter
     * added for symmetry would be a parameter somebody eventually assumes is doing something.
     */
    @Transactional(readOnly = true)
    public List<IssueRowResponse> list(
        String projectId,
        String statusId,
        String assigneeMemberId,
        String issueTypeId,
        String priorityId
    ) {
        projectService.requireProject(projectId);

        // Archived issues are out of the project's list (TSSR-4). This listing has no "show archived"
        // switch on purpose: the Shipped screen is where put-away work is read, and search is where it is
        // found — a third answer here would be a second archive screen nobody asked for.
        List<Issue> issues = issueRepository.findByProjectIdAndArchivedAtIsNullOrderByRankAsc(projectId).stream()
            .filter(issue -> statusId == null || statusId.equals(issue.getStatusId()))
            .filter(issue -> assigneeMemberId == null || assigneeMemberId.equals(issue.getAssigneeMemberId()))
            .filter(issue -> issueTypeId == null || issueTypeId.equals(issue.getIssueTypeId()))
            .filter(issue -> priorityId == null || priorityId.equals(issue.getPriorityId()))
            .toList();

        return issueAssembler.rows(issues);
    }

    @Transactional(readOnly = true)
    public IssueResponse get(Jwt jwt, String issueId) {
        return detail(requireIssue(issueId), memberService.resolveMember(jwt));
    }

    /**
     * The same read addressed by issue key, for the issue page — whose URL is the key, because the key
     * is what people already paste to each other (ticket 07). Keys are stored uppercase, so the lookup
     * uppercases its argument rather than relying on a collation: MySQL would match either way and
     * PostgreSQL would not.
     */
    @Transactional(readOnly = true)
    public IssueResponse getByKey(Jwt jwt, String issueKey) {
        return getByKey(issueKey, memberService.resolveMember(jwt));
    }

    /**
     * The same, for a caller that is not an HTTP request.
     *
     * <p>⚠️ <strong>It takes a caller now, and it used not to</strong> (TSSR-43). The old reasoning was
     * that an issue reads the same to everybody who may read it at all, since who that is had already
     * been decided by {@code @RequiresAccess} or by the tool dispatcher. That held while every reference
     * an issue carried lived in its own project. Links do not: a tracking hub names work anywhere, so
     * part of this answer now depends on which projects the reader belongs to.
     */
    @Transactional(readOnly = true)
    public IssueResponse getByKey(String issueKey, Member caller) {
        Issue issue = issueRepository.findByIssueKey(issueKey.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueKey));

        return detail(issue, caller);
    }

    /**
     * Both reads end the same way, and deliberately share it rather than each writing it out: whichever
     * way an issue is addressed, the answer is assembled once.
     */
    private IssueResponse detail(Issue issue, Member caller) {
        Project project = projectService.requireProject(issue.getProjectId());

        return issueAssembler.detail(issue, project, caller);
    }

    @Transactional
    public IssueResponse update(Jwt jwt, String issueId, UpdateIssueRequest request) {
        return update(memberService.resolveMember(jwt), issueId, request);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse update(Member caller, String issueId, UpdateIssueRequest request) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

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

        return issueAssembler.detail(issue, project, caller);
    }

    @Transactional
    public void delete(Jwt jwt, String issueId) {
        memberService.resolveMember(jwt);

        delete(issueId);
    }

    /**
     * The same, for a caller that is not an HTTP request.
     *
     * <p>⚠️ No caller parameter: deleting an issue records nothing about who did it — the activity log
     * goes with the issue — so one would be a parameter nothing reads. Who may is decided before this,
     * on the route or by the dispatcher.
     */
    @Transactional
    public void delete(String issueId) {
        Issue issue = requireIssue(issueId);

        // Detach children so the hierarchy does not dangle, then remove every satellite that FK-references
        // this issue before the issue itself.
        issueRepository.findByParentIdOrderByRankAsc(issueId).forEach(child -> child.setParentId(null));

        issueLabelRepository.deleteByIssueId(issueId);
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

    /**
     * The issue an <em>issue key</em> names, provided it belongs to {@code projectId} — the lookup every
     * drag-and-drop endpoint does, since a card and a backlog row are both addressed by key. An issue in
     * another project is reported as not found rather than forbidden: a caller scoped to one project has
     * no business learning that a key exists elsewhere.
     */
    @Transactional(readOnly = true)
    public Issue requireIssueInProject(String issueKey, String projectId) {
        Issue issue = issueRepository.findByIssueKey(issueKey)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueKey));

        if (!issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Issue not found: " + issueKey);
        }

        return issue;
    }

    /**
     * The rank of the issue a drop landed next to, or null when that side of the list is open — the
     * pair of bounds {@link RankService#between} takes. Shared by the board's move and the backlog's,
     * which express a drop the same way: the two neighbours the member could actually see.
     */
    @Transactional(readOnly = true)
    public String neighbourRank(String neighbourIssueKey, String projectId) {
        return neighbourIssueKey == null ? null : requireIssueInProject(neighbourIssueKey, projectId).getRank();
    }

    /**
     * Setting an assignee additionally requires {@code ASSIGN_ISSUE}; the assignee must be a member.
     *
     * <p>⚠️ <strong>One of the few checks that stays in a service.</strong> It is not the route's
     * permission — creating and editing an issue cost their own — it is a <em>further</em> one, owed only
     * when the request happens to name somebody. An annotation names one permission for the whole call,
     * so expressing this there would mean either refusing every edit to a member who may not assign, or
     * letting an assignment through under {@code EDIT_ISSUE}.
     */
    private String resolveAssignee(Member caller, String projectId, String assigneeMemberId) {
        if (assigneeMemberId == null) {
            return null;
        }
        projectAccess.require(caller, projectId, Permissions.ASSIGN_ISSUE);
        return memberService.requireMember(assigneeMemberId).getId();
    }

    private String nextRank(String projectId) {
        return issueRepository.findFirstByProjectIdOrderByRankDesc(projectId)
            .map(last -> rankService.rankAfter(last.getRank()))
            .orElseGet(rankService::initialRank);
    }

    private void requirePriority(String priorityId) {
        if (!priorityRepository.existsById(priorityId)) {
            throw new ResourceNotFoundException("Priority not found: " + priorityId);
        }
    }

}
