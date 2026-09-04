package net.innoventa.tessera.security.access.target;

import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.security.access.TesseraScope;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.jpa.ProjectedAccessTargetResolver;
import org.jmouse.access.spi.ScopeHierarchy;
import org.springframework.stereotype.Component;

/**
 * Where an {@link Issue} lives and who owns it.
 *
 * <p><strong>The one resolver Tessera actually needs</strong>, and the reason is worth stating: almost
 * every route in this product spells its project into the URL, so the binder reads the place off the
 * path and no lookup happens at all. The routes under {@code /api/issues/{issueId}} are the exception —
 * an issue is addressed on its own because that is what an issue page's URL is — and this is what turns
 * that identifier into a project.
 *
 * <p><strong>The owner is the reporter.</strong> Not the assignee: an assignment is a statement about
 * who is doing the work and changes hands, and a permission held at
 * {@link TesseraScope#SELF} has to mean something stable. "The issues you raised" is that; "the issues
 * currently pointed at you" is a filter.
 *
 * <p>⚠️ A projection rather than a load — two columns, no aggregate. This runs on the security path of
 * every issue route, which is the one path where "it is only one more query" is charged per request
 * rather than per screen.
 */
@Component
public class IssueAccessTargetResolver extends ProjectedAccessTargetResolver<Issue> {

    public IssueAccessTargetResolver(ScopeHierarchy scopeHierarchy) {
        super(scopeHierarchy);
    }

    /**
     * ⚠️ The word a policy writes for this type, declared HERE rather than on the class.
     * A library type cannot carry the annotation — `jmouse-files` has no dependency on
     * `jmouse-access` and must not grow one — and declaring every resolver's name the same way keeps
     * one rule instead of two. Same words as Innoventa's and Kiwi's: one type, one spelling.
     */
    @Override
    public String resourceName() {
        return "issue";
    }

    @Override
    public Class<Issue> resourceType() {
        return Issue.class;
    }

    @Override
    protected String ownerPath() {
        return "reporterMemberId";
    }

    @Override
    protected String placePath() {
        return "projectId";
    }

    @Override
    protected ScopeKind placeOf() {
        return TesseraScope.PROJECT;
    }
}
