package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A first-class, configurable kind of work — Epic, Story, Task, Bug, Sub-task — carrying a
 * {@code hierarchyLevel} (int), not a hard-coded enum (CONTEXT.md, "IssueType"). The level encodes
 * everything: Epic = 1, Story/Task/Bug = 0, Sub-task = -1; higher levels (e.g. Initiative = 2) are
 * addable with no schema change. "Sub-task-ness" is {@code hierarchyLevel < 0} — deliberately no
 * {@code isSubtask} boolean. Global catalog, reused across projects through an {@code IssueTypeScheme}.
 */
@Entity
@Table(name = "issue_types", uniqueConstraints = @UniqueConstraint(name = "uq_issue_types_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueType {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "hierarchy_level", nullable = false)
    private int hierarchyLevel;

    @Column(name = "icon_key", length = 64)
    private String iconKey;

    @Column(length = 255)
    private String description;

}
