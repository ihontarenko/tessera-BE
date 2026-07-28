package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One ordered column of a {@link Board} (ADR-0010). A column maps N statuses through
 * {@link BoardColumnStatus}; a status not explicitly mapped falls into the column whose
 * {@code fallbackForCategory} equals the status's {@link StatusCategory}. The default board carries
 * three columns — one per category, each a fallback, with zero explicit mappings — so every issue
 * resolves by category. The invariant "exactly one fallback column per category always exists" keeps
 * auto-mapping total, so no issue ever falls off the board.
 * <p>
 * {@code minIssues}/{@code maxIssues} are soft WIP limits — stored and returned, enforced only
 * visually on the client (Phase-2 ticket 03).
 */
@Entity
@Table(name = "board_columns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class BoardColumn {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "board_id", nullable = false, length = 36)
    private String boardId;

    @Column(nullable = false, length = 128)
    private String name;

    /** Left-to-right order on the board. Ordered by this ascending. */
    @Column(nullable = false)
    private int position;

    /** Soft WIP floor — a column below it is flagged, never blocked (ticket 03). Null = no floor. */
    @Column(name = "min_issues")
    private Integer minIssues;

    /** Soft WIP ceiling — a column over it is highlighted, never blocked (ticket 03). Null = no cap. */
    @Column(name = "max_issues")
    private Integer maxIssues;

    /** The category this column is the default home for; null on a column mapping only explicit statuses. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_for_category", length = 16)
    private StatusCategory fallbackForCategory;

}
