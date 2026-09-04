package net.innoventa.tessera.security.access.target;

/**
 * A project addressed by its key rather than by its identifier — a resource type with no rows.
 *
 * <p>{@code GET /api/projects/by-key/{projectKey}} exists because {@code TSSR} is what a project's URL
 * carries and what somebody says out loud. The {@code PROJECT} scope resolves its instance from a
 * request parameter literally named {@code projectId}, so a route whose path variable is a key has
 * nothing for it to read — and naming the entity instead would hand {@code TSSR} to a resolver looking
 * it up as a primary key: no row, and a refusal reading <em>not found</em> for a project that is right
 * there.
 *
 * <p>So this names the <strong>way in</strong> rather than the thing. It is deliberately not an entity,
 * has no table and is never instantiated — it is a token in the declaration, and
 * {@link ProjectByKeyAccessTargetResolver} is what it means. The same shape, and the same reasoning, as
 * {@link IssueByKey}.
 */
public final class ProjectByKey {

    private ProjectByKey() {
    }
}
