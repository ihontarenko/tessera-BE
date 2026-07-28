package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One issue's membership of one sprint — when it joined, when it left, and what it was estimated at
 * when it joined. The sprint's history is therefore a fact in the database rather than a
 * reconstruction, and reports read it directly.
 * <p>
 * Row identity is {@code (sprintId, issueId)}: re-adding an issue to a sprint it was removed from
 * <strong>revives this row</strong> ({@code removedAt} cleared, the other three fields reset to the new
 * act) rather than writing a second one. Carry-over at sprint close instead produces a row in the
 * <em>next</em> sprint, so an issue that took two sprints has two rows and both sprint reports are
 * correct.
 * <p>
 * {@link #storyPointsAtAdd} freezes the estimate at the moment membership begins — a later re-estimate
 * deliberately does not rewrite what was committed to. Null means unestimated and counts as zero.
 */
@Entity
@Table(
    name = "sprint_issues",
    uniqueConstraints = @UniqueConstraint(name = "uq_sprint_issues_sprint_issue", columnNames = {"sprint_id", "issue_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SprintIssue {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "sprint_id", nullable = false, length = 36)
    private String sprintId;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    /** Null ⇔ the issue is still a member. Set when it is dragged out or de-scoped. */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "story_points_at_add")
    private Double storyPointsAtAdd;

    /** Who committed the issue — the actor the activity log attributes the change to. */
    @Column(name = "added_by_member_id", nullable = false, length = 36)
    private String addedByMemberId;

}
