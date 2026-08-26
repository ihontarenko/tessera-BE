package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * One row of the dense issue table (ticket 07) — everything shown at a glance, nothing that needs a
 * join per row beyond the caller's batch loads. {@code open} is the {@code resolution IS NULL}
 * invariant surfaced (ADR-0004); {@code archivedAt} is the second axis beside it (TSSR-4), non-null only
 * on a row the Shipped screen is showing, since every other list drops archived issues entirely.
 */
public record IssueRowResponse(
    String id,
    String issueKey,
    /**
     * The permanent identifier — see {@code Issue.hash}.
     *
     * ⚠️ On a <em>row</em> because a row is where somebody decides to quote something: a picker offering
     * issues and a list offering "copy reference" both need it, and a second request per row to fetch
     * six characters is the kind of thing that turns a list into a waterfall.
     */
    String hash,
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
    LocalDateTime resolvedAt,
    LocalDateTime archivedAt,
    LocalDateTime updatedAt
) {
}
