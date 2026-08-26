package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The full issue as the detail modal sees it (tickets 07–13): core fields plus hierarchy (parent +
 * children), labels, links, and the legal transitions available from the current status. {@code open}
 * surfaces the {@code resolution IS NULL} invariant (ADR-0004). Comments and history are loaded by
 * their own endpoints, not embedded here.
 */
public record IssueResponse(
    String id,
    String projectId,
    String issueKey,
    /**
     * The permanent identifier — see {@code Issue.hash}.
     *
     * ⚠️ <strong>Here so that a reference can be WRITTEN.</strong> Anything storing a link to this issue
     * outside the database — a wiki page, another product's description — has to be able to reach the
     * one part of it that never moves, and a value nothing hands out is a value nobody can quote.
     */
    String hash,
    int sequence,
    String summary,
    String description,
    IssueTypeSummary type,
    PrioritySummary priority,
    StatusSummary status,
    ResolutionSummary resolution,
    boolean open,
    MemberSummary reporter,
    MemberSummary assignee,
    IssueReference parent,
    List<IssueReference> children,
    Double storyPoints,
    String rank,
    List<String> labels,
    List<IssueLinkView> links,
    /**
     * The issue keys holding this one up, or empty (TSSR-41).
     *
     * ⚠️ <strong>Keys, never summaries.</strong> Links cross project boundaries and issues do not, so a
     * blocker may sit in a project the reader cannot open. A key is enough to ask a colleague about; a
     * summary would be somebody else's backlog read out to a stranger.
     *
     * ⚠️ It is why {@code availableTransitions} is short, so the two travel together — a screen that
     * showed a missing button with no explanation would be worse than one that never hid it.
     */
    List<String> blockedBy,
    List<TransitionOption> availableTransitions,
    LocalDateTime createdAt,
    /** When it entered a Done status; null while open (ADR-0011). */
    LocalDateTime resolvedAt,
    /** When somebody put it away, or null while it is still in view (TSSR-4). */
    LocalDateTime archivedAt,
    LocalDateTime updatedAt
) {
}
