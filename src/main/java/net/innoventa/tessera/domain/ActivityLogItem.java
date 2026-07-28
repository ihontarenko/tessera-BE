package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One field's change within an {@link ActivityLog} event (ADR-0007). {@code oldValue}/{@code newValue}
 * are <strong>point-in-time display-string snapshots</strong> captured at change time — not FKs — so a
 * later rename of a status, member or version never rewrites what the history showed. {@code field} is
 * a stable identifier (e.g. {@code status}, {@code assignee}) the UI localizes.
 */
@Entity
@Table(name = "activity_log_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ActivityLogItem {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "activity_log_id", nullable = false, length = 36)
    private String activityLogId;

    @Column(nullable = false, length = 64)
    private String field;

    @Column(name = "old_value", length = 1024)
    private String oldValue;

    @Column(name = "new_value", length = 1024)
    private String newValue;

}
