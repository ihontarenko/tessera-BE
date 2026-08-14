package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.IssueSearchService;
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
    private final TransitionService  transitionService;
    private final WorkflowResolver   workflowResolver;
    private final ProjectService     projectService;
    private final IssueRepository    issueRepository;
    private final ToolMembers        members;

    @Override
    public String toolName() {
        return "issues";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(search(), transition());
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
