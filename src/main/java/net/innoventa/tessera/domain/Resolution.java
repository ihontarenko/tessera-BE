package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A first-class reason an issue closed — Done, Won't Do, Duplicate, Cannot Reproduce. Global catalog,
 * <strong>no scheme</strong> (like {@link Priority}). On an issue it is a nullable FK, an axis
 * independent of {@link Status}: an issue is open ⇔ resolution IS NULL, closed ⇔ it is set — the
 * canonical invariant (ADR-0004). Set/cleared by workflow transitions, never as a free field.
 */
@Entity
@Table(name = "resolutions", uniqueConstraints = @UniqueConstraint(name = "uq_resolutions_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Resolution {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

}
