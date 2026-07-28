package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A free-text tag, global and created on the fly (ticket 11) — deliberately not an admin-managed
 * catalog: any member with {@code EDIT_ISSUE} mints a new label simply by applying an unseen string.
 * Keyed by unique {@code name} so the same tag reused across issues is one row; issues attach to it
 * through {@link IssueLabel}.
 */
@Entity
@Table(name = "labels", uniqueConstraints = @UniqueConstraint(name = "uq_labels_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Label {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

}
