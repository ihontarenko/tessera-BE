package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A first-class, auto-provisioned view onto a project's issues (ADR-0009) — exactly one per project
 * ({@code projectId} unique). Every project has one, with nothing to branch on — since ADR-0015 there
 * is no project type, and this board's {@link BoardScopeStrategy} is the only thing that distinguishes
 * a Scrum project from a Kanban one. The board holds board-wide view settings — the {@link SwimlaneStrategy}, the
 * {@link BoardScopeStrategy} deciding which issues it draws from, and an optional done-threshold —
 * while its ordered {@link BoardColumn}s carry the status→column mapping.
 * <p>
 * Ids are stored flat, no JPA relations, matching the sibling entities.
 */
@Entity
@Table(name = "boards", uniqueConstraints = @UniqueConstraint(name = "uq_boards_project", columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Board {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "swimlane_strategy", nullable = false, length = 16)
    private SwimlaneStrategy swimlaneStrategy;

    /**
     * Which issues the board renders — the whole project or only the active sprint (ADR-0012). Set at
     * creation and editable afterwards; this one field is the whole of "this project does Scrum", and
     * since ADR-0015 there is no project type left for it to disagree with.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_strategy", nullable = false, length = 16)
    private BoardScopeStrategy scopeStrategy;

    /** Completed issues older than this many days drop off the board (Phase-2 ticket 06); null = never. */
    @Column(name = "hide_done_older_than_days")
    private Integer hideDoneOlderThanDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
