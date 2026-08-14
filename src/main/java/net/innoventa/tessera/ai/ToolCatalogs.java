package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.service.ProjectIssueTypeService;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolRefusedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turning a name a model wrote into an identifier the domain takes.
 *
 * <p><strong>An identifier is never an argument.</strong> Every catalog in Tessera is keyed by a
 * surrogate string — {@code issue-type-story}, {@code priority-high} — and a schema that asked for one
 * would be asking a model to produce a value it has no way to know. It would then guess, and a guess
 * that happens to be a real identifier from another project is the worst outcome available.
 *
 * <p>So the tools take <em>names</em>, and this is where a name becomes a row. It is the same rule
 * {@code IssueTool.requireTargetStatus} follows for a status and {@code ProjectScopeResolver} follows
 * for a project — never guess, and always name what is visible.
 *
 * <p>⚠️ <strong>A refusal here lists what would have worked.</strong> "There is no issue type 'Task'"
 * is true and leaves a model to try another word; naming the four the project actually offers ends the
 * guessing in one call. That is the whole finding of ticket 18 applied to arguments rather than to
 * workflow transitions.
 *
 * <p>⚠️ <strong>Matched case-insensitively, and that is deliberate.</strong> A model writes what a
 * person said, and a person says "story" as often as "Story". Refusing over a capital letter would be
 * a refusal about typography wearing a refusal about vocabulary's clothes.
 */
@Component
@RequiredArgsConstructor
public class ToolCatalogs {

    private final ProjectIssueTypeService issueTypes;
    private final PriorityRepository      priorities;

    /**
     * The issue type this project may create under that name.
     *
     * <p>⚠️ <strong>Asked of the project, not of the installation.</strong> A project's scheme narrows
     * what it may raise, and the global catalog is the wrong list to create from — the same distinction
     * {@code GET /api/projects/&#123;id&#125;/issue-types} exists for. A type that exists but is not in
     * this project's scheme is refused exactly like one that does not exist, because from where the
     * caller stands the difference is nothing.
     *
     * @param name the type as a person would say it, or null to take the project's default
     */
    public String issueTypeIdFor(Project project, String name) {
        ProjectIssueTypesResponse creatable = issueTypes.listCreatableIssueTypes(project);

        if (name == null || name.isBlank()) {
            return requireDefault(creatable);
        }

        return creatable.issueTypes().stream()
                .filter(type -> name.equalsIgnoreCase(type.name()))
                .findFirst()
                .map(IssueTypeResponse::id)
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                        "'" + project.getKey() + "' has no issue type called '" + name + "'. It can "
                        + "raise: " + names(creatable) + "."));
    }

    /**
     * The priority under that name, or the lowest-sequence one where none was given.
     *
     * <p>Priorities are installation-wide — no scheme narrows them — so this asks the catalog directly.
     */
    public String priorityIdFor(String name) {
        List<Priority> all = priorities.findAllByOrderBySequenceAsc();

        if (name == null || name.isBlank()) {
            return all.stream()
                    .findFirst()
                    .map(Priority::getId)
                    .orElseThrow(() -> new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                            "This installation has no priorities configured, so an issue cannot be "
                            + "raised at all. That is a configuration to fix rather than a call to "
                            + "retry."));
        }

        return all.stream()
                .filter(priority -> name.equalsIgnoreCase(priority.getName()))
                .findFirst()
                .map(Priority::getId)
                .orElseThrow(() -> new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                        "There is no priority called '" + name + "'. The priorities are: "
                        + all.stream().map(Priority::getName).collect(Collectors.joining(", ")) + "."));
    }

    private String requireDefault(ProjectIssueTypesResponse creatable) {
        if (creatable.defaultIssueTypeId() != null) {
            return creatable.defaultIssueTypeId();
        }

        throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                "This project has no default issue type, so one has to be named. It can raise: "
                + names(creatable) + ".");
    }

    private String names(ProjectIssueTypesResponse creatable) {
        return creatable.issueTypes().stream()
                .map(IssueTypeResponse::name)
                .collect(Collectors.joining(", "));
    }
}
