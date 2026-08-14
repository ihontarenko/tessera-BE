package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named, ordered scale a project estimates on — Fibonacci, T-shirt, Powers of two (ADR-0019).
 *
 * <p>The third scheme entity, and deliberately the same shape as the other two: a name, a description,
 * and an ordered list of items in {@link EstimationSchemeItem}. Two projects may share one, and editing
 * it affects both.
 *
 * <p>⚠️ <strong>A project may point at none, and that is not a scheme called "None".</strong>
 * {@code projects.estimation_scheme_id} is nullable and null means the project does not estimate — the
 * story-points control disappears entirely rather than offering an empty select.
 */
@Entity
@Table(name = "estimation_schemes", uniqueConstraints = @UniqueConstraint(name = "uq_estimation_schemes_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class EstimationScheme {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

}
