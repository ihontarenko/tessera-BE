package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.IssueType;

/**
 * ADR-0014 — the planning unit is a hierarchy-level-0 issue — expressed once, so nothing re-derives it.
 * Only a planning unit may hold sprint membership: a sub-task ({@code < 0}) inherits its parent's sprint
 * as a value that is never stored, so a story and its sub-tasks cannot be split across sprints and story
 * points cannot double-count; an epic ({@code > 0}) is a container and is never committed.
 * <p>
 * The rule is expressed against {@link IssueType#getHierarchyLevel()}, <strong>never</strong> against a
 * type name, so a project that adds an {@code Initiative} at level 2 needs no code change here.
 */
public final class PlanningUnit {

    /** The level that <em>is</em> "a thing you commit to" — Story, Task, Bug. */
    static final int HIERARCHY_LEVEL = 0;

    /** May this issue's type hold sprint membership? An unknown type is not a planning unit. */
    public static boolean isPlanningUnit(IssueType type) {
        return type != null && type.getHierarchyLevel() == HIERARCHY_LEVEL;
    }

    /** Below the planning level — a fragment of a planning unit, committed only through its parent. */
    public static boolean isSubTask(IssueType type) {
        return type != null && type.getHierarchyLevel() < HIERARCHY_LEVEL;
    }

    private PlanningUnit() {
    }

}
