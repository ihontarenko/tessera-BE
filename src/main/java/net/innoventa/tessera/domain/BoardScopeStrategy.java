package net.innoventa.tessera.domain;

/**
 * Which issues a {@link Board} renders — and, by extension, the whole of "this project does Scrum"
 * (ADR-0012). Seeded from the project type's preset ({@link ProjectTypeDefaultScheme}) and editable
 * afterwards, so switching a Kanban team onto sprints is one setting rather than a new project.
 * <p>
 * This field exists precisely so that no code, backend or frontend, ever branches on
 * {@link ProjectType}: it selects the board's issue source <em>and</em> the availability of the
 * Backlog view.
 */
public enum BoardScopeStrategy {

    /** The whole project, exactly as the board behaved before sprints existed. */
    ALL_ISSUES,

    /** Only the active sprint's current members — and zero cards when no sprint is running. */
    ACTIVE_SPRINT

}
