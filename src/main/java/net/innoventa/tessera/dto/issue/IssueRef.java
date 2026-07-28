package net.innoventa.tessera.dto.issue;

/**
 * A lightweight reference to another issue — parent, child, or the far side of a link. Enough to
 * render a clickable chip (key + summary + type + status) without loading the full issue.
 */
public record IssueRef(
    String id,
    String issueKey,
    String summary,
    IssueTypeSummary type,
    StatusSummary status,
    boolean open
) {
}
