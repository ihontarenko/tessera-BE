package net.innoventa.tessera.domain;

/**
 * {@code SCRUM | KANBAN | TODO}. The types differ purely by <strong>scheme presets + which view the
 * UI shows</strong>, never by backend branching (CONTEXT.md, "Project type"). A TODO project is the
 * same machinery as Scrum/Kanban with simpler seeded schemes — there is deliberately no
 * {@code if (type == TODO)} behavioural branch anywhere in the backend; the type→scheme mapping is
 * data ({@link ProjectTypeDefaultScheme}), not code.
 */
public enum ProjectType {
    SCRUM,
    KANBAN,
    TODO
}
