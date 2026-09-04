package net.innoventa.tessera.service;

import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What a list of issues may be ordered by, and the one place that vocabulary is written down.
 *
 * <h2>⚠️ A closed list, because a sort field is a column name reaching the database</h2>
 *
 * <p>The obvious shape is {@code ?sort=whatever} handed straight to {@code Sort.by}. It is also how a
 * caller gets to order by a column nobody meant to expose, and how a typo becomes a 500 from deep inside
 * Hibernate rather than a sentence. So the wire carries a <em>name this class recognises</em>, and
 * anything else falls back to the default rather than failing — a list somebody asked to sort oddly is
 * still a list, and refusing the whole search over a bad query parameter helps nobody.
 *
 * <h2>⚠️ The same vocabulary the interface offers, and deliberately not a superset</h2>
 *
 * <p>Tessera sorts issues in two places — a project's list, held whole in the browser, and the
 * cross-project search, paged in the database. The first can order anything it can see; the second can
 * order only what SQL can. A field offered in one and missing from the other would make the same control
 * behave differently on two screens, so the vocabulary is the <em>intersection</em>, stated here and
 * mirrored in the interface's `issueSorting.ts`.
 *
 * <p>⚠️ <strong>Rank is not here, and that is the one deliberate gap.</strong> A LexoRank orders a
 * <em>board</em>, and a board belongs to one project; ordering issues from six projects by it would
 * interleave six independent sequences into one meaningless list. The project list offers it and this
 * does not.
 *
 * <h2>⚠️ Two of these are joins, and the join is what makes them worth having</h2>
 *
 * <p>Priority and type are foreign keys on {@code issues}, so ordering by the column would order by an
 * opaque identifier. What somebody means by "sort by priority" is <em>severity</em>, which lives on
 * {@code priorities.sequence} — hence the ad-hoc joins in {@code IssueRepository.search}. Status is
 * ordered by <em>name</em> rather than by category, because the category is stored as a string and would
 * sort {@code DONE} before {@code IN_PROGRESS} before {@code TO_DO}: alphabetical order dressed up as
 * workflow order is worse than plainly alphabetical.
 */
public enum IssueSortOrder {

    /** The key. ⚠️ Sorts as text, which is right because keys are zero-padded — see `Project.keyPattern`. */
    KEY("key", "issueKey"),

    SUMMARY("summary", "summary"),

    /**
     * By hierarchy level, so an epic and the stories under it fall on opposite ends rather than being
     * interleaved alphabetically. ⚠️ Which end is which is the direction's business, not this enum's.
     */
    TYPE("type", "type.hierarchyLevel"),

    /** Alphabetical — what groups the same status together, which is what the sort is used for. */
    STATUS("status", "status.name"),

    /** Most severe first when descending — {@code sequence} is the catalogue's own ordering. */
    PRIORITY("priority", "priority.sequence"),

    /**
     * ⚠️ Nullable, and no null precedence is asked for. MySQL has no {@code NULLS LAST} and emulating it
     * would put a {@code CASE} in front of every ordered read for a nicety; unestimated issues land at
     * one end or the other depending on the engine, which is worth saying out loud and not worth fixing.
     */
    POINTS("points", "storyPoints"),

    /**
     * The day somebody means to pick it up, soonest first when ascending — which is what "what is up
     * next" asks, and the whole reason the column exists.
     *
     * <p>⚠️ Nullable, with no null precedence asked for, exactly as {@link #POINTS} is: MySQL has no
     * {@code NULLS LAST} and emulating it would put a {@code CASE} in front of every ordered read. So
     * unqueued issues land at one end or the other depending on the engine. Say it rather than fix it —
     * anybody sorting by this is looking for the rows that <em>have</em> a date.
     */
    QUEUED_FOR("queuedFor", "queuedFor"),

    /** The warning date, soonest first when ascending. Nullable on the same terms as {@link #QUEUED_FOR}. */
    RED_LINE("redLine", "redLine"),

    /** The day it is due, soonest first when ascending. Nullable on the same terms as {@link #QUEUED_FOR}. */
    DEADLINE("deadline", "deadline"),

    UPDATED("updated", "updatedAt");

    /** What the default is when nobody asks: most recently touched first. */
    public static final IssueSortOrder DEFAULT = UPDATED;

    /** ⚠️ Newest-first is the useful default for a date and the useless one for a name. */
    public static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.DESC;

    private final String wireName;
    private final String property;

    IssueSortOrder(String wireName, String property) {
        this.wireName    = wireName;
        this.property    = property;
    }

    public String wireName() {
        return wireName;
    }

    /** The JPQL path, against the aliases {@code IssueRepository.search} declares. */
    public String property() {
        return property;
    }

    /** The one this name asks for, or empty when it is not a name this build knows. */
    public static Optional<IssueSortOrder> byWireName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }

        String wanted = candidate.trim().toLowerCase(Locale.ROOT);

        return Arrays.stream(values())
            .filter(order -> order.wireName.equals(wanted))
            .findFirst();
    }

    /**
     * The ordering for a request, falling back to the default at every step.
     *
     * <p>⚠️ <strong>Ties are broken by {@code updatedAt}, always.</strong> Ordering a paged query by a
     * column with duplicates — a status, a priority, a story-point value — leaves rows in whichever order
     * the engine happens to produce, which differs between one page and the next: an issue can appear on
     * page one and again on page two, or on neither. A second, near-unique key is what makes paging
     * stable, and it costs nothing on the field that already is one.
     */
    public static Sort resolve(String sortName, String directionName) {
        IssueSortOrder order = byWireName(sortName).orElse(DEFAULT);

        Sort.Direction direction = Sort.Direction.fromOptionalString(directionName)
            .orElse(DEFAULT_DIRECTION);

        Sort primary = Sort.by(direction, order.property);

        if (order == UPDATED) {
            return primary;
        }

        return primary.and(Sort.by(Sort.Direction.DESC, UPDATED.property));
    }

}
