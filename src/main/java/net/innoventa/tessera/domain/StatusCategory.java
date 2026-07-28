package net.innoventa.tessera.domain;

/**
 * One of exactly three fixed buckets every {@link Status} belongs to — the one genuinely closed set
 * in the configuration model (CONTEXT.md, "StatusCategory"). Modeled as an enum, not a seeded table,
 * precisely because it is closed: the whole workflow/resolution model (ADR-0004, ADR-0005) branches
 * on {@link #DONE}, and a fourth category would be a design error, not data. Drives board columns and
 * "what counts as done" reporting.
 */
public enum StatusCategory {
    TODO,
    IN_PROGRESS,
    DONE
}
