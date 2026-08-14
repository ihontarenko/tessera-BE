package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One option on an estimation scale: what it is called, and what it counts as.
 *
 * <p>⚠️ <strong>The {@code (label, weight)} pair is the whole design (ADR-0019).</strong>
 * {@code issues.story_points} stores the <em>weight</em> — {@code XL} is stored as {@code 8} — so
 * burndown, velocity, {@code story_points_at_add} and every jME filter add numbers today and add the
 * same numbers afterwards. Nothing in the reporting layer learns that scales exist.
 *
 * <p>⚠️ <strong>Two items may share a weight with different labels.</strong> Nothing breaks; the reverse
 * lookup takes the first in {@code sequence} order. That is a documented consequence of storing the
 * weight rather than a tie-break rule worth inventing.
 */
@Entity
@Table(
    name = "estimation_scheme_items",
    uniqueConstraints = @UniqueConstraint(name = "uq_estimation_scheme_item", columnNames = {"scheme_id", "label"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class EstimationSchemeItem {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "scheme_id", nullable = false, length = 36)
    private String schemeId;

    /** What a person picks — {@code XL}, or {@code 8} where the scale is numeric. */
    @Column(nullable = false, length = 32)
    private String label;

    /** What it counts as, and what is stored on the issue. */
    @Column(nullable = false)
    private double weight;

    @Column(name = "sequence", nullable = false)
    private int sequence;

}
