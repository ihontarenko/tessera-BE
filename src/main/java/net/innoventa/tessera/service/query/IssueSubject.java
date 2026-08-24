package net.innoventa.tessera.service.query;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.exception.AccessDeniedException;
import net.innoventa.tessera.security.access.AccessReason;
import org.jmouse.query.schema.QuerySchema;
import org.jmouse.query.spring.builder.QueryRequest;
import org.jmouse.query.spring.builder.QuerySubject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Issues, as something a person may write a query about.
 *
 * <h2>⚠️ This does not replace the board filter, and is not meant to</h2>
 *
 * <p>The jME filter on a board <em>marks</em> cards that are already loaded, over a rich object graph —
 * labels, links, the resolved Epic ancestor — and it stays exactly as it is. This is the other question:
 * <em>which issues, out of everything, and in what order</em>, answered by the database so paging and
 * counts are real. The two overlap in vocabulary on purpose and in nothing else.</p>
 *
 * <h2>⚠️ The whole of Tessera's contribution is this class and a schema</h2>
 *
 * <p>No controller, no DTO, no verdict endpoint, no translation between builder rows and text — all of
 * that is {@code jmouse-query-spring-boot}'s and is identical in every product.</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
@Component
@RequiredArgsConstructor
public class IssueSubject implements QuerySubject {

    private final StatusRepository     statuses;
    private final IssueTypeRepository  types;
    private final PriorityRepository   priorities;
    private final ResolutionRepository resolutions;
    private final CurrentMembers       members;

    @Override
    public String name() {
        return "issues";
    }

    /**
     * ⚠️ Signed in, and no more than that — the confinement is the <em>scope</em>, not this gate.
     *
     * <p>What this discloses is the installation's catalogues: status names, issue types, priorities.
     * Everybody with an account already sees all of them on every board. What must not leak is
     * <em>which issues</em>, and {@link IssueQueries} confines that to the projects the caller may
     * browse — so somebody who browses nothing gets an empty answer rather than a refusal, because
     * "you belong to no project" is not a permission failure.</p>
     */
    @Override
    public void authorize(QueryRequest request) {
        if (members.current() == null) {
            throw new AccessDeniedException(AccessReason.UNAUTHENTICATED, "Sign in to write a query.");
        }
    }

    @Override
    public QuerySchema schema(QueryRequest request) {
        return IssueSchema.schema();
    }

    /** So a query can say {@code issue.assignee == currentMember} — the board filter's own idiom. */
    @Override
    public Map<String, Object> values(QueryRequest request) {
        Member caller = members.current();

        return caller == null ? Map.of() : Map.of("currentMember", caller.getId());
    }

    /**
     * ⚠️ The catalogues are read <strong>once</strong> here, not once per attribute — four small queries
     * rather than four per field on a seventeen-field vocabulary.
     *
     * <p>⚠️ A member is deliberately offered no options. There can be thousands, and a filter that means
     * <em>mine</em> is written {@code == currentMember} rather than by finding yourself in a list.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Presentation> presentations(QuerySchema schema, QueryRequest request) {
        Map<String, Presentation> shownAs = new LinkedHashMap<>();

        shownAs.put("issue.summary", plain("Summary"));
        shownAs.put("issue.key", plain("Key"));
        shownAs.put("issue.status.name", new Presentation("Status", statusNames()));
        shownAs.put("issue.status.category", new Presentation("Status category", categories()));
        shownAs.put("issue.type.name", new Presentation("Type", typeNames()));
        shownAs.put("issue.type.hierarchyLevel", plain("Hierarchy level"));
        shownAs.put("issue.priority.name", new Presentation("Priority", priorityNames()));
        shownAs.put("issue.resolution", plain("Resolved"));
        shownAs.put("issue.resolution.name", new Presentation("Resolution", resolutionNames()));
        shownAs.put("issue.assignee", plain("Assignee"));
        shownAs.put("issue.reporter", plain("Reporter"));
        shownAs.put("issue.points", plain("Story points"));
        shownAs.put("issue.parent", plain("Parent"));
        shownAs.put("issue.createdAt", plain("Created"));
        shownAs.put("issue.updatedAt", plain("Updated"));
        shownAs.put("issue.resolvedAt", plain("Resolved at"));
        shownAs.put("issue.archivedAt", plain("Archived at"));

        return shownAs;
    }

    private Presentation plain(String label) {
        return new Presentation(label, List.of());
    }

    private List<String> statusNames() {
        return statuses.findAll().stream().map(Status::getName).sorted().toList();
    }

    private List<String> typeNames() {
        return types.findAll().stream().map(IssueType::getName).sorted().toList();
    }

    private List<String> priorityNames() {
        return priorities.findAll().stream().map(Priority::getName).sorted().toList();
    }

    private List<String> resolutionNames() {
        return resolutions.findAll().stream().map(Resolution::getName).sorted().toList();
    }

    /** ⚠️ From the enum, so a category nobody can write never appears in a list somebody picks from. */
    private List<String> categories() {
        return Arrays.stream(StatusCategory.values()).map(Enum::name).toList();
    }
}
