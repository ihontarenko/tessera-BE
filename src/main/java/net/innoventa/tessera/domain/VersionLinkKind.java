package net.innoventa.tessera.domain;

/**
 * Which of an issue's two distinct version associations an {@link IssueVersion} row represents
 * (ticket 11): {@link #AFFECTS} — a version the issue is present in — versus {@link #FIX} — a version
 * that will resolve it. One table discriminated by this kind, rather than two near-identical tables.
 */
public enum VersionLinkKind {
    AFFECTS,
    FIX
}
