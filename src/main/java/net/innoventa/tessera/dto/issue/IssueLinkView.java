package net.innoventa.tessera.dto.issue;

/**
 * A link as seen from one issue (ticket 12): the direction the viewer sits in, the label to show for
 * that direction, and the issue on the other end. Both sides of a single stored row render from this
 * shape — no duplicate reverse record.
 */
public record IssueLinkView(
    String id,
    String linkTypeId,
    String linkTypeName,
    LinkDirection direction,
    String label,
    IssueRef issue
) {
}
