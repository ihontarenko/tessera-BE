package net.innoventa.tessera.dto.issue;

/**
 * Which side of a stored {@link net.innoventa.tessera.domain.IssueLink} the viewing issue is on
 * (ticket 12). {@link #OUTWARD} — the viewing issue is the source, so it reads the outward label
 * ("blocks"); {@link #INWARD} — it is the target, reading the inward label ("is blocked by"). The link
 * is one row; direction is computed per viewer, never duplicated.
 */
public enum LinkDirection {
    OUTWARD,
    INWARD
}
