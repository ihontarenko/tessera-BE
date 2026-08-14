package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
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
        return List.of(list());
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

    /**
     * Narrowed on purpose. A project response carries scheme identifiers, key strategies and the
     * caller's own permission list — all of it real, none of it anything a model asked about, and each
     * one an identifier it might then try to use somewhere it does not belong.
     */
    private Map<String, Object> describe(ProjectResponse project) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("key",  project.key());
        described.put("name", project.name());

        if (project.lead() != null) {
            described.put("lead", project.lead().displayName());
        }

        return described;
    }
}
