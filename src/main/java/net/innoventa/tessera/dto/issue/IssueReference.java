package net.innoventa.tessera.dto.issue;

/**
 * A lightweight reference to another issue — parent, child, or the far side of a link. Enough to
 * render a clickable chip (key + summary + type + status) without loading the full issue.
 *
 * <p>⚠️ <strong>The far side of a link may be somewhere the reader cannot go</strong> (TSSR-43). Links
 * cross project boundaries and issues do not, so a tracking hub can name work in a project the reader
 * is not a member of. Such a reference is <em>redacted</em> rather than dropped, and {@link #readable}
 * says which it is.
 */
public record IssueReference(
    String id,
    String issueKey,
    /** ⚠️ Null exactly when {@link #readable} is false — somebody else's words, withheld. */
    String summary,
    IssueTypeSummary type,
    StatusSummary status,
    boolean open,
    /**
     * Whether the reader may open this issue.
     *
     * <p>⚠️ <strong>A flag rather than a null summary standing in for one.</strong> "No summary" and
     * "a summary you may not see" would render identically and mean opposite things, and a client
     * inferring the difference from a null is a client that will get it wrong once.
     */
    boolean readable
) {

    /**
     * The key and the state, and nothing anybody wrote.
     *
     * <p>⚠️ <strong>The key travels; the summary does not.</strong> The same line TSSR-41 drew for
     * blockers: enough to ask a colleague about, not enough to read their backlog. The status goes with
     * it because a register that could not say how much of an effort is done would not be a register —
     * and a status name is installation-wide configuration, not anybody's private text.
     *
     * <p>⚠️ <strong>And it is redacted, never omitted.</strong> An effort whose list silently drops four
     * of its twenty items is a list that lies, with nothing to tell the reader it did. "Something you
     * cannot see" is information; a shorter list is misinformation.
     */
    public static IssueReference redacted(IssueReference full) {
        return new IssueReference(
            // No id either: it is what every link-through in the interface is built from, and a
            // reference somebody may not open should not carry the means to try.
            null,
            full.issueKey(),
            null,
            full.type(),
            full.status(),
            full.open(),
            false);
    }

}
