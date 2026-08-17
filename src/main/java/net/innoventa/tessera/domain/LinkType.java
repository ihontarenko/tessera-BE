package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A kind of typed relationship between two issues (ticket 12), seeded by migration. Carries an
 * {@code outwardLabel} read from the source ("blocks") and an {@code inwardLabel} read from the target
 * ("is blocked by"). Symmetric types (Relates) share the same label both ways — the direction is still
 * stored, the labels simply match.
 */
@Entity
@Table(name = "link_types", uniqueConstraints = @UniqueConstraint(name = "uq_link_types_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class LinkType {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "outward_label", nullable = false, length = 64)
    private String outwardLabel;

    @Column(name = "inward_label", nullable = false, length = 64)
    private String inwardLabel;

    /**
     * What a link of this type does — see {@link LinkTypeEffect} (TSSR-40).
     *
     * <p>⚠️ <strong>Not null, and {@code NONE} is a real answer.</strong> A nullable effect would make
     * "does nothing" and "nobody said" two values that render the same and behave the same, which is one
     * value too many.
     *
     * <p>⚠️ <strong>This is the single definition of "blocking".</strong> The filter grammar used to
     * decide it by matching the link type's <em>name</em> against the literal {@code "Blocks"}, which
     * broke the moment anybody renamed the row or added a second blocking type. Anything asking whether
     * a link blocks reads this field.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private LinkTypeEffect effect = LinkTypeEffect.NONE;

}
