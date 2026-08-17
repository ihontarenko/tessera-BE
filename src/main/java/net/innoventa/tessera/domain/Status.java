package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A first-class workflow state (e.g. "In Review") belonging to exactly one {@link StatusCategory}.
 * Global catalog, reused across projects; a status enters a project <em>through</em> its workflows,
 * never by a direct project link — it has no scheme of its own (ADR-0001). The category, not the
 * name, decides whether a transition into this status closes an issue (ADR-0004).
 */
@Entity
@Table(name = "statuses", uniqueConstraints = @UniqueConstraint(name = "uq_statuses_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Status {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatusCategory category;

    /**
     * A CSS colour the pill is drawn in, or {@code null} to be drawn from the {@link #category}.
     *
     * <p>⚠️ <strong>Null is a meaning, not a gap.</strong> It says "whatever this category looks like",
     * which is how every status looked before there was a column — so a team that never opens the
     * picker sees no change, and clearing the field is a real way back to the default rather than a
     * value to invent.
     *
     * <p>Purely presentational: nothing in the workflow, the board or the closed/open invariant reads
     * it. The {@link #category} remains the field that carries meaning (ADR-0004).
     */
    @Column(length = 16)
    private String color;

}
