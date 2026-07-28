package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A first-class ranking of urgency (Highest…Lowest). Global catalog with <strong>no scheme</strong>
 * (ADR-0001) — every project shares the same priorities. {@code sequence} orders them for display
 * (1 = Highest); a level value, not a boolean, in keeping with the model's "prefer level/state over
 * flag" principle.
 */
@Entity
@Table(name = "priorities", uniqueConstraints = @UniqueConstraint(name = "uq_priorities_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Priority {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(length = 16)
    private String color;

}
