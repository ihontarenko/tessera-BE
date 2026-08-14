package net.innoventa.tessera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * That one {@link net.innoventa.tessera.bootstrap.BootstrapStep} has run, and what it looked like when
 * it did.
 *
 * <p>Flyway's history table, for rows rather than for schema. The {@code checksum} is what makes it more
 * than a flag: a step whose source changed after it ran stops the application, because from inside
 * there is no way to tell a typo somebody fixed from an installation that quietly diverged.
 *
 * <p>⚠️ <strong>Keyed per step, never one flag per installation.</strong> A single "already
 * bootstrapped" boolean fills the tables on the first start and fills nothing ever again, so a release
 * adding a permission to a role reaches a fresh installation and misses every existing one — silently,
 * looking exactly like a feature that shipped without its authorization.
 */
@Entity
@Table(name = "bootstrap_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class BootstrapRecord {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "step_key", nullable = false, length = 128, unique = true)
    private String stepKey;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    /** Never a person. {@code bootstrap-ledger}, and pretending otherwise would make the column useless. */
    @Column(name = "applied_by", nullable = false, length = 64)
    private String appliedBy;

    /** What the step was for, and what it turned out to do — both, because the second is the interesting one. */
    @Column(length = 512)
    private String note;

}
