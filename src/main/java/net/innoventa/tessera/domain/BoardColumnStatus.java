package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * An explicit status→column mapping on a {@link Board} (ADR-0010) — present only when an administrator
 * deviates from the category default; a default board has none. {@code boardId} is denormalised onto
 * the row so a status maps to at most one column <em>per board</em> (the unique constraint), and the
 * board-render path prefetches every mapping of a board in one {@code boardId} lookup — no per-card
 * join. Ids are stored flat, no JPA relations.
 */
@Entity
@Table(
    name = "board_column_statuses",
    uniqueConstraints = @UniqueConstraint(name = "uq_board_column_statuses_board_status", columnNames = {"board_id", "status_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class BoardColumnStatus {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "board_id", nullable = false, length = 36)
    private String boardId;

    @Column(name = "board_column_id", nullable = false, length = 36)
    private String boardColumnId;

    @Column(name = "status_id", nullable = false, length = 36)
    private String statusId;

}
