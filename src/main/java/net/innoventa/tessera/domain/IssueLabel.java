package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The join attaching a global {@link Label} to an {@link Issue} (ticket 11). Unique per
 * {@code (issue, label)}; removing an issue's label deletes the row, never the shared label.
 */
@Entity
@Table(
    name = "issue_label",
    uniqueConstraints = @UniqueConstraint(name = "uq_issue_label", columnNames = {"issue_id", "label_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueLabel {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "issue_id", nullable = false, length = 36)
    private String issueId;

    @Column(name = "label_id", nullable = false, length = 36)
    private String labelId;

}
