package net.innoventa.tessera.domain;

/**
 * A {@link Version}'s lifecycle stage. A single {@code state}, deliberately not a pair of
 * {@code released}/{@code archived} booleans (ticket 06; "prefer state over flag") — the states are
 * mutually exclusive and ordered, which a boolean pair cannot express without an illegal combination.
 */
public enum VersionState {
    UNRELEASED,
    RELEASED,
    ARCHIVED
}
