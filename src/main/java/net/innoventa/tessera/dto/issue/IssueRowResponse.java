package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * One row of the dense issue table (ticket 07) — everything shown at a glance, nothing that needs a
 * join per row beyond the caller's batch loads. {@code open} is the {@code resolution IS NULL}
 * invariant surfaced (ADR-0004).
 */
public record IssueRowResponse(
    String id,
    String issueKey,
    int sequence,
    String summary,
    IssueTypeSummary type,
    PrioritySummary priority,
    StatusSummary status,
    ResolutionSummary resolution,
    boolean open,
    MemberSummary assignee,
    MemberSummary reporter,
    Double storyPoints,
    String parentKey,
    String rank,
    LocalDateTime updatedAt
) {
}
