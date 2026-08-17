package net.innoventa.tessera.dto.block;

/**
 * How a directive resolved.
 *
 * <p>⚠️ <strong>None of these is an error, and none of them renders as nothing.</strong> A document
 * outlives its subjects — an issue is deleted, a sprint is renamed, a reader loses access to a project —
 * so every one of these is ordinary and every one of them shows a notice. A block that quietly
 * disappeared would read as "nothing to report", which is a lie about a document whose whole promise is
 * that its numbers are live.
 */
public enum BlockStatus {

    RESOLVED,

    /**
     * Nothing matched the argument.
     *
     * <p>⚠️ <strong>Also what a caller gets for something they may not see</strong>, and the two are
     * indistinguishable on purpose. Separating them would let anybody enumerate the tracker by writing
     * directives into a page and reading which came back — the same disclosure rule
     * {@code IssueReferenceService} states for inline mentions, and the same one ADR-0002 buys
     * everywhere else.
     */
    NOT_FOUND,

    /** Nothing is registered for this directive name — a block from a product this one is not. */
    UNKNOWN_DIRECTIVE,

    /**
     * The directive was asked for, but its line does not appear in the page it was asked against.
     *
     * <p>⚠️ <strong>This status is the security boundary, not a diagnostic.</strong> Without the check
     * behind it, the resolve endpoint is a way to read any issue in any project by asking for it,
     * dressed as rendering a page — see {@code DirectiveMatcher}.
     */
    NOT_ON_THIS_PAGE

}
