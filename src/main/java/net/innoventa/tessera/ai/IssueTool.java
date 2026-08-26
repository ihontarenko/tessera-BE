package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.dto.issue.CreateIssueLinkRequest;
import net.innoventa.tessera.dto.issue.CreateIssueRequest;
import net.innoventa.tessera.dto.issue.IssueLinkView;
import net.innoventa.tessera.dto.issue.IssueReference;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.TransitionOption;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.dto.issue.SetParentRequest;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.dto.issue.UpdateIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.CommentService;
import net.innoventa.tessera.service.IssueArchiveService;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.IssueHierarchyService;
import net.innoventa.tessera.service.IssueLinkService;
import net.innoventa.tessera.service.IssueSearchService;
import net.innoventa.tessera.service.IssueService;
import net.innoventa.tessera.service.ProjectService;
import net.innoventa.tessera.service.TransitionService;
import net.innoventa.tessera.service.WorkflowResolver;
import org.jmouse.ai.AffectedRecords;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.CallerAttributes;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ScopeConfinement;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.jmouse.ai.ToolRefusedException;
import net.innoventa.tessera.service.file.AttachmentOwners;
import org.jmouse.files.jpa.ManagedFile;
import org.jmouse.files.management.FileManagement;
import org.jmouse.storage.Content;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final IssueHierarchyService hierarchyService;
    private final IssueLinkService   issueLinkService;
    private final IssueArchiveService archiveService;
    private final ProjectAccess      projectAccess;
    private final ToolMembers        members;
    private final ToolCatalogs       catalogs;
    private final FileManagement     files;

    /**
     * How a file arrives — the two forms, and the disk-reading capability among them.
     *
     * <p>⚠️ Moved out to {@link ToolFileBytes} when {@code files_upload} became the second action to
     * carry bytes. Two copies would be two answers to <em>may this server read that path</em>.</p>
     */
    private final ToolFileBytes      fileBytes;

    @Override
    public String toolName() {
        return "issues";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(search(), list(), get(), create(), update(), assign(), transition(), attach(),
                       comment(),
                       link(), unlink(), relink(), archive(), archiveCompleted(), unarchive(),
                       delete());
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
                           + "who it is assigned to, its labels, its links, and what is blocking it. "
                           + "A link, parent or child in a project you cannot browse shows its key "
                           + "only. "
                           + "Read this before editing one, because an update replaces the fields it "
                           + "is given.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to read, e.g. TES-42."))
                .readOnly()
                .scopeConfined()
                .handler(this::handleGet)
                .build();
    }

    private Object handleGet(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        return describeInFull(issueService.getByKey(issue.getIssueKey(), members.actingSubject(invocation)));
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
     *
     * <p>⚠️ <strong>{@code reference} is the second exception, and it is deliberately not a bare
     * identifier.</strong> A reference stored outside this tracker has to carry the permanent hash
     * rather than the key, or it breaks the day a key is re-minted — so the value has to be reachable.
     * Handing out the hash on its own is exactly the mistake the paragraph above describes: it would be
     * passed straight back as an {@code issueKey}. What is handed out is therefore the whole written
     * form, {@code issue:<hash>}, which is a link destination and nothing a tool would accept.
     */
    private Map<String, Object> describeInFull(IssueResponse issue) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",         issue.issueKey());
        described.put("reference",   "issue:" + issue.hash());
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
            described.put("children", issue.children().stream().map(IssueReference::issueKey).toList());
        }

        // ⚠️ Links, at last, and the description had been promising them all along (TSSR-44). A missing
        // field makes a model ask; a described field that never arrives makes it read the answer it was
        // given, conclude the issue has none, and act on that.
        //
        // Grouped by LABEL rather than by type, the way the Relations panel groups them: a symmetric
        // type says the same word both ways, while an asymmetric one reads "blocks" in one direction and
        // "is blocked by" in the other — two different statements about this issue.
        //
        // ⚠️ Redacted entries stay redacted. The far end may be in a project the caller cannot browse,
        // and `IssueAssembler` has already withheld its summary; reaching around that here would make
        // the tool the more dangerous of the two clients.
        if (!issue.links().isEmpty()) {
            described.put("links", issue.links().stream().collect(Collectors.groupingBy(
                    IssueLinkView::label,
                    LinkedHashMap::new,
                    Collectors.mapping(link -> link.issue().issueKey(), Collectors.toList()))));
        }

        described.put("canMoveTo", issue.availableTransitions().stream()
                .map(TransitionOption::toStatusName)
                .toList());

        // Why canMoveTo is short, when it is. Without this an agent reads a missing transition and has
        // no way to tell a workflow that forbids it from a blocker that will lift on its own.
        if (!issue.blockedBy().isEmpty()) {
            described.put("blockedBy", issue.blockedBy());
        }

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
                        .optionalString("parent", "The issue this one belongs under, by key — an epic "
                                      + "for a story, a story for a sub-task. The parent's type must "
                                      + "sit strictly higher in the hierarchy, and a parent that "
                                      + "cannot hold this type is refused. A parent whose type spans "
                                      + "projects — a Hub — may be in another project you belong to.")
                        .optionalNumber("storyPoints", "An estimate, where the team uses them.")
                        .confirm())
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
                        // The parent, unlike the assignee, needs nothing beyond CREATE_ISSUE, so it is
                        // accepted here rather than left to a second call.
                        parentIdNamedBy(invocation),
                        invocation.optionalNumber("storyPoints").orElse(null)));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("created",   true);
        answer.put("issueKey",  created.issueKey());
        // The form to write into a page — see `describeInFull` for why it is not the bare hash.
        answer.put("reference", "issue:" + created.hash());
        answer.put("status",    created.status() == null ? null : created.status().name());

        return answer;
    }

    /**
     * The parent an invocation named, as an identifier — or null where it named none.
     *
     * <p>⚠️ <strong>Resolved through {@link #requireLinkableIssue}, not {@link #requireIssue}</strong>
     * (TSSR-56). It used to be confined to the scope, so a key elsewhere read as <em>no such issue</em>
     * — which was right while a parent had to be in the child's project and became wrong the moment a
     * Hub could hold work across them. The far end of a link has always resolved this way, and a parent
     * that may cross is the same question: findable anywhere the caller browses, invisible everywhere
     * else.
     *
     * <p>⚠️ Both rules that constrain it stay where they were. {@code IssueHierarchyService} owns
     * "strictly higher level" and "same project unless the type spans them", and refuses with the
     * reason; a second opinion in a tool is a second place for the two to disagree.
     */
    private String parentIdNamedBy(ToolInvocation invocation) {
        return invocation.optionalString("parent")
                .map(key -> requireLinkableIssue(invocation, key).getId())
                .orElse(null);
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
                        .optionalString("parent", "The issue this one should belong under, by key. Use "
                                      + "it to adopt an existing issue into an epic. The parent's type "
                                      + "must sit strictly higher in the hierarchy. A parent whose type "
                                      + "spans projects — a Hub — may be in another project you belong to.")
                        .optionalNumber("storyPoints", "A new estimate.")
                        .confirm())
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleUpdate)
                .build();
    }

    // ── Who is working on it ─────────────────────────────────────────────────────

    /**
     * Assigning, as its own action rather than a field on {@link #update()}.
     *
     * <p>⚠️ <strong>Because one tool is one permission.</strong> An {@code assignee} argument on
     * {@code issues_update} would ride on {@code tool:issues_update}, so switching on <em>may edit an
     * issue</em> would also switch on <em>may decide who works on it</em>. Split, an installation grants
     * either without the other — which is the whole reason the axis is named per action.
     *
     * <p>It matches the domain, which already keeps the two apart: assigning asks
     * {@link Permissions#ASSIGN_ISSUE} and editing asks {@code EDIT_ISSUE}, and {@link #handleUpdate}
     * deliberately carries the existing assignee through untouched rather than letting an edit change it.
     *
     * <p>⚠️ <strong>Not confirmed, unlike the destructive actions.</strong> Assigning is reversible, it
     * loses nothing, and a confirmation round-trip on every one would make a board unusable through a
     * conversation.
     */
    private ToolAction assign() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("assign")
                .title("Assign an issue")
                .description("Says who is working on an issue. Pass 'me' to take it yourself, a member "
                           + "id to give it to somebody, or 'none' to leave it unassigned. Nothing else "
                           + "about the issue changes. ⚠️ An agent is its own member here, so 'me' from "
                           + "a client means THE AGENT, not the person who owns it. ⚠️ And an agent may "
                           + "only ever assign work to itself: a client naming anybody else is refused, "
                           + "because a person assigns people.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to assign, e.g. TES-42.")
                        .requiredString("assignee",
                                "'me' for the caller, a member id a previous answer reported, or 'none' "
                              + "to clear it."))
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleAssign)
                .build();
    }

    /**
     * ⚠️ <strong>Everything except the assignee is read back and written unchanged.</strong>
     * {@code UpdateIssueRequest} takes the whole editable set and replaces all of it, so a field left
     * out is a field blanked — the same trap {@link #handleUpdate} works around, and worse here because
     * nobody asking to assign an issue expects its description to move.
     *
     * <p>⚠️ <strong>The agent rule is the domain's and is not repeated here.</strong>
     * {@code IssueService.resolveAssignee} refuses a client naming anybody but itself, with a sentence
     * worth reading rather than paraphrasing. A copy of that check in this class would be the kind of
     * rule that ends up stated twice and enforced once.
     */
    private Object handleAssign(ToolInvocation invocation) {
        Member        acting   = members.actingSubject(invocation);
        Issue         issue    = requireIssue(invocation, invocation.requiredString("issueKey"));
        IssueResponse existing = issueService.getByKey(issue.getIssueKey(), acting);

        String        named    = invocation.requiredString("assignee").trim();
        String        assignee = switch (named.toLowerCase(Locale.ROOT)) {
            case "me"   -> callingAgentId(invocation).orElseGet(acting::getId);
            case "none" -> null;
            default     -> named;
        };

        IssueResponse updated = issueService.update(
                acting,
                issue.getId(),
                new UpdateIssueRequest(
                        existing.summary(),
                        existing.description(),
                        issue.getPriorityId(),
                        assignee,
                        issue.getStoryPoints()));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("assigned", assignee != null);
        answer.put("issueKey", updated.issueKey());
        answer.put("assignee", updated.assignee() == null ? "nobody" : updated.assignee().displayName());

        return answer;
    }

    /**
     * The agent behind this call, where one is behind it — <strong>who {@code "me"} means</strong>.
     *
     * <p>⚠️ <strong>The acting subject is the wrong answer, and it reads like the right one.</strong>
     * {@link ToolMembers#actingSubject} says <em>whose rows are in view</em>, which for an inheriting
     * agent is deliberately the owner: correct to authorize with, correct to read with, and wrong to
     * write into {@code assignee_member_id}. Assigning is a question of <em>whose name goes on the
     * row</em> — the same question {@code CommentService} and {@code ActivityLogService} already answer
     * with the agent (TSSR-34) — so taking a ticket used to hand it to a person who was asleep at the
     * time, and answer {@code "assignee": "SU"} as though that had been the ask (TSSR-74).
     *
     * <p>⚠️ <strong>Read from the invocation, never from {@code CallingAgent}.</strong> That class
     * reaches into {@code SecurityContextHolder}, which is a fact about the thread serving a request and
     * not about the call; the identity a tool call runs as has already been resolved once, by
     * {@code TesseraCallerResolver}, and it puts the agent here under <em>both</em> authorities — the
     * only place it survives under {@code INHERITED}, where the caller identifier is the owner's.
     *
     * <p>Empty where a person is at the keyboard, so the in-application assistant is unchanged.
     */
    private Optional<String> callingAgentId(ToolInvocation invocation) {
        return Optional.ofNullable(invocation.caller().attributes().get(CallerAttributes.AGENT_ID))
                .filter(agentId -> !agentId.isBlank());
    }

    private Object handleUpdate(ToolInvocation invocation) {
        Issue         issue    = requireIssue(invocation, invocation.requiredString("issueKey"));
        IssueResponse existing = issueService.getByKey(issue.getIssueKey(), members.actingSubject(invocation));

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

        // ⚠️ A second call rather than a field, because the parent is not one: `UpdateIssueRequest` does
        // not carry it, and the hierarchy has its own service, its own validation and its own history
        // entry. Same permission (EDIT_ISSUE), so nothing here can be refused that the update was not.
        String parentId = parentIdNamedBy(invocation);

        if (parentId != null) {
            hierarchyService.setParent(
                    members.actingSubject(invocation), issue.getId(), new SetParentRequest(parentId));
        }

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("updated",  true);
        answer.put("issueKey", updated.issueKey());

        return answer;
    }

    // ── Tying two of them together ───────────────────────────────────────────────

    /**
     * ⚠️ <strong>The three link actions are the only ones whose target may be outside the scope</strong>
     * (TSSR-44) — see {@link #requireLinkableIssue}. The acting issue is scoped as usual and is what
     * {@code EDIT_ISSUE} is checked against; the far end only has to be somewhere the caller can browse.
     */
    private ToolAction link() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("link")
                .title("Link two issues")
                .description("Records a typed relationship between two issues — 'Blocks', 'Tracks', "
                           + "'Relates'. The other issue may be in a different project: gathering work "
                           + "that spans projects under one tracking issue is what this is for.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue the link is recorded on, e.g. TES-42.")
                        .requiredString("otherIssueKey", "The issue at the other end. May be in another "
                                                       + "project you belong to.")
                        .requiredString("linkType", "The kind of relationship, by name — the "
                                                  + "installation keeps a short list, e.g. 'Blocks'. "
                                                  + "There is no default: the type is the whole content "
                                                  + "of a link."))
                .scopeConfined()
                .handler(this::handleLink)
                .build();
    }

    private Object handleLink(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));
        Issue other = requireLinkableIssue(invocation, invocation.requiredString("otherIssueKey"));
        String linkTypeId = catalogs.linkTypeIdFor(invocation.requiredString("linkType"));

        // The service's own refusals travel untouched — the duplicate check and the blocking-cycle walk
        // live there, and a second copy here would be a second answer able to disagree with the first.
        issueLinkService.addLink(
                members.actingSubject(invocation),
                issue.getId(),
                new CreateIssueLinkRequest(linkTypeId, other.getId()));

        return linkAnswer("linked", issue, other, invocation.requiredString("linkType"));
    }

    // ── Putting it away ──────────────────────────────────────────────────────────

    /**
     * Filing finished work, over the protocol (TSSR-4 built it for the browser only).
     *
     * <p>⚠️ <strong>Its absence was the gap, and it showed as one.</strong> Archiving is how a finished
     * issue leaves the board, the backlog and the project's list — and with no action for it, a
     * conversation could resolve twenty issues and then had no way to put a single one away. Tidying up a
     * long Shipped list meant twenty clicks in a browser, which is exactly the shape of work a protocol
     * exists to take.
     *
     * <p>⚠️ <strong>Gated on {@code EDIT_ISSUE}, the same permission the route asks for</strong> — filing
     * is an edit to the issue's own state, not a transition (archived is a flag, never a status) and not a
     * deletion (nothing is destroyed; search and the Shipped screen still find it).
     *
     * <p>⚠️ <strong>Not marked destructive, and deliberately not confirmed.</strong> It is reversible by
     * {@link #unarchive()} and reversed automatically by reopening the issue, so requiring a confirmation
     * token per issue would make the one job this action exists for — a list of them — unusable. The
     * record is still resolved, so the ceiling and the preview have something to be about.
     */
    private ToolAction archive() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("archive")
                .title("Put a finished issue away")
                .description("Files a finished issue: it leaves the board, the backlog and the project's "
                           + "issue list at once. Nothing is destroyed: the Shipped screen still lists it, "
                           + "marked as archived. ⚠️ But issues_search does NOT return archived issues, so "
                           + "once filed it is not findable through this protocol — do not file something "
                           + "you will need to read again here. ⚠️ Only finished work "
                           + "can be filed: an issue with no resolution is refused, because hiding work "
                           + "somebody is still expected to do is what an archive exists to prevent. "
                           + "Archiving twice is not an error and changes nothing the second time.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to put away, e.g. TES-42."))
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleArchive)
                .build();
    }

    private Object handleArchive(ToolInvocation invocation) {
        String issueKey = invocation.requiredString("issueKey");
        Issue  issue    = requireIssue(invocation, issueKey);

        // The service's own refusal travels untouched — "only finished work can be archived" names the
        // issue and what state it is in, and a second copy of that sentence here could disagree with it.
        // ⚠️ The domain's refusal becomes a REFUSAL, not an exception. "Only finished work can be archived"
        // is a sentence the caller can act on — resolve it first — and letting it escape would surface as a
        // failure about an exception instead. This product has already paid for that once: a library port's
        // refusal arriving as a 500, and a 500 says nothing. `handleTransition` does the same thing.
        try {
            IssueResponse archived = archiveService.archive(members.actingSubject(invocation), issue.getId());

            return filedAnswer("archived", archived);

        } catch (BusinessRuleViolationException refused) {
            throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                    refused.getMessage() + " Nothing was changed.");
        }
    }

    /**
     * Filing a whole project's finished work in one call.
     *
     * <h2>⚠️ Raised by Ivan out of what it actually cost</h2>
     *
     * <p>Clearing six projects after a long batch took <strong>seventy-three</strong> separate
     * {@link #archive()} calls, each one resolving the same project again. A protocol exists to take
     * exactly that shape of work, and an action that can only be called in a loop is a loop the caller
     * was made to write.
     *
     * <h2>⚠️ {@code status} is a filter, never the definition of finished</h2>
     *
     * <p>The obvious reading of "archive everything Done" is to match the status name, and that rule
     * stops being true the day somebody adds <em>Shipped</em> beside it — status names are rows on a
     * configuration screen. So the service files by <strong>resolution</strong>, which is what
     * {@link #archive()} has always required, and treats a named status as a narrowing filter on top.
     * Both actions therefore mean the same thing by "finished".
     *
     * <p>⚠️ <strong>Unresolved issues are skipped and counted, not refused.</strong> Failing on the first
     * open issue would make this unusable in the one situation it exists for — a project that is mostly
     * finished — and the count is in the answer so nothing is left behind quietly.
     *
     * <p>⚠️ <strong>Confirmed, unlike the single-issue action.</strong> That one is one reversible act on
     * a named issue; this one's whole point is that the caller does not enumerate what it touches, so the
     * preview is the only place the scope of it can be seen before it happens.
     */
    private ToolAction archiveCompleted() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("archive_completed")
                .title("File everything finished in a project")
                .description("Files every finished issue in one project at once — each leaves the board, "
                           + "the backlog and the issue list, and nothing is destroyed. ⚠️ Finished means "
                           + "CARRYING A RESOLUTION, not sitting in a particular status: an issue with no "
                           + "resolution is skipped and counted rather than filed, because hiding work "
                           + "somebody still owes is what an archive exists to prevent. Narrow it with "
                           + "'status' to file only one column — the resolution rule still applies. "
                           + "Answers with the keys filed and how many were skipped. ⚠️ Archived issues "
                           + "are NOT returned by issues_search, so do not file work you will need to read "
                           + "again through this protocol.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .optionalString("status",
                                "Only file issues in this status, e.g. Done. Omit to file everything "
                              + "finished, whichever status it rests in."))
                .scopeConfined()
                .handler(this::handleArchiveCompleted)
                .build();
    }

    private Object handleArchiveCompleted(ToolInvocation invocation) {
        Project project = projectService.requireProject(invocation.scopeId());

        IssueArchiveService.ArchivedBatch batch = archiveService.archiveCompleted(
                members.actingSubject(invocation), project.getId(), invocation.optionalString("status").orElse(null));

        return Map.of(
                "project",  batch.projectKey(),
                "archived", batch.archived(),
                "count",    batch.archived().size(),
                // ⚠️ Always present, including as zero. A caller reading a count of filed issues has no
                // way to tell "nothing was left behind" from "the field is missing" unless it is stated.
                "skipped",  batch.skipped());
    }

    /** Taking it back out. No rule to satisfy — un-archiving is always allowed, unlike the way in. */
    private ToolAction unarchive() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("unarchive")
                .title("Take an issue back out of the archive")
                .description("Puts a filed issue back on the board, the backlog and the project's list. "
                           + "Always allowed — and reopening an archived issue does this on its own, so "
                           + "nothing can be open and invisible at the same time. Un-archiving something "
                           + "that was never filed is not an error.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to take back out, e.g. TES-42."))
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleUnarchive)
                .build();
    }

    private Object handleUnarchive(ToolInvocation invocation) {
        String issueKey = invocation.requiredString("issueKey");
        Issue  issue    = requireIssue(invocation, issueKey);

        IssueResponse restored = archiveService.unarchive(members.actingSubject(invocation), issue.getId());

        return filedAnswer("unarchived", restored);
    }

    /**
     * The answer both filing actions give.
     *
     * <p>⚠️ A {@code LinkedHashMap} rather than {@code Map.of}: a status can be null — a status row
     * somebody deleted out from under an issue — and {@code Map.of} throws on a null value, which would
     * turn a successful write into a 500 answering about a `NullPointerException`.
     */
    private Map<String, Object> filedAnswer(String verb, IssueResponse issue) {
        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put(verb,       true);
        answer.put("issueKey", issue.issueKey());
        answer.put("status",   issue.status() == null ? null : issue.status().name());

        return answer;
    }

    private ToolAction unlink() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("unlink")
                .title("Remove a link between two issues")
                .description("Removes a typed relationship between two issues. Addressed by the two "
                           + "issues and the type, because no action hands out a link identifier.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue the link is recorded on, e.g. TES-42.")
                        .requiredString("otherIssueKey", "The issue at the other end.")
                        .requiredString("linkType", "Which relationship to remove, by name."))
                .scopeConfined()
                .handler(this::handleUnlink)
                .build();
    }

    private Object handleUnlink(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));
        Issue other = requireLinkableIssue(invocation, invocation.requiredString("otherIssueKey"));
        String linkTypeId = catalogs.linkTypeIdFor(invocation.requiredString("linkType"));

        issueLinkService.removeLink(
                members.actingSubject(invocation),
                issue.getId(),
                issueLinkService.requireLinkBetween(issue.getId(), other.getId(), linkTypeId).getId());

        return linkAnswer("unlinked", issue, other, invocation.requiredString("linkType"));
    }

    /**
     * ⚠️ <strong>Retyping, not re-pointing.</strong> A link is {@code (source, target, type)}; changing
     * an end is a different link, so that is unlink-then-link. And it is written to the activity log
     * (TSSR-40), because turning "is blocked by" into "relates to" is also how a gate gets lifted.
     */
    private ToolAction relink() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("relink")
                .title("Change what a link between two issues says")
                .description("Changes an existing link's type without unmaking the relationship — a "
                           + "'Blocks' that was really a 'Relates'. The change is recorded on the "
                           + "issue's history.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue the link is recorded on, e.g. TES-42.")
                        .requiredString("otherIssueKey", "The issue at the other end.")
                        .requiredString("linkType", "The relationship as it reads now, by name.")
                        .requiredString("newLinkType", "What it should say instead, by name."))
                .scopeConfined()
                .handler(this::handleRelink)
                .build();
    }

    private Object handleRelink(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));
        Issue other = requireLinkableIssue(invocation, invocation.requiredString("otherIssueKey"));
        String linkTypeId = catalogs.linkTypeIdFor(invocation.requiredString("linkType"));
        String nextTypeId = catalogs.linkTypeIdFor(invocation.requiredString("newLinkType"));

        issueLinkService.changeLinkType(
                members.actingSubject(invocation),
                issue.getId(),
                issueLinkService.requireLinkBetween(issue.getId(), other.getId(), linkTypeId).getId(),
                nextTypeId);

        return linkAnswer("relinked", issue, other, invocation.requiredString("newLinkType"));
    }

    private Map<String, Object> linkAnswer(String verb, Issue issue, Issue other, String linkType) {
        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put(verb,           true);
        answer.put("issueKey",     issue.getIssueKey());
        answer.put("otherIssueKey", other.getIssueKey());
        answer.put("linkType",     linkType);

        return answer;
    }

    // ── Putting a file on one ────────────────────────────────────────────────────

    /**
     * The one action that carries BYTES.
     *
     * <p>An assistant is shown a screenshot of a broken screen, fixes it, files the ticket — and could
     * not attach the screenshot to the ticket it had just raised, because nothing in the protocol carried
     * a file. That is the whole of why this exists.</p>
     *
     * <p>⚠️ <strong>The same upload the interface makes.</strong> It goes through {@code FileManagement}
     * exactly as the screen does, so the acceptance policy, the size ceiling, the quota, the audit line
     * and the entry in the issue's history are the ones already there. A protocol path that could store
     * what a person could not would be a second policy — and the one nobody is looking at.</p>
     *
     * <p>⚠️ <strong>Not confirmed, deliberately.</strong> Attaching adds; it overwrites nothing and takes
     * nothing away, and the file is removable afterwards from the issue screen. The guard belongs on
     * {@code issues_delete}, which is where it is.</p>
     */
    private ToolAction attach() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("attach")
                .title("Attach a file to an issue")
                .description("Puts a file on an issue — a screenshot of a fault, a log, an export. Send "
                           + "the bytes as 'base64', or 'path' to read one off this server's disk where "
                           + "the installation allows that. It is the same upload the interface makes, so "
                           + "the same size and type rules apply, and it appears in the issue's history "
                           + "like any other change.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to attach it to, e.g. TES-42.")
                        .requiredString("name", "What to call it — the filename, including its extension.")
                        .optionalString("base64", "The bytes, base64-encoded. Give this or 'path', never both.")
                        .optionalString("path", "A local file to read instead of sending its bytes. Only "
                                              + "paths under this installation's configured upload root "
                                              + "are allowed, and the form is refused entirely when none "
                                              + "is configured.")
                        .optionalString("contentType", "The media type, when it is not obvious from the name."))
                .scopeConfined()
                .handler(this::handleAttach)
                .build();
    }

    private Object handleAttach(ToolInvocation invocation) {
        Issue  issue = requireIssue(invocation, invocation.requiredString("issueKey"));
        String name  = invocation.requiredString("name");
        byte[] bytes = fileBytes.of(invocation);

        // ⚠️ THE UPLOADER IS THE AGENT, NOT ITS OWNER — the same rule V000034 settled for comments:
        // "the author IS the agent". Written as `actingSubject` this hung every file an assistant
        // attached on the person who happened to own it, so a board full of work nobody remembers doing
        // looks exactly like a board where somebody was busy. `callingAgentId` is empty at the in-app
        // assistant, where the caller and the subject are the same member, so the fallback is not a
        // special case — it is the ordinary one.
        String uploader = callingAgentId(invocation)
                .orElseGet(() -> members.actingSubject(invocation).getId());

        // ⚠️ The size is declared from what actually arrived rather than left unknown: the bytes are
        // already in memory, so the acceptance policy can refuse an oversized file before anything is
        // written instead of storing it and reclaiming it afterwards.
        ManagedFile stored = files.upload(
                AttachmentOwners.issue(issue.getId()),
                AttachmentOwners.NAMESPACE,
                Content.of(name, invocation.optionalString("contentType").orElse(null), bytes.length,
                           () -> new ByteArrayInputStream(bytes)),
                name,
                uploader);

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("id",          stored.getId());
        answer.put("name",        stored.getDisplayName());
        answer.put("contentType", stored.getStoredFile().getContentType().toString());
        answer.put("sizeBytes",   stored.getStoredFile().getSizeBytes());
        answer.put("issueKey",    issue.getIssueKey());
        answer.put("attached",    true);

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
                        .requiredString("body", "What to say, as Markdown.")
                        // Named, never identified — and the catalog is deliberately not listed here.
                        // It is per-installation and editable on a screen, so a list in this string
                        // would be stale the first time somebody renames a row. The refusal carries
                        // the real one.
                        .optionalString("topic", "What the comment is about, by name — the installation "
                                               + "keeps a short list of them, e.g. 'Code review'. Omit "
                                               + "for an ordinary remark."))
                .scopeConfined()
                .handler(this::handleComment)
                .build();
    }

    private Object handleComment(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        commentService.add(
                members.actingSubject(invocation),
                issue.getId(),
                new SaveCommentRequest(
                        invocation.requiredString("body"),
                        catalogs.commentTopicIdFor(invocation.optionalString("topic").orElse(null))));

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
                // includeArchived — a tool reads what a screen reads, and put-away work is off every
                // screen (TSSR-4). It becomes an argument the day something needs to search the archive.
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
                                "Why it is finished, by name — Done, Duplicate, Won't Do. Required "
                              + "only when the target status is a Done one, and ignored otherwise. A "
                              + "name the installation does not offer is refused with the list that "
                              + "would have worked.")
                        .confirm())
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

    /**
     * ⚠️ Resolved by <strong>name</strong>, exactly like the status above and the type and priority on
     * {@code create}. This argument used to reach the domain untouched — see
     * {@link ToolCatalogs#resolutionIdFor} for what that cost.
     */
    private String resolutionId(ToolInvocation invocation) {
        return catalogs.resolutionIdFor(invocation.optionalString("resolution").orElse(null));
    }

    /**
     * The far end of a link — resolved <strong>outside</strong> the scope, on purpose (TSSR-44).
     *
     * <p>⚠️ <strong>A link crosses the project boundary by definition</strong>, which is the whole point
     * of a tracking issue: an effort is one thing that lands in four projects. So this deliberately does
     * not call {@code ScopeConfinement.require} — the acting issue stays scoped and its project is what
     * the permission is checked against; the far end is resolved against everything the caller may
     * browse.
     *
     * <p>⚠️ <strong>Out of view is "no such issue", never "forbidden".</strong> A project somebody does
     * not belong to is a 404 to this protocol and always has been — saying "you may not" would confirm
     * that the issue exists, which is the one thing the refusal must not do.
     *
     * <p>Keys are uppercased before the lookup rather than left to a collation, the same way
     * {@code IssueService.getByKey} does it: MySQL would match either way and PostgreSQL would not.
     *
     * <p>⚠️ <strong>Asked of the one project, not of a list of every project.</strong> This filtered on
     * {@code visibleProjectIds} and so refused every link for a caller who browses everything
     * installation-wide — that answer is an empty list rather than "all of them", as its own javadoc
     * warns, and this is the second place that warning has been earned. It also asked once per candidate.
     */
    private Issue requireLinkableIssue(ToolInvocation invocation, String issueKey) {
        Member caller = members.actingSubject(invocation);

        return issueRepository.findByIssueKey(issueKey.toUpperCase(Locale.ROOT))
                .filter(candidate -> projectAccess.holds(
                        caller, candidate.getProjectId(), Permissions.BROWSE_PROJECT))
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                        "There is no issue '" + issueKey + "' you can see. Use issues_search to find one "
                        + "— it searches every project you belong to, and an issue key is never "
                        + "invented."));
    }

    /**
     * The bytes, from whichever of the two forms was used.
     *
     * <p>⚠️ <strong>Exactly one, and saying "both" is refused rather than resolved.</strong> Picking a
     * winner would mean a caller who sent two different files gets one of them silently, and never finds
     * out which.</p>
     */

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
