package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.dto.project.CreateProjectRequest;
import net.innoventa.tessera.dto.project.ProjectResponse;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.ProjectService;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which projects there are to work in.
 *
 * <p><strong>The one action that is deliberately not confined to a project</strong>, because it is how
 * a caller finds out which projects exist at all. Confining it would need a project argument to
 * discover projects, which is a circle — the same shape as {@code spaces.list} in Innoventa, and the
 * same reason.
 *
 * <p>It answers with the <strong>key</strong> first, because the key is what every other action takes
 * in its {@code scope} argument and what an issue key already contains.
 */
@Component
@RequiredArgsConstructor
public class ProjectTool implements ToolDefinition {

    private final ProjectService projectService;
    private final ToolMembers    members;

    @Override
    public String toolName() {
        return "projects";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(list(), get(), create());
    }

    private ToolAction list() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("list")
                .title("List projects")
                .description("Lists every project this person is a member of, with the key to pass as "
                           + "the 'scope' argument of other actions. A person only ever sees projects "
                           + "they belong to — a project they are not a member of does not appear here "
                           + "and cannot be named.")
                .inputSchema(ArgumentSchema.none())
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .handler(this::handleList)
                .build();
    }

    private Object handleList(ToolInvocation invocation) {
        return projectService.list(members.actingSubject(invocation)).stream()
                .map(this::describe)
                .toList();
    }

    // ── One project ──────────────────────────────────────────────────────────────

    private ToolAction get() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("get")
                .title("Read one project")
                .description("Reads one project — its name, its lead, and whether it plans in sprints. "
                           + "Ask for this before planning work in a project, because whether a sprint "
                           + "exists to put an issue into is a property of the project and not "
                           + "something to assume.")
                .inputSchema(ArgumentSchema.builder()
                        .scope(ProjectScopeResolver.KIND, "projects_list"))
                .requiredPermission(Permissions.BROWSE_PROJECT)
                .readOnly()
                .scopeConfined()
                .handler(this::handleGet)
                .build();
    }

    private Object handleGet(ToolInvocation invocation) {
        ProjectResponse project =
                projectService.get(members.actingSubject(invocation), invocation.scopeId());

        Map<String, Object> described = describe(project);

        // Only on the single read: a list of twenty projects does not need this twenty times, and the
        // one question it answers — "is there a backlog and sprints here" — is asked about one project.
        described.put("planning",
                project.boardScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT ? "sprints" : "board");

        return described;
    }

    // ── Making one ───────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>Not scope-confined, and it cannot be:</strong> there is no project yet to be confined
     * to. It is therefore gated by the scope-free question alone, which is why the permission it declares
     * has to mean something installation-wide.
     *
     * <p>⚠️ <strong>It used to declare {@link Permissions#BROWSE_PROJECT}</strong> — the only permission
     * it could honestly borrow, on the reasoning that inventing one during a cutover would be a new rule
     * wearing a refactor's clothes. The borrowing had a consequence nobody intended: {@code
     * project:browse} is carried by the three {@code @PROJECT} roles and by nothing else, so creating a
     * project through this tool required already belonging to one and a person who belonged to none could
     * never create their first. {@link Permissions#CREATE_PROJECT} is the honest name for what this costs,
     * and the note on it explains why the HTTP route deliberately stays open to any signed-in caller.
     *
     * <p>Confirmed, though. A project is the one thing in this product that cannot be deleted through
     * any surface at all, so a mistaken one is permanent — which is a stronger argument for asking than
     * most deletes have.
     */
    private ToolAction create() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("create")
                .title("Create a project")
                .description("Creates a project and makes the caller its administrator. The key is what "
                           + "every issue in it will be prefixed with — TES-42 — and is permanent: it "
                           + "must be uppercase letters and digits, starting with a letter. Choose it "
                           + "with the person rather than for them.")
                .inputSchema(ArgumentSchema.builder()
                        .requiredString("name", "What the project is called, for people to read.")
                        .requiredString("key",
                                "The permanent issue-key prefix, uppercase letters and digits — TES, "
                              + "PLATFORM. Cannot be changed afterwards.")
                        .optionalBoolean("plansInSprints",
                                "True for a Scrum project — a backlog, sprints and a sprint-scoped "
                              + "board. False or omitted for a board of everything.")
                        .optionalString("icon",
                                "One emoji standing for the project in every list — 🚀, 📦. Omit for "
                              + "none; anything that is not a single emoji is refused.")
                        .confirm())
                .requiredPermission(Permissions.CREATE_PROJECT)
                .handler(this::handleCreate)
                .build();
    }

    private Object handleCreate(ToolInvocation invocation) {
        BoardScopeStrategy planning = invocation.flag("plansInSprints")
                ? BoardScopeStrategy.ACTIVE_SPRINT
                : BoardScopeStrategy.ALL_ISSUES;

        ProjectResponse created = projectService.create(
                members.actingSubject(invocation),
                new CreateProjectRequest(
                        invocation.requiredString("name"),
                        invocation.requiredString("key"),
                        planning,
                        // The lead is the creator unless somebody says otherwise, and saying otherwise
                        // needs a member identifier a model has no way to know. It is a screen's job.
                        null,
                        invocation.optionalString("icon").orElse(null)));

        Map<String, Object> answer = new LinkedHashMap<>(describe(created));

        answer.put("created", true);

        return answer;
    }

    /**
     * Narrowed on purpose. A project response carries scheme identifiers, key strategies and the
     * caller's own permission list — all of it real, none of it anything a model asked about, and each
     * one an identifier it might then try to use somewhere it does not belong.
     */
    private Map<String, Object> describe(ProjectResponse project) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",  project.key());
        described.put("name", project.name());

        if (project.icon() != null) {
            described.put("icon", project.icon());
        }

        if (project.lead() != null) {
            described.put("lead", project.lead().displayName());
        }

        return described;
    }
}
