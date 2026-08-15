package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.exception.AccessDeniedException;
import net.innoventa.tessera.security.Permissions;
import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.Subject;
import org.jmouse.access.VisibilityScope;
import org.jmouse.access.VisibilityScopeResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * What the engine says, for the code that has to <em>ask</em> rather than be gated.
 *
 * <p><strong>Almost nothing needs this, and that is the point.</strong> A route declares what it needs
 * with {@code @RequiresAccess} and the engine refuses before the handler runs, so a service asking for
 * itself is the exception. Three kinds of caller are the exception on purpose:
 *
 * <ul>
 *   <li><strong>An interface that has to stop offering what it is about to refuse.</strong> The project
 *       payload carries the caller's permissions so the board can hide a button. ⚠️ That answer is never
 *       the authority — every one of those permissions is still checked server-side.
 *   <li><strong>A rule that is about more than the route.</strong> "Delete your own comment, or anybody's
 *       if you administer the project" is one handler with two answers, and the second is a question the
 *       annotation cannot phrase.
 *   <li><strong>A listing across projects.</strong> Search spans everything the caller can see, which is
 *       a property of the reader rather than of any one row — {@link #visibleProjectIds}.
 * </ul>
 *
 * <p>⚠️ <strong>It replaced {@code ProjectPermissionService}, and the resolution is not the same.</strong>
 * That class applied overrides in insertion order, so an ALLOW written after a DENY quietly won. The
 * engine's rule is <em>deny wins, the subtraction runs last, at every level</em>, and it runs inside
 * {@link EffectivePermissionsResolver} where nothing here can get it wrong.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccess {

    private final EffectivePermissionsResolver effectivePermissions;
    private final VisibilityScopeResolver      visibilityScopes;

    /** Every permission this member effectively holds in this project. */
    public Set<String> permissionsIn(Member member, String projectId) {
        return resolve(subjectOf(member), projectId).names();
    }

    public boolean holds(Member member, String projectId, String permission) {
        return holds(subjectOf(member), projectId, permission);
    }

    /**
     * The same question about a subject that is not simply a member.
     *
     * <p>⚠️ <strong>This overload exists for agents, and taking a {@code Member} instead is the bug it
     * prevents.</strong> An agent restricted to its own grants is a <em>service sub-account</em> to the
     * engine — its set is capped by its owner's in every scope — and that fact lives on the
     * {@link Subject}, nowhere else. Handing this the owner's member row would resolve the owner's
     * permissions and quietly ignore the ceiling, which is a restriction somebody set having no effect.
     */
    public boolean holds(Subject subject, String projectId, String permission) {
        return resolve(subject, projectId).contains(permission);
    }

    /**
     * The same question, refused rather than answered.
     *
     * <p>⚠️ <strong>For the handful of routes whose permission depends on the request body, and for
     * nothing else.</strong> A drag onto a sprint costs {@code MANAGE_SPRINT} and the same drag within
     * the backlog costs {@code EDIT_ISSUE}; assigning an issue while creating it costs
     * {@code ASSIGN_ISSUE} on top of {@code CREATE_ISSUE}. An annotation can only name one permission, so
     * these branches stay where the destination is known — behind a route that has already refused
     * anybody with no business in the project.
     *
     * <p>It refuses with the same exception and the same words the engine's own axis produces, so a
     * client cannot tell which of the two answered. That is the point: two sentences for one idea is how
     * a reader concludes the product is broken.
     */
    public void require(Member member, String projectId, String permission) {
        if (holds(member, projectId, permission)) {
            return;
        }

        throw new AccessDeniedException(
                AccessReason.NO_PERMISSION,
                "You do not have permission to do this (" + permission + "). Somebody who administers "
                + "this project has to give it to you.");
    }

    /**
     * Whether anything at all reaches this member in this project.
     *
     * <p><strong>Membership is not a check any more, it is the empty set.</strong> No assignment covering
     * a project means no permissions there, so this asks whether the covering chain reaches the project
     * rather than looking a row up in a membership table — which is what lets a personal grant make
     * somebody effectively a member without one.
     */
    public boolean isMember(Member member, String projectId) {
        return resolve(subjectOf(member), projectId).reaches(Targets.projectScope(projectId));
    }

    /**
     * Every project this member may browse.
     *
     * <p>Resolved from the grants rather than from a membership table, and memoised per request by
     * {@link AccessContext} — so a search that asks once and a search that asks per row cost the same,
     * and a test can see the difference.
     *
     * <p>⚠️ <strong>An installation-wide grant reads as "every project".</strong> The visibility scope
     * says so with {@code EVERYTHING} rather than by listing them, because a filter has no row to ask
     * upward from — so a caller holding {@code BROWSE_PROJECT} at {@code @GLOBAL} gets an empty list here
     * and the caller has to mean <em>no restriction</em> by it. {@link #browsesEveryProject} is what says
     * which of the two an empty answer is.
     */
    public List<String> visibleProjectIds(Member member) {
        return visibleProjectIds(subjectOf(member));
    }

    /** The same, for a subject that may be an agent — see {@link #holds(Subject, String, String)}. */
    public List<String> visibleProjectIds(Subject subject) {
        VisibilityScope visible = visibilityScopes.of(subject, Permissions.BROWSE_PROJECT);

        return visible.places().stream()
                .filter(place -> TesseraScope.PROJECT.equals(place.type()))
                .map(ScopeReference::id)
                .toList();
    }

    /** Whether this member browses every project there is, which no list of identifiers can express. */
    public boolean browsesEveryProject(Member member) {
        return visibilityScopes.of(subjectOf(member), Permissions.BROWSE_PROJECT).breadth()
               == VisibilityScope.Breadth.EVERYTHING;
    }

    private EffectivePermissions resolve(Subject subject, String projectId) {
        return effectivePermissions.resolve(subject, Targets.project(projectId));
    }

    private static Subject subjectOf(Member member) {
        return CurrentMemberSubject.of(member);
    }
}
