package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named, reusable ordered set of {@link IssueType}s plus a default type (CONTEXT.md, "Scheme";
 * ADR-0001). A {@code Project} points at one. The membership + ordering live in
 * {@link IssueTypeSchemeItem}; two projects may share one scheme, and editing it affects both — by
 * design. One of exactly two scheme entities (the other is {@link WorkflowScheme}).
 */
@Entity
@Table(name = "issue_type_schemes", uniqueConstraints = @UniqueConstraint(name = "uq_issue_type_schemes_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IssueTypeScheme {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "default_issue_type_id", nullable = false, length = 36)
    private String defaultIssueTypeId;

    @Column(length = 255)
    private String description;

}
