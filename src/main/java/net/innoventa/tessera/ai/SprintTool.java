package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.sprint.CreateSprintRequest;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.SprintRepository;
import net.innoventa.tessera.service.SprintMembershipService;
import net.innoventa.tessera.service.SprintService;
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
 * A project's sprints, and what is committed to one.
 *
 * <p><strong>Committing work is the act this tool exists for.</strong> Reading a sprint is ordinary;
 * moving an issue into one is a planning decision with consequences a model should not make casually —
 * a sprint's story points are frozen at commitment, so an issue added after a sprint starts is scope
 * added mid-sprint and shows up in the report as exactly that.
 *
 * <p>⚠️ <strong>Sprints are named, never identified.</strong> A sprint identifier is a surrogate string
 * nobody says out loud; a sprint's name is what a person means. So both write actions take a name and
 * refuse an unknown one by listing the sprints that exist — the same rule {@code IssueTool} follows for
 * a status and {@code ToolCatalogs} for a type.
 *
 * <p>⚠️ <strong>There is no {@code start} and no {@code complete}, and that is on purpose.</strong>
 * Starting a sprint sets a team's commitment for a fortnight and closing one decides what happens to
 * unfinished work; both are decisions somebody stands behind, made on a screen where the consequences
 * are laid out. A tool that could start a sprint would eventually start one because a sentence sounded
 * like it wanted one.
 *
 * <p>⚠️ <strong>{@code create} was refused on that same reasoning and has been let in, which is a
 * narrowing of the rule rather than an abandonment of it.</strong> The argument above is about
 * <em>commitment</em>, and a planned sprint is not one: it has no dates, no committed points and no
 * issues, it changes nothing about work in flight, and deleting it costs a click. What the old rule
 * actually cost was reachability — a Scrum project on the day it is created has no sprint, so
 * {@link #read()} and {@link #addIssue()} both refused every call until somebody opened a screen, and
 * the two most useful actions here were unreachable through the protocol they are published on. The
 * line is drawn where the consequence is: naming a container is a tool's business, starting the clock
 * on it is a person's.
 */
@Component
@RequiredArgsConstructor
public class SprintTool implements ToolDefinition {

    private final SprintService           sprintService;
    private final SprintMembershipService memberships;
    private final SprintRepository        sprints;
    private final IssueRepository         issues;
    private final BoardRepository         boards;
    private final ToolMembers             members;

    @Override
    public String toolName() {
        return "sprints";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(list(), read(), create(), addIssue(), removeIssue());
    }

    // ── Reading ──────────────────────────────────────────────────────────────────

    private ToolAction list() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("list")
                .title("List a project's sprints")
                .description("Lists a project's sprints, oldest first, with the state each is in — "
                           + "planned, active or closed. A project that does not plan in sprints has "
                           + "none, which is an answer rather than an error.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list"))
                .readOnly()
                .scopeConfined()
                .handler(this::handleList)
                .build();
    }

    private Object handleList(ToolInvocation invocation) {
        return sprintService.list(invocation.scopeId()).stream().map(SprintTool::describe).toList();
    }

    private ToolAction read() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("read")
                .title("Read one sprint")
                .description("Reads one sprint by name, with the issues currently committed to it. "
                           + "Read this before adding anything: what is already in a sprint is most of "
                           + "what decides whether more should go in.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("sprint", "The sprint's name, as sprints_list reported it."))
                .readOnly()
                .scopeConfined()
                .handler(this::handleRead)
                .build();
    }

    private Object handleRead(ToolInvocation invocation) {
        Sprint sprint = requireSprint(invocation, invocation.requiredString("sprint"));

        List<String> committed = issues
                .findAllById(memberships.currentMembers(sprint.getId()).stream()
                        .map(SprintIssue::getIssueId)
                        .toList())
                .stream()
                .map(Issue::getIssueKey)
                .toList();

        Map<String, Object> answer = new LinkedHashMap<>(describe(SprintSummary.from(sprint)));

        answer.put("committed", committed);

        return answer;
    }

    // ── Planning one ─────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>Planned only — never started.</strong> The sprint this raises has a name, an optional
     * goal, no dates and nothing committed to it, which is the whole of what makes it safe to create
     * from a sentence. See the class note for where the line sits and why it moved.
     *
     * <p>⚠️ <strong>Refused outright on a project that does not plan in sprints.</strong> The domain
     * would happily store the row — a sprint is not forbidden by a board's scope strategy — and it would
     * then appear on no screen and scope no board, which is a thing that exists and does nothing. A
     * caller that meant to plan is better told the project is not set up for it.
     */
    private ToolAction create() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("create")
                .title("Plan a new sprint")
                .description("Creates a sprint in a project that plans in sprints. It is planned and "
                           + "not started: it has a name, an optional goal, no dates and nothing in "
                           + "it. Starting it and closing it are decisions made on the backlog screen, "
                           + "so there is deliberately no tool for either.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("name",
                                "What the sprint is called — 'Sprint 4'. It is what every other sprint "
                              + "action takes to name this one.")
                        .optionalString("goal", "What the team means to achieve in it. Omit for none.")
                        .confirm())
                .scopeConfined()
                .handler(this::handleCreate)
                .build();
    }

    private Object handleCreate(ToolInvocation invocation) {
        if (!plansInSprints(invocation)) {
            throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                    "This project plans on a board of everything rather than in sprints, so a sprint "
                    + "would sit on no screen and scope no board. Planning in sprints is a project "
                    + "setting, changed on a screen.");
        }

        String name = invocation.requiredString("name");

        requireUnusedSprintName(invocation, name);

        SprintSummary created = sprintService.create(
                invocation.scopeId(),
                new CreateSprintRequest(name, invocation.optionalString("goal").orElse(null)));

        Map<String, Object> answer = new LinkedHashMap<>(describe(created));

        answer.put("created", true);

        return answer;
    }

    /**
     * ⚠️ Nothing in the schema stops two sprints sharing a name, and {@link #requireSprint} matches by
     * name — so a duplicate would make every later {@code read} and {@code add_issue} act on whichever
     * came first. Refusing here keeps the lookup those two depend on unambiguous.
     */
    private void requireUnusedSprintName(ToolInvocation invocation, String name) {
        boolean taken = sprints.findByProjectIdOrderByCreatedAtAsc(invocation.scopeId()).stream()
                .anyMatch(sprint -> name.equalsIgnoreCase(sprint.getName()));

        if (taken) {
            throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                    "This project already has a sprint called '" + name + "'. A second one by the same "
                    + "name would make every later call ambiguous about which it meant.");
        }
    }

    // ── Committing work, and taking it back ──────────────────────────────────────

    private ToolAction addIssue() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("add_issue")
                .title("Commit an issue to a sprint")
                .description("Moves one issue into a sprint. ⚠️ Adding to a sprint that has already "
                           + "started is scope added mid-sprint — the sprint's committed points were "
                           + "frozen when it started, so the report will show it as added work. Say so "
                           + "rather than doing it quietly.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to commit, e.g. TES-42.")
                        .requiredString("sprint", "The sprint to move it into, by name.")
                        .confirm())
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleAdd)
                .build();
    }

    private Object handleAdd(ToolInvocation invocation) {
        Issue  issue  = requireIssue(invocation, invocation.requiredString("issueKey"));
        Sprint sprint = requireSprint(invocation, invocation.requiredString("sprint"));

        if (sprint.getState() == SprintState.CLOSED) {
            throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                    "'" + sprint.getName() + "' is closed, and a closed sprint never reopens. Commit "
                    + "the issue to a planned or active sprint, or leave it in the backlog.");
        }

        boolean moved = memberships.moveToSprint(issue, sprint, members.actingSubject(invocation));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("committed", true);
        answer.put("issueKey",  issue.getIssueKey());
        answer.put("sprint",    sprint.getName());

        if (!moved) {
            answer.put("note", "It was already in that sprint; nothing changed.");
        } else if (sprint.getState() == SprintState.ACTIVE) {
            answer.put("note", "That sprint is already running, so this is added scope and the sprint "
                             + "report will show it as such.");
        }

        return answer;
    }

    private ToolAction removeIssue() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("remove_issue")
                .title("Take an issue out of its sprint")
                .description("Moves one issue back to the backlog, out of whatever sprint it is "
                           + "committed to. The issue itself is untouched — this is a planning change, "
                           + "not a change to the work.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list")
                        .requiredString("issueKey", "The issue to move back to the backlog, e.g. TES-42.")
                        .confirm())
                .scopeConfined()
                .affectedRecords(this::selectIssue)
                .handler(this::handleRemove)
                .build();
    }

    private Object handleRemove(ToolInvocation invocation) {
        Issue issue = requireIssue(invocation, invocation.requiredString("issueKey"));

        // null is the backlog — the same call, with nowhere to move to, which is what the domain means
        // by "not committed" rather than a second method that would have to agree with this one.
        boolean moved = memberships.moveToSprint(issue, null, members.actingSubject(invocation));

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("issueKey", issue.getIssueKey());
        answer.put("removed",  moved);

        if (!moved) {
            answer.put("note", "It was not in a sprint; nothing changed.");
        }

        return answer;
    }

    // ── ─────────────────────────────────────────────────────────────────────────

    private AffectedRecords selectIssue(ToolInvocation invocation) {
        return issues.findByIssueKey(invocation.requiredString("issueKey"))
                .filter(issue -> issue.getProjectId().equals(invocation.scopeId()))
                .map(issue -> AffectedRecords.of(List.of(
                        AffectedRecords.Record.of(issue.getId(), issue.getIssueKey(), "issue"))))
                .orElseGet(AffectedRecords::none);
    }

    /**
     * The sprint under that name, in the project this call is confined to.
     *
     * <p>⚠️ Matched within the scope and never across the installation. Two projects may perfectly well
     * both have a "Sprint 4", and a lookup by name alone would find whichever came first — which is a
     * planning change made to somebody else's team.
     */
    private Sprint requireSprint(ToolInvocation invocation, String name) {
        List<Sprint> inProject = sprints.findByProjectIdOrderByCreatedAtAsc(invocation.scopeId());

        return inProject.stream()
                .filter(sprint -> name.equalsIgnoreCase(sprint.getName()))
                .findFirst()
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                        describeMissingSprint(invocation, name, inProject)));
    }

    /**
     * ⚠️ <strong>"No sprints" and "does not plan in sprints" are two different answers, and this used to
     * give the second for both.</strong> A Scrum project on the day it is created has no sprints yet and
     * plans in them all the same — telling that caller the project "plans on a board of everything" is a
     * statement about the project that contradicts what {@code projects_get} reports, and it sends a
     * model off to look for a board that is not the one it wants. The project itself is the only thing
     * that knows which case this is, so ask it rather than inferring it from an empty list.
     */
    private String describeMissingSprint(ToolInvocation invocation, String name, List<Sprint> inProject) {
        if (inProject.isEmpty()) {
            if (plansInSprints(invocation)) {
                return "This project plans in sprints but none has been created yet, so there is "
                     + "nothing to commit work to. A sprint is started on the backlog screen — see "
                     + "sprints_list, which will show it once it exists.";
            }

            return "This project has no sprints at all — it plans on a board of everything rather than "
                 + "in sprints, so there is nothing to commit work to.";
        }

        return "This project has no sprint called '" + name + "'. Its sprints are: "
             + inProject.stream().map(Sprint::getName).collect(Collectors.joining(", ")) + ".";
    }

    /**
     * Whether this project plans in sprints at all — a property of its board and of nothing else
     * (ADR-0015), read the same way {@code ProjectService} reads it so the two never disagree.
     */
    private boolean plansInSprints(ToolInvocation invocation) {
        return boards.findByProjectId(invocation.scopeId())
                .map(Board::getScopeStrategy)
                .orElse(BoardScopeStrategy.ALL_ISSUES) == BoardScopeStrategy.ACTIVE_SPRINT;
    }

    private Issue requireIssue(ToolInvocation invocation, String issueKey) {
        Issue issue = issues.findByIssueKey(issueKey).orElseThrow(() ->
                new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                        "There is no issue '" + issueKey + "'. Use issues_search to find one — an "
                        + "issue key is never invented."));

        ScopeConfinement.require(
                invocation, issue.getProjectId().equals(invocation.scopeId()), "issue", issueKey);

        return issue;
    }

    private static Map<String, Object> describe(SprintSummary sprint) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("name",  sprint.name());
        described.put("state", sprint.state() == null ? null : sprint.state().name().toLowerCase());
        described.put("goal",  sprint.goal());

        if (sprint.endDate() != null) {
            described.put("endsOn", sprint.endDate().toString());
        }

        return described;
    }
}
