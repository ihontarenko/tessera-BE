package net.innoventa.tessera.service.filter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a filter expression is allowed to see — the {@code issue} root variable of every jME predicate
 * (ADR-0008). Deliberately its own model rather than {@code BoardCardView} or the {@code Issue}
 * entity: a transport DTO changes whenever the UI does, and coupling the filter grammar to it would
 * make every card-rendering tweak a breaking change to expressions members have saved. This record
 * <em>is</em> the grammar's accessor surface — adding a field here is the only way to widen what a
 * filter can ask about.
 * <p>
 * jME resolves property access natively, so {@code issue.status.category} needs no mapping layer: it
 * lands on {@code status().category()}. Enum-valued fields are carried as {@code String} so a
 * predicate can compare against a literal ({@code issue.status.category == 'IN_PROGRESS'}) instead of
 * needing a Java type it cannot name.
 * <p>
 * Timestamps are {@link Instant}, not {@code LocalDateTime}, because that is the only type jME's
 * temporal filters accept: {@code minusDays} returns {@code null} for anything else, and a comparison
 * against {@code null} evaluates <em>true</em> — so a {@code LocalDateTime} here would not fail, it
 * would quietly match every card. Carrying {@code Instant} also leaves the ADR's {@code | instant}
 * idiom valid as a harmless no-op, so both spellings behave.
 */
public record IssueFilterView(
    String id,
    String key,
    String summary,
    TypeReference type,
    PriorityReference priority,
    StatusReference status,
    ResolutionReference resolution,
    MemberReference assignee,
    MemberReference reporter,
    EpicReference epic,
    List<String> labels,
    List<String> components,
    List<String> versions,
    List<LinkReference> links,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt
) {

    /** {@code issue.type.name == 'Bug'}, {@code issue.type.hierarchyLevel == 0}. */
    public record TypeReference(String name, int hierarchyLevel) {
    }

    /** {@code issue.priority.name == 'Highest'}. */
    public record PriorityReference(String name) {
    }

    /** {@code issue.status.category == 'DONE'} — the category, never the status name (ADR-0004). */
    public record StatusReference(String name, String category) {
    }

    /** Null ⇔ the issue is open (ADR-0004), which is what {@code issue.resolution is null} asks. */
    public record ResolutionReference(String name) {
    }

    /** The resolved Epic ancestor, not the direct parent — {@code issue.epic.key == 'TIC-42'}. */
    public record EpicReference(String key) {
    }

    /**
     * One end of a typed relationship, seen from this issue.
     * <p>
     * A link is stored once, source → target, so the same row reads two ways: {@code direction} says
     * which end this issue is standing on. That is the whole reason {@code is blocked} can be answered
     * at all — "blocked" is the <em>inward</em> half of a Blocks link, and without the direction the
     * blocker and the blocked would be indistinguishable.
     */
    public record LinkReference(String type, String direction, String issueKey) {

        /** This issue is the source: it {@code blocks} the other one. */
        public static final String OUTWARD = "OUTWARD";

        /** This issue is the target: it {@code is blocked by} the other one. */
        public static final String INWARD = "INWARD";

    }

    /**
     * A person a predicate can compare against {@code currentMember}.
     * <p>
     * {@code issue.assignee == currentMember} compiles down to {@code equals()}, and a record's
     * generated {@code equals} compares <em>every</em> component — so two snapshots of the same member
     * taken either side of a display-name edit would be unequal and the filter would silently drop the
     * card. Identity here is the id and nothing else; {@code displayName} rides along only so an
     * expression can read it.
     */
    public record MemberReference(String id, String displayName) {

        @Override
        public boolean equals(Object other) {
            return other instanceof MemberReference reference && Objects.equals(id, reference.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

    }

}
