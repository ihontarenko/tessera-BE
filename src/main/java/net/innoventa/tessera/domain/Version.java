package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A release marker within a {@link Project} — issues declare the versions they <em>affect</em> and the
 * versions that will <em>fix</em> them (ticket 06/11). Carries a {@link VersionState} lifecycle rather
 * than released/archived booleans, an optional {@code releaseDate}, and a {@code sequence} that orders
 * versions for display. Project-scoped, unique by name within its project.
 */
@Entity
@Table(
    name = "versions",
    uniqueConstraints = @UniqueConstraint(name = "uq_versions_project_name", columnNames = {"project_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Version {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VersionState state;

    @Column(name = "sequence", nullable = false)
    private int sequence;

}
