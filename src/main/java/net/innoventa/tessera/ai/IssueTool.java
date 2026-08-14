package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.dto.issue.CreateIssueRequest;
import net.innoventa.tessera.dto.issue.IssueRef;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.TransitionOption;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.dto.issue.UpdateIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.CommentService;
import net.innoventa.tessera.service.IssueSearchService;
import net.innoventa.tessera.service.IssueService;
import net.innoventa.tessera.service.ProjectService;
import net.innoventa.tessera.service.TransitionService;
import net.innoventa.tessera.service.WorkflowResolver;
import org.jmouse.ai.AffectedRecords;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ScopeConfinement;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.jmouse.ai.ToolRefusedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finding issues, and moving one through the workflow.
 *
 * <p><strong>{@code transition} is the case this whole adoption was worth doing for.</strong> Every
 * other action in every product so far is a read or a field write; this one asks a domain engine for
 * permission and can be told no for reasons that have nothing to do with authorization — the scheme
 * has no edge from here to there, or the target is a Done status and no resolution was given.
 *
 * <p>⚠️ <strong>A domain refusal has to reach the model as something it can act on.</strong> The
 * workflow engine's own sentence — <em>"Illegal transition: no edge to 'Done' from the issue's current
 * status"</em> — is true, correct, and useless to a model: it says what failed and not what would
 * work, so the next call is another guess. {@link #refuseWithWhatIsPossible} turns it into a refusal
 * naming the transitions that <em>are</em> available from where the issue actually is. That is the
 * finding, and the fix belongs here rather than in the engine: a person reading a 409 in the interface
 * has the board in front of them and can see the answer.
 */
@Component
@RequiredArgsConstructor
public class IssueTool implements ToolDefinition {

    private static final int DEFAULT_LIMIT = 25;

    private final IssueSearchService searchService;
    private final IssueService       issueService;
    private final CommentService     commentService;
    private final TransitionService  transitionService;
    private final WorkflowResolver   workflowResolver;
    private final ProjectService     projectService;
    private final IssueRepository    issueRepository;
    private final ToolMembers        members;
    private final ToolCatalogs       catalogs;

    @Override
    public String toolName() {
        return "issues";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(search(), list(), get(), create(), update(), transition(), comment(), delete());
    }

    // ── The project's own list ───────────────────────────────────────────────────

    /**
     * ⚠️ <strong>Beside {@code search}, not instead of it, and the difference is worth keeping.</strong>
     * Search answers "which issues match this" across a page of results, newest first; this answers
     * "what is in this project, in the order the team ranked it". A backlog is an ordered list and its
     * order is the information — flattening the two would lose it.
     */
    private ToolAction list() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("list")
                .title("List a project's issues")
                .description("Lists a project's issues in the team's own ranked order — the order the "
                           + "backlog and the board show. Narrow by who they are assigned to. Use "
                           + "issues_search instead when looking for words rather than reading a list.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .optionalString("assigneeMemberId",
                                "Restrict to one person's issues, by the member id a previous answer "
                              + "reported. Omit for everyone's.")
                        .limit(DEFAULT_LIMIT))
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .scopeConfined()
                .handler(this::handleList)
                .build();
    }

    private Object handleList(ToolInvocation invocation) {
        List<IssueRowResponse> issues = issueService.list(
                invocation.scopeId(),
                null,
                invocation.optionalString("assigneeMemberId").orElse(null),
                null,
                null);

        int limit = invocation.limitArgument(DEFAULT_LIMIT);

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("total",  issues.size());
        answer.put("issues", issues.stream().limit(limit).map(this::describe).toList());

        if (issues.size() > limit) {
            answer.put("note", "Showing the first " + limit + " of " + issues.size() + " in ranked "
                             + "order. Raise 'limit' up to " + ToolInvocation.MAXIMUM_LIMIT + ".");
        }

        return answer;
    }

    // ── One issue, in full ───────────────────────────────────────────────────────

    private ToolAction get() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("get")
                .title("Read one issue")
                .description("Reads one issue in full — its description, its status, who reported and "
                           + "who it is assigned to, its labels and its links. Read this before "
                           + "editing one, because an update replaces the fields it is given.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to read, e.g. TES-42."))
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .scopeConfined()
                .handler(this::handleGet)
                .build();
    }

    private Object handleGet(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        return describeInFull(issueService.getByKey(issue.getIssueKey()));
    }

    /**
     * The whole issue, minus every identifier.
     *
     * <p>⚠️ <strong>Narrowed for the reason {@code ProjectTool.describe} is</strong>, and more so: the
     * detail response carries a project id, a rank, a scheme's worth of summary objects and each child's
     * identifier. All of it real, none of it anything a model asked about, and each one a string it
     * might then hand to an action that takes a different kind of identifier entirely.
     *
     * <p>{@code availableTransitions} is the exception worth keeping, because it is the answer to the
     * question the next call is going to ask.
     */
    private Map<String, Object> describeInFull(IssueResponse issue) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",         issue.issueKey());
        described.put("summary",     issue.summary());
        described.put("description", issue.description());
        described.put("status",      issue.status() == null ? null : issue.status().name());
        described.put("type",        issue.type() == null ? null : issue.type().name());
        described.put("priority",    issue.priority() == null ? null : issue.priority().name());
        described.put("open",        issue.open());
        described.put("storyPoints", issue.storyPoints());
        described.put("labels",      issue.labels());

        if (issue.reporter() != null) {
            described.put("reporter", issue.reporter().displayName());
        }
        if (issue.assignee() != null) {
            described.put("assignee", issue.assignee().displayName());
        }
        if (issue.parent() != null) {
            described.put("parent", issue.parent().issueKey());
        }
        if (!issue.children().isEmpty()) {
            described.put("children", issue.children().stream().map(IssueRef::issueKey).toList());
        }

        described.put("canMoveTo", issue.availableTransitions().stream()
                .map(TransitionOption::toStatusName)
                .toList());

        return described;
    }

    // ── Raising one ──────────────────────────────────────────────────────────────

    private ToolAction create() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("create")
                .title("Raise an issue")
                .description("Raises an issue in a project, with the caller as its reporter. The type "
                           + "and priority are named rather than identified — 'Story', 'High' — and a "
                           + "name the project does not offer is refused with the list that would have "
                           + "worked. Omit either to take the project's default.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("summary", "One line saying what the issue is. This is what "
                                      + "everybody reads on a board card.")
                        .optionalString("description", "The detail, as Markdown. Omit for none.")
                        .optionalString("type", "Issue type by name — Story, Bug, Task. Omit for the "
                                      + "project's default.")
                        .optionalString("priority", "Priority by name — High, Medium. Omit for the "
                                      + "lowest-ranked one.")
                        .optionalNumber("storyPoints", "An estimate, where the team uses them.")
                        .confirm())
                .requiredPermission(Permissions.CREATE_ISSUE)
                .scopeConfined()
                .handler(this::handleCreate)
                .build();
    }

    private Object handleCreate(ToolInvocation invocation) {
        Project project = projectService.requireProject(invocation.scopeId());

        IssueResponse created = issueService.create(
                members.actingSubject(invocation),
                project.getId(),
                new CreateIssueRequest(
                        invocation.requiredString("summary"),
                        invocation.optionalString("description").orElse(null),
                        catalogs.issueTypeIdFor(project, invocation.optionalString("type").orElse(null)),
                        catalogs.priorityIdFor(invocation.optionalString("priority").orElse(null)),
                        // ⚠️ Never assigned on creation. Assigning costs ASSIGN_ISSUE on top of
                        // CREATE_ISSUE, and a tool that quietly needed a second permission would be
                        // refused for a reason its own description never mentioned.
                        null,
                        null,
                        invocation.optionalNumber("storyPoints").orElse(null)));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("created",  true);
        answer.put("issueKey", created.issueKey());
        answer.put("status",   created.status() == null ? null : created.status().name());

        return answer;
    }

    // ── Editing one ──────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>A replace, not a patch, and the description has to say so.</strong>
     * {@code UpdateIssueRequest} takes the whole editable set and writes all of it — a summary left out
     * is a summary blanked. So this reads the issue first and fills in whatever the call did not name,
     * which turns the domain's replace into the patch a model expects without changing what the domain
     * means.
     */
    private ToolAction update() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("update")
                .title("Edit an issue")
                .description("Changes one issue's fields. Anything not named is left as it is. To "
                           + "change the status use issues_transition instead — a status is not a "
                           + "field, it is a move the workflow has to allow.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to change, e.g. TES-42.")
                        .optionalString("summary", "A new one-line summary.")
                        .optionalString("description", "A new description, as Markdown.")
                        .optionalString("priority", "A new priority, by name.")
                        .optionalNumber("storyPoints", "A new estimate.")
                        .confirm())
                .requiredPermission(Permissions.EDIT_ISSUE)
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleUpdate)
                .build();
    }

    private Object handleUpdate(ToolInvocation invocation) {
        Issue         issue    = requireIssue(invocation, invocation.requiredString("issueKey"));
        IssueResponse existing = issueService.getByKey(issue.getIssueKey());

        String priority = invocation.optionalString("priority")
                .map(catalogs::priorityIdFor)
                .orElse(issue.getPriorityId());

        IssueResponse updated = issueService.update(
                members.actingSubject(invocation),
                issue.getId(),
                new UpdateIssueRequest(
                        invocation.optionalString("summary").orElse(existing.summary()),
                        invocation.optionalString("description").orElse(existing.description()),
                        priority,
                        // ⚠️ Carried through rather than cleared. Assigning is ASSIGN_ISSUE's, and an
                        // edit that silently unassigned somebody would be the worst kind of surprise:
                        // correct, permitted, and nobody asked for it.
                        issue.getAssigneeMemberId(),
                        invocation.optionalNumber("storyPoints").orElse(issue.getStoryPoints())));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("updated",  true);
        answer.put("issueKey", updated.issueKey());

        return answer;
    }

    // ── Saying something about one ───────────────────────────────────────────────

    private ToolAction comment() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("comment")
                .title("Comment on an issue")
                .description("Adds a comment to an issue, attributed to the caller. Comments are how a "
                           + "decision gets recorded where the people working on the issue will see it.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to comment on, e.g. TES-42.")
                        .requiredString("body", "What to say, as Markdown."))
                .requiredPermission(Permissions.ADD_COMMENT)
                .scopeConfined()
                .handler(this::handleComment)
                .build();
    }

    private Object handleComment(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        commentService.add(
                members.actingSubject(invocation),
                issue.getId(),
                new SaveCommentRequest(invocation.requiredString("body")));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("commented", true);
        answer.put("issueKey",  issue.getIssueKey());

        return answer;
    }

    // ── Removing one ─────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>The one destructive action Tessera publishes.</strong> Deleting an issue takes its
     * comments, its links, its labels and its history with it, and detaches its children — none of which
     * comes back. {@code destructive()} makes the guard chain resolve the record and confirm it by
     * identity, so a model cannot delete something it merely believes it named.
     */
    private ToolAction delete() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("delete")
                .title("Delete an issue")
                .description("Deletes one issue permanently, along with its comments, links and "
                           + "history. Its children are detached rather than deleted. There is no undo "
                           + "— prefer moving it to a Done status unless somebody has asked for it to "
                           + "be gone.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to delete, e.g. TES-42.")
                        .confirm())
                .requiredPermission(Permissions.DELETE_ISSUE)
                .destructive()
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleDelete)
                .build();
    }

    private Object handleDelete(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        issueService.delete(issue.getId());

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("deleted",  true);
        answer.put("issueKey", issue.getIssueKey());

        return answer;
    }

    // ── Reading ──────────────────────────────────────────────────────────────────

    private ToolAction search() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("search")
                .title("Search issues")
                .description("Finds issues in a project, most recently changed first. Narrow by free "
                           + "text over the summary, or by who they are assigned to. Every result "
                           + "carries the issue key — TES-42 — which is what every other action takes "
                           + "to name an issue.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .optionalString("text", "Words to look for in the summary. Omit for everything.")
                        .optionalString("assigneeMemberId",
                                "Restrict to one person's issues, by the member id a previous answer "
                              + "reported. Omit for everyone's.")
                        .limit(DEFAULT_LIMIT))
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .scopeConfined()
                .handler(this::handleSearch)
                .build();
    }

    private Object handleSearch(ToolInvocation invocation) {
        IssueSearchResponse found = searchService.search(
                members.actingSubject(invocation),
                invocation.optionalString("text").orElse(null),
                invocation.scopeId(),
                null,
                invocation.optionalString("assigneeMemberId").orElse(null),
                // openOnly — the search gained it for the dashboard's "assigned to me"; this tool's
                // behaviour is unchanged, and it becomes an argument here whenever the assistant needs
                // to ask for open work specifically.
                false,
                0,
                invocation.limitArgument(DEFAULT_LIMIT));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("total",  found.total());
        answer.put("shown",  found.items().size());
        answer.put("issues", found.items().stream().map(item -> describe(item.issue())).toList());

        if (found.total() > found.items().size()) {
            answer.put("note", "Showing the " + found.items().size() + " most recently changed of "
                             + found.total() + ". Add text to match on, or raise 'limit' up to "
                             + ToolInvocation.MAXIMUM_LIMIT + ".");
        }

        return answer;
    }

    // ── Moving one ───────────────────────────────────────────────────────────────

    private ToolAction transition() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("transition")
                .title("Move an issue through the workflow")
                .description("Moves one issue to another status, through the project's workflow — not "
                           + "every status is reachable from every other, and the workflow decides. "
                           + "Moving to a Done status also needs a resolution. If the move is not "
                           + "allowed the refusal lists the moves that are, so read it rather than "
                           + "trying another status.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to move, e.g. TES-42.")
                        .requiredString("toStatus",
                                "Name of the status to move it to, as a board column or a previous "
                              + "answer reported it.")
                        .optionalString("resolution",
                                "Why it is finished — required only when the target status is a Done "
                              + "one, and ignored otherwise.")
                        .confirm())
                .requiredPermission(Permissions.TRANSITION_ISSUE)
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleTransition)
                .build();
    }

    /**
     * The one issue this call is about, resolved before anything is changed.
     *
     * <p>Not marked destructive — a transition is reversible, and confirming every one of them would
     * make a board unusable through a conversation. It resolves its record anyway, which is what gives
     * the ceiling and the preview something to be about if this ever becomes a bulk action.
     */
    private AffectedRecords selectIssue(ToolInvocation invocation) {
        return issueRepository.findByIssueKey(invocation.requiredString("issueKey"))
                .filter(issue -> issue.getProjectId().equals(invocation.scopeId()))
                .map(issue -> AffectedRecords.of(List.of(
                        AffectedRecords.Record.of(issue.getId(), issue.getIssueKey(), "issue"))))
                .orElseGet(AffectedRecords::none);
    }

    private Object handleTransition(ToolInvocation invocation) {
        Member caller   = members.actingSubject(invocation);
        String issueKey = invocation.requiredString("issueKey");
        Issue  issue    = requireIssue(invocation, issueKey);

        Project project    = projectService.requireProject(issue.getProjectId());
        String  workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());
        Status  target     = requireTargetStatus(invocation, issue, workflowId);

        try {
            IssueResponse moved = transitionService.transition(
                    caller,
                    issue.getId(),
                    new TransitionIssueRequest(target.getId(), resolutionId(invocation)));

            Map<String, Object> answer = new LinkedHashMap<>();

            answer.put("moved",    true);
            answer.put("issueKey", moved.issueKey());
            answer.put("status",   moved.status() == null ? null : moved.status().name());

            return answer;

        } catch (BusinessRuleViolationException refused) {
            throw refuseWithWhatIsPossible(refused, issue, workflowId);
        }
    }

    /**
     * The status named, matched against what the workflow can actually reach from here.
     *
     * <p>⚠️ Resolved by <strong>name</strong> and never by identifier, because a name is what a person
     * says and what a board column shows. Matching it against the available transitions rather than
     * against every status in the installation means the refusal for a real-but-unreachable status is
     * the same helpful one as for a status that does not exist.
     */
    private Status requireTargetStatus(ToolInvocation invocation, Issue issue, String workflowId) {
        String requested = invocation.requiredString("toStatus");

        List<Status> reachable = reachableStatuses(issue, workflowId);

        return reachable.stream()
                .filter(status -> requested.equalsIgnoreCase(status.getName()))
                .findFirst()
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                        "'" + issue.getIssueKey() + "' cannot move to '" + requested + "' from where it "
                        + "is now. " + describeReachable(reachable)));
    }

    /**
     * The engine's own refusal, made actionable.
     *
     * <p>The message is kept — it is the domain's and says something true — and the list of what is
     * possible is added, because that is the part a model needs to make a second call worth making.
     */
    private ToolRefusedException refuseWithWhatIsPossible(
            BusinessRuleViolationException refused, Issue issue, String workflowId) {

        return new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                refused.getMessage() + ". " + describeReachable(reachableStatuses(issue, workflowId))
                + " Nothing was changed.");
    }

    private List<Status> reachableStatuses(Issue issue, String workflowId) {
        return workflowResolver.availableTransitions(workflowId, issue.getStatusId()).stream()
                .map(Transition::getToStatusId)
                .distinct()
                .map(workflowResolver::requireStatus)
                .toList();
    }

    private String describeReachable(List<Status> reachable) {
        if (reachable.isEmpty()) {
            return "The workflow allows no move at all from its current status, which is a workflow "
                 + "to fix rather than a call to retry.";
        }

        return "From where it is now the workflow allows: "
             + reachable.stream().map(Status::getName).collect(Collectors.joining(", ")) + ".";
    }

    private String resolutionId(ToolInvocation invocation) {
        return invocation.optionalString("resolution").orElse(null);
    }

    private Issue requireIssue(ToolInvocation invocation, String issueKey) {
        Issue issue = issueRepository.findByIssueKey(issueKey).orElseThrow(() ->
                new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                        "There is no issue '" + issueKey + "'. Use issues_search to find one — an "
                        + "issue key is never invented."));

        ScopeConfinement.require(
                invocation, issue.getProjectId().equals(invocation.scopeId()), "issue", issueKey);

        return issue;
    }

    private Map<String, Object> describe(IssueRowResponse issue) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",     issue.issueKey());
        described.put("summary", issue.summary());
        described.put("status",  issue.status() == null ? null : issue.status().name());
        described.put("type",    issue.type() == null ? null : issue.type().name());
        described.put("open",    issue.open());

        if (issue.assignee() != null) {
            described.put("assignee", issue.assignee().displayName());
        }

        return described;
    }
}
