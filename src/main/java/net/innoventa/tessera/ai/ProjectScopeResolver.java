package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.repository.ProjectRepository;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.InvocationScope;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolInvocation;
import org.jmouse.ai.ToolRefusedException;
import org.jmouse.ai.spi.ScopeResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns the project a caller <em>named</em> into the project an action runs in.
 *
 * <p><strong>This is the case {@code InvocationScope.kind} was generalised for, and it fits.</strong>
 * Innoventa scopes by workspace, Moneta would scope by persona, and Tessera scopes by project — three
 * products, three words, one mechanism, and nothing in the library had to learn the third. The kind
 * travels as {@code "project"} and every refusal and echo says <em>project</em> because of it.
 *
 * <p>Addressed <strong>by key</strong> rather than by identifier — {@code TES}, not a UUID. A key is
 * what a person writes and what an issue key already contains, so a model that has seen
 * {@code TES-42} in conversation can name the project without a lookup. The name matches too, because
 * a person is as likely to say <em>Tessera</em> as <em>TES</em>.
 *
 * <p>Two rules, the same two every scope resolver in this workspace keeps:
 *
 * <ul>
 *   <li><strong>Never guess.</strong> Anything resolving more than one way is refused, with the
 *       candidates listed so the next call can be right.
 *   <li><strong>Always name what is visible.</strong> A refusal that merely says "no such project"
 *       leaves a model to guess again.
 * </ul>
 *
 * <p>⚠️ <strong>Visibility is what the grants say, and there is no membership table left to ask.</strong>
 * A caller who holds nothing at a project does not see it here at all — the same answer
 * {@code ProjectService.get} gives, from the same rows, which is what keeps a tool and a route from
 * disagreeing about which projects exist.
 */
@Component
@RequiredArgsConstructor
public class ProjectScopeResolver implements ScopeResolver {

    /** Tessera's word for a place, as every refusal and every echo says it to a model. */
    public static final String KIND = "project";

    private final ProjectRepository projectRepository;
    private final ProjectAccess     projectAccess;
    private final MemberService     members;

    @Override
    public InvocationScope resolve(CallerIdentity caller, ToolAction action, String requested) {
        List<Project> visible = visibleProjects(caller);

        if (requested == null || requested.isBlank()) {
            return applyDefault(visible);
        }

        return match(visible, requested.trim());
    }

    private List<Project> visibleProjects(CallerIdentity caller) {
        // Asked of the grants rather than of a membership table — that table is gone (V000014), and a
        // project reached through a personal grant was never in it to begin with.
        List<String> projectIds = projectAccess.visibleProjectIds(members.requireMember(caller.callerId()));

        return projectIds.isEmpty() ? List.of() : projectRepository.findByIdInOrderByKeyAsc(projectIds);
    }

    /** By key first, because a key is exact; by name second, because a name is what a person says. */
    private InvocationScope match(List<Project> visible, String requested) {
        List<Project> matches = visible.stream()
                .filter(project -> requested.equalsIgnoreCase(project.getKey())
                                || requested.equalsIgnoreCase(project.getName()))
                .toList();

        if (matches.size() == 1) {
            return named(matches.getFirst());
        }

        if (matches.isEmpty()) {
            throw new ToolRefusedException(RefusalReason.UNKNOWN_SCOPE,
                    "This caller cannot see a project called '" + requested + "'. " + describe(visible));
        }

        throw new ToolRefusedException(RefusalReason.AMBIGUOUS_SCOPE,
                "'" + requested + "' matches " + matches.size() + " projects this caller can see: "
                + matches.stream().map(this::identify).collect(Collectors.joining(", "))
                + ". Name one by its key — this will not be resolved by guessing.");
    }

    /**
     * A single visible project <em>is</em> the default, unambiguously. More than one is not a default
     * at all, and is refused rather than picked.
     */
    private InvocationScope applyDefault(List<Project> visible) {
        if (visible.size() == 1) {
            return defaulted(visible.getFirst());
        }

        if (visible.isEmpty()) {
            throw new ToolRefusedException(RefusalReason.UNDETERMINED_SCOPE,
                    "This caller is not a member of any project, so there is nothing to act in. Ask a "
                    + "project administrator to add them.");
        }

        throw new ToolRefusedException(RefusalReason.UNDETERMINED_SCOPE,
                "This caller can see " + visible.size() + " projects, so there is no single default "
                + "project to fall back on. Name one with the '" + ToolInvocation.SCOPE_ARGUMENT
                + "' argument: " + visible.stream().map(Project::getKey).collect(Collectors.joining(", "))
                + ".");
    }

    private InvocationScope named(Project project) {
        return InvocationScope.named(KIND, project.getId(), project.getKey());
    }

    private InvocationScope defaulted(Project project) {
        return InvocationScope.defaulted(KIND, project.getId(), project.getKey());
    }

    private String describe(List<Project> visible) {
        if (visible.isEmpty()) {
            return "It is not a member of any project, so there is nothing to name.";
        }

        return "It can see: " + visible.stream().map(this::identify).collect(Collectors.joining(", "))
             + ".";
    }

    private String identify(Project project) {
        return project.getKey() + " (" + project.getName() + ")";
    }
}
