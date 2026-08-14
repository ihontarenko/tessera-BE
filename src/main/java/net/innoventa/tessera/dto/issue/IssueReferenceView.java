package net.innoventa.tessera.dto.issue;

/**
 * What a `TES-42` written in prose turns into.
 *
 * <p>Deliberately four fields. A reference is read inside a sentence, so it carries what makes the
 * sentence make sense — which issue, what it is about, and whether it is still open — and nothing that
 * would need a second glance to interpret.
 *
 * <p>⚠️ <strong>No project, and no identifier.</strong> The key already names the project to anybody
 * who works here, and an internal identifier in a payload built for prose is a value somebody will
 * eventually pass to an endpoint that means something different by it.
 */
public record IssueReferenceView(
    String issueKey,
    String summary,
    String status,
    boolean open
) {
}
