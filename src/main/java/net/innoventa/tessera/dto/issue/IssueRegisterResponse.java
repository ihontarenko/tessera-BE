package net.innoventa.tessera.dto.issue;

import java.util.List;

/**
 * A page of <em>registers</em> — issues that gather other issues, with what each one gathers (TSSR-45).
 *
 * <p>This is the read behind the Tracked tab, and it is the answer to a question the product could not
 * answer before: <em>which efforts are being tracked at all</em>. A tracking issue was only ever visible
 * from itself, so a register nobody remembered the key of was a register nobody could find.
 *
 * <p>⚠️ <strong>Nothing here names a tracking link.</strong> An entry is a link of whichever type the
 * caller asked about, and {@code Tracks} is a row somebody may rename or delete. The interface defaults
 * its picker to that row when the installation has one; this shape does not know it exists.
 */
public record IssueRegisterResponse(
    List<Item> items,
    int page,
    int size,
    long total
) {

    /**
     * One register: the issue that gathers, where it lives, and what hangs off it.
     *
     * @param done how many entries are finished — {@code resolution IS NULL} inverted (ADR-0004), counted
     *             over the entries below rather than stored. Derived on read like every other number in
     *             this product's reporting (ADR-0013): it will be right, and it will not be fast on a hub
     *             with two hundred entries — which is the signal that the effort wanted a container of its
     *             own after all.
     */
    public record Item(
        IssueSearchResponse.ProjectRef project,
        IssueRowResponse issue,
        List<Entry> entries,
        int done
    ) {
    }

    /**
     * One gathered issue, as much of it as the reader may see.
     *
     * <p>⚠️ <strong>{@code projectKey} travels even for an entry the reader may not open.</strong> It is
     * not a second disclosure: an issue key already carries its project's key, so withholding the badge
     * while printing {@code INVT-21} beside it would hide nothing and cost the reader the one column that
     * says where the work sits. The project's <em>name</em> is its own text and does not travel — the same
     * line {@link IssueReference#redacted} draws between addressing and anybody's words.
     *
     * @param label  how the relationship reads <em>from the heading issue</em> — the outward label when the
     *               heading gathers, the inward one when it is gathered. ⚠️ Not always the outward one: the
     *               screen asks for one side at a time, and a group of "tracked by" rows labelled "tracks"
     *               would state the relationship backwards.
     * @param linkId what {@code DELETE /api/issues/{id}/links/{linkId}} and the retype control address.
     *               ⚠️ Only the register's own end is actionable; a redacted entry still carries it,
     *               because unlinking is a write on <em>this</em> issue in a project the caller edits — and a
     *               link is reachable from either end, so an inward one is removable here too.
     */
    public record Entry(
        String linkId,
        String linkTypeId,
        String linkTypeName,
        String label,
        String projectKey,
        IssueReference issue
    ) {
    }

}
