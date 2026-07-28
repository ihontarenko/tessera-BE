package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One {@link IssueType} membership in an {@link IssueTypeScheme}, with a {@code sequence} giving the
 * scheme its ordered set. Flat join entity with its own id, matching the sibling codebases' style
 * (see Moneta's {@code TransactionTag}).
 */
@Entity
@Table(
    name = "issue_type_scheme_items",
    uniqueConstraints = @UniqueConstraint(name = "uq_issue_type_scheme_item", columnNames = {"scheme_id", "issue_type_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueTypeSchemeItem {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "scheme_id", nullable = false, length = 36)
    private String schemeId;

    @Column(name = "issue_type_id", nullable = false, length = 36)
    private String issueTypeId;

    @Column(name = "sequence", nullable = false)
    private int sequence;

}
