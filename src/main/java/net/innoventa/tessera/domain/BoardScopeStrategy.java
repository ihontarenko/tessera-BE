package net.innoventa.tessera.domain;

/**
 * Which issues a {@link Board} renders — and, by extension, the whole of "this project does Scrum"
 * (ADR-0012). Set from the answer given at project creation and editable afterwards, so switching a
 * Kanban team onto sprints is one setting rather than a new project.
 * <p>
 * Since ADR-0015 this is the <strong>only</strong> stored representation of that fact: it selects the
 * board's issue source, the availability of the Backlog view, and the word the interface shows —
 * {@code ACTIVE_SPRINT} reads as Scrum, {@code ALL_ISSUES} as Kanban. There is no project type left to
 * disagree with it.
 */
public enum BoardScopeStrategy {

    /** The whole project, exactly as the board behaved before sprints existed. */
    ALL_ISSUES,

    /** Only the active sprint's current members — and zero cards when no sprint is running. */
    ACTIVE_SPRINT

}
