package net.innoventa.tessera.security.access.target;

/**
 * An issue addressed by its key rather than by its identifier — a resource type with no rows.
 *
 * <p>{@code GET /api/issues/by-key/{issueKey}} exists because {@code TES-42} is what an issue page's
 * URL carries and what a person pastes into a chat. The engine resolves a target from an identifier and
 * the feature that owns the resource, so the route needs <em>something</em> to declare, and declaring
 * {@code Issue.class} would hand {@code TES-42} to a resolver looking it up as a primary key: no row,
 * and a refusal reading <em>not found</em> for an issue that is right there.
 *
 * <p>So this names the <strong>way in</strong> rather than the thing. It is deliberately not an entity,
 * has no table and is never instantiated — it is a token in the declaration, and
 * {@link IssueByKeyAccessTargetResolver} is what it means.
 *
 * <p>⚠️ One more route addressing issues by key is one more {@code @RequiresAccess} naming this. Two
 * <em>kinds</em> of key would be a second class, not a parameter on this one — a resource type whose
 * meaning depended on an argument would be a resolver nobody could read.
 */
public final class IssueByKey {

    private IssueByKey() {
    }
}
