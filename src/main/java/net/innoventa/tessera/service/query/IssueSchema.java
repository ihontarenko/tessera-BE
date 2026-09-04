package net.innoventa.tessera.service.query;

import org.jmouse.query.schema.QueryAttribute;
import org.jmouse.query.schema.QuerySchema;
import org.jmouse.query.schema.QueryType;
import org.jmouse.query.sql.QuerySource;
import org.jmouse.query.sql.QueryTarget;
import org.jmouse.query.sql.mapping.AttributeMappings;
import org.jmouse.query.sql.mapping.ColumnMapping;
import org.jmouse.query.sql.mapping.JoinMapping;
import org.jmouse.query.sql.mapping.JoinedTable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jmouse.query.schema.QueryAttribute.Access.COLUMN;
import static org.jmouse.query.schema.QueryAttribute.Access.JOINED;

/**
 * What a jMQ query may say about an issue.
 *
 * <h2>⚠️ The same words the board filter already uses, deliberately</h2>
 *
 * <p>People here already write {@code issue.assignee == currentMember} and
 * {@code issue.status.category == 'IN_PROGRESS'} — that is the jME board filter, and it is staying. Every
 * name below is taken from {@code FilterGrammarReference} unchanged. Two filter surfaces on one product
 * are tolerable; two <strong>vocabularies</strong> for the same fields are not, because then knowing one
 * screen actively misleads you about the other.</p>
 *
 * <h2>⚠️ What is deliberately NOT offered, and why</h2>
 *
 * <ul>
 *   <li>{@code issue.labels} — {@code issue_label} holds label <em>identifiers</em>, not names, so a
 *       collection over it would have people filtering on a UUID. It needs a join inside the collection,
 *       which the mapping does not do yet.</li>
 *   <li>{@code issue.links} — same shape, same reason.</li>
 *   <li>{@code issue.epic.key} — the Epic ancestor is <em>resolved</em> by walking the hierarchy, which
 *       is a recursive read rather than a column. The board filter can do it because it evaluates over
 *       loaded cards; SQL here would need a recursive CTE.</li>
 * </ul>
 *
 * <p>Each of those is a real gap and is better as an honest absence than as an attribute that answers
 * about something else.</p>
 *
 * <h2>⚠️ One join per table, so a member is compared by identity</h2>
 *
 * <p>{@code JoinMapping} keys its alias on the table, so {@code members} can be reached once. Assignee
 * and reporter are therefore compared as identifiers — which is exactly what
 * {@code issue.assignee == currentMember} means and what the board filter already documents
 * (<em>"members compare by identity, so a renamed person still matches"</em>).</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
public final class IssueSchema {

    /** The name a query writes after {@code from}. */
    public static final String ISSUES = "issues";

    private static final String TABLE = "issues";
    private static final String ALIAS = "i";
    private static final String KEY   = "id";

    private static final JoinedTable STATUSES    = new JoinedTable("statuses", "status_id", "id");
    private static final JoinedTable TYPES       = new JoinedTable("issue_types", "issue_type_id", "id");
    private static final JoinedTable PRIORITIES  = new JoinedTable("priorities", "priority_id", "id");
    private static final JoinedTable RESOLUTIONS = new JoinedTable("resolutions", "resolution_id", "id");

    /**
     * ⚠️ Ordered as somebody scanning a list of fields would want them, not as the table declares them.
     * What people filter by first is what they should see first.
     */
    private static final List<QueryAttribute> ATTRIBUTES = List.of(
            new QueryAttribute("issue.summary", "summary", QueryType.TEXT, COLUMN),
            new QueryAttribute("issue.key", "issue_key", QueryType.TEXT, COLUMN),

            new QueryAttribute("issue.status.name", "statuses.name", QueryType.TEXT, JOINED),
            new QueryAttribute("issue.status.category", "statuses.category", QueryType.TEXT, JOINED),
            new QueryAttribute("issue.type.name", "issue_types.name", QueryType.TEXT, JOINED),
            new QueryAttribute("issue.type.hierarchyLevel", "issue_types.hierarchy_level",
                               QueryType.NUMBER, JOINED),
            new QueryAttribute("issue.priority.name", "priorities.name", QueryType.TEXT, JOINED),

            // ⚠️ The COLUMN, not the join: `issue.resolution is null` is how this product says "open", and
            // it has to keep meaning that. A joined name would be null for the same rows, but by accident.
            new QueryAttribute("issue.resolution", "resolution_id", QueryType.TEXT, COLUMN),
            new QueryAttribute("issue.resolution.name", "resolutions.name", QueryType.TEXT, JOINED),

            new QueryAttribute("issue.assignee", "assignee_member_id", QueryType.TEXT, COLUMN),
            new QueryAttribute("issue.reporter", "reporter_member_id", QueryType.TEXT, COLUMN),

            new QueryAttribute("issue.points", "story_points", QueryType.NUMBER, COLUMN),
            new QueryAttribute("issue.parent", "parent_id", QueryType.TEXT, COLUMN),

            // The schedule. ⚠️ TEMPORAL like the timestamps beside them, though these are DATE columns:
            // a query says `issue.deadline <= today` either way, and the type here decides which
            // comparisons the grammar offers rather than what the column holds.
            new QueryAttribute("issue.queuedFor", "queued_for", QueryType.TEMPORAL, COLUMN),
            new QueryAttribute("issue.redLine", "red_line", QueryType.TEMPORAL, COLUMN),
            new QueryAttribute("issue.deadline", "deadline", QueryType.TEMPORAL, COLUMN),

            new QueryAttribute("issue.createdAt", "created_at", QueryType.TEMPORAL, COLUMN),
            new QueryAttribute("issue.updatedAt", "updated_at", QueryType.TEMPORAL, COLUMN),
            new QueryAttribute("issue.resolvedAt", "resolved_at", QueryType.TEMPORAL, COLUMN),
            new QueryAttribute("issue.archivedAt", "archived_at", QueryType.TEMPORAL, COLUMN));

    private IssueSchema() {
    }

    /** The issue source, ready to compile against. */
    public static QuerySource source() {
        return new QuerySource(
                new QueryTarget(ISSUES, TABLE, ALIAS, KEY),
                schema(),
                AttributeMappings.byAccess(
                        ColumnMapping.qualified(),
                        AttributeMappings.refusing("a value bag"),
                        JoinMapping.of(STATUSES, TYPES, PRIORITIES, RESOLUTIONS)));
    }

    /** What may be asked. */
    public static QuerySchema schema() {
        Map<String, QueryAttribute> byName = new LinkedHashMap<>();

        ATTRIBUTES.forEach(attribute -> byName.put(attribute.name(), attribute));

        return new QuerySchema() {

            @Override
            public Optional<QueryAttribute> attribute(String name) {
                return Optional.ofNullable(byName.get(name));
            }

            @Override
            public Collection<QueryAttribute> attributes() {
                return byName.values();
            }
        };
    }
}
