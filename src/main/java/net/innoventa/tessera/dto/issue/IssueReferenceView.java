package net.innoventa.tessera.dto.issue;

/**
 * What a `TES-42` written in prose turns into.
 *
 * <p>A reference is read inside a sentence, so this carries what a badge draws and what makes the
 * sentence make sense — which issue, what it is about, what state it is in — and nothing that would
 * need a second glance to interpret. There is still no project here: the key already names it to
 * anybody who works here.
 *
 * <h2>⚠️ Two identifiers, and they are asked for interchangeably</h2>
 *
 * <p>A document mixes the form somebody typed with the form a picker inserted, so a batch is asked in
 * whichever of the two each mention happens to carry — and the answer carries <strong>both</strong>, so
 * the client can file it under either and find it again with the token it sent.
 *
 * <p>⚠️ <strong>{@link #hash} is a public value, and the earlier note here saying this record holds no
 * identifier was about a different thing.</strong> That warning was about an internal row id, which
 * somebody would eventually pass to an endpoint meaning something else by it. This one is the opposite:
 * it exists precisely to be written down, and a reference that carries it survives the key being
 * re-minted. It is what a durable link is built out of.
 *
 * @param issueKey       the key as it stands right now — ⚠️ what a renderer prints, never the text the
 *                       document happened to be written with
 * @param hash           the permanent identifier, for storing a reference that outlives the key
 * @param summary        what the issue is about
 * @param status         the status name
 * @param statusColor    its own colour, or null to be drawn from the category
 * @param statusCategory the category, which is what colours a status that has no colour of its own
 * @param typeName       the issue type, for the badge's tooltip
 * @param typeIconKey    the icon key that type is drawn with; null draws the generic one
 * @param open           the invariant, never a status name — an issue is open exactly while it has no
 *                       resolution
 */
public record IssueReferenceView(
    String issueKey,
    String hash,
    String summary,
    String status,
    String statusColor,
    String statusCategory,
    String typeName,
    String typeIconKey,
    boolean open
) {
}
