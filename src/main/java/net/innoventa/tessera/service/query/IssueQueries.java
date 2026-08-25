package net.innoventa.tessera.service.query;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.BrowsableProjects;
import org.jmouse.jdbc.dialect.Dialect;
import org.jmouse.query.sql.Fragment;
import org.jmouse.query.sql.QueryTarget;
import org.jmouse.query.spring.builder.QueryRequest;
import org.jmouse.query.spring.builder.QueryRunner;
import org.jmouse.query.spring.source.QuerySources;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Runs a jMQ filter over issues — and it is the <strong>identifiers</strong> that come back.
 *
 * <h2>⚠️ Beside the board filter, never instead of it</h2>
 *
 * <p>{@code BoardFilterEvaluator} marks loaded cards with jME and keeps doing so. This answers a
 * different question — <em>which issues, out of everything</em> — in SQL, so {@code LIMIT} and
 * {@code COUNT(*)} mean what they say. A bounded slice filtered in the application answers correctly
 * right up to the row that falls off the end of it, and then answers wrongly for ever.</p>
 *
 * <h2>⚠️ The scope is what confines this, and it is composed rather than written</h2>
 *
 * <p>An issue is visible because its <strong>project</strong> is browsable. That list comes from
 * {@link BrowsableProjects} — the same answer the ordinary search uses — and is {@code AND}-ed on as its
 * own fragment with its own bound values. So a filter arriving in a URL cannot reach a project the
 * caller does not belong to, and cannot {@code OR} its way past the restriction.</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
@Service
@RequiredArgsConstructor
public class IssueQueries {

    private final QueryRunner       runner;
    private final BrowsableProjects browsableProjects;
    private final QuerySources      sources;
    private final IssueSubject      subject;

    /**
     * The issues this caller may see that satisfy a filter, in its order, for one page.
     *
     * @param caller    who is asking — also what {@code currentMember} means in the filter
     * @param projectId one project, or {@code null} for everything browsable
     * @param filter    the jMQ condition, or blank
     * @param order     the jMQ sort, or blank
     * @param offset    how many rows to skip
     * @param limit     how many to return
     * @return the matching issue identifiers, and the total
     */
    @Transactional(readOnly = true)
    public QueryRunner.Matches matching(
            Member caller,
            String projectId,
            boolean includeArchived,
            String filter,
            String order,
            long offset,
            int limit) {

        List<String> browsable = browsableProjects.idsFor(caller);

        // ⚠️ Nothing browsable means nothing matches, said here rather than by handing an empty IN list
        // to the database — which different databases render differently, and one of them accepts.
        if (browsable.isEmpty()) {
            return new QueryRunner.Matches(List.of(), 0);
        }

        // ⚠️ RESOLVED, never `IssueSchema.source()` directly. Once a declaration can be taken over there
        // are two candidates for what `issues` means, and if this ran the shipped one while the screen
        // showed the stored one, the screen would be a lie — the most convincing kind, because both
        // halves are real. One place decides, and this is a caller of it rather than a second opinion.
        return runner.matching(
                sources.resolve(subject, new QueryRequest(subject.name(), caller.getId(), Map.of()))
                        .orElseGet(IssueSchema::source),
                within(projectId == null ? browsable : narrowed(browsable, projectId), includeArchived),
                filter,
                order,
                Map.of("currentMember", caller.getId()),
                offset,
                limit);
    }

    /**
     * ⚠️ Asking for one project narrows what is browsable; it never widens it. A project identifier in a
     * request is a request, and the answer to a request for somebody else's project is an empty list.
     */
    private List<String> narrowed(List<String> browsable, String projectId) {
        return browsable.contains(projectId) ? List.of(projectId) : List.of();
    }

    /**
     * ⚠️ Archived work is excluded <strong>by the scope</strong>, not by the filter, and that matters.
     *
     * <p>Search is the one read where an archived issue stays reachable at all — an archive nothing can
     * find again is a delete with extra steps — but it is reachable only when it is asked for. Left to
     * the filter, {@code issue.summary is contains('x')} would quietly include work somebody deliberately
     * put away, and the person who wrote that filter would never know it had.</p>
     */
    private BiFunction<QueryTarget, Dialect, Fragment> within(
            List<String> projectIds, boolean includeArchived) {

        return (target, dialect) -> {
            if (projectIds.isEmpty()) {
                return Fragment.of("1 = 0");
            }

            String placeholders = String.join(", ", Collections.nCopies(projectIds.size(), "?"));
            String written      = "%s.%s IN (%s)".formatted(
                    target.alias(), dialect.quote("project_id"), placeholders);

            if (!includeArchived) {
                written += " AND %s.%s IS NULL".formatted(target.alias(), dialect.quote("archived_at"));
            }

            return new Fragment(written, new ArrayList<>(projectIds));
        };
    }
}
