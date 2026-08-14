package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.Roles;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessAdministration.Effect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Every change {@code project_memberships} and {@code project_permission_overrides} make, made in the
 * engine's tables too.
 *
 * <p>⚠️ <strong>Transitional, deliberately, and it is deleted by V000014.</strong> The two models are
 * standing side by side while {@link ParallelAuthorizationCheck} proves they agree, and a parallel run
 * only means something if both halves stay current — a check that compared a live model against a frozen
 * one would report drift it caused itself. So administration keeps writing the old rows, because the
 * people screen still reads them, and this writes the same fact where authorization is actually decided
 * from.
 *
 * <p><strong>The engine is the one that matters.</strong> This is not a fallback and the old rows are
 * not a second opinion: {@code @RequiresAccess} resolves from {@code access_*} and nothing else. What the
 * old tables buy for one release is a screen that did not have to be rewritten in the same change as the
 * authorization underneath it.
 *
 * <p>⚠️ <strong>A failure here is logged, never thrown.</strong> Reversed, it would be right — a grant
 * the engine did not receive is a person who cannot do what a screen just told them they can. It is not
 * reversed because the whole point of the parallel window is that the disagreement is <em>visible</em>:
 * the check reports it at the next start, by name, which is more useful than a 500 on the membership
 * screen that nobody can explain.
 */
@Component
@RequiredArgsConstructor
public class LocalAuthorizationMirror {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalAuthorizationMirror.class);

    /** Told apart from a seeded assignment and from one the handover carried over. */
    private static final String SOURCE = "DIRECT";

    private final AccessAdministration access;

    /**
     * @param roleDisplayName what {@code project_roles} calls it — {@code Administrator}, and so on
     */
    public void assignRole(String memberId, String projectId, String roleDisplayName, String byMemberId) {
        String roleName = Roles.ofDisplayName(roleDisplayName);

        if (roleName == null) {
            LOGGER.error("A membership names the project role '{}', which is none of the three the "
                         + "policy document declares, so it is NOT mirrored into the engine. That "
                         + "member holds nothing in project {}.", roleDisplayName, projectId);
            return;
        }

        access.assign(memberId, roleName, Targets.projectScope(projectId), SOURCE, byMemberId, null);
    }

    /**
     * Takes back every role this member holds here — what leaving a project means.
     *
     * <p>{@code unassignAllAt} rather than a role-by-role loop, and the port offers it for a reason: a
     * caller that had to name the role would have to read it first, and would then be one race away from
     * revoking a membership while leaving its authority behind.
     */
    public void unassignAllRoles(String memberId, String projectId) {
        access.unassignAllAt(memberId, Targets.projectScope(projectId));
    }

    /** One permission handed to, or taken from, this member personally in this project. */
    public void setOverride(
            String memberId, String projectId, String permission, boolean allowed, String byMemberId) {

        access.grant(
                memberId,
                permission,
                Targets.projectScope(projectId),
                allowed ? Effect.ALLOW : Effect.DENY,
                "Set on the project's people screen",
                byMemberId,
                null);
    }

    public void clearOverride(String memberId, String projectId, String permission) {
        access.ungrant(memberId, permission, Targets.projectScope(projectId));
    }

    /**
     * Everything this member personally holds here, cleared — for somebody being removed from a project.
     *
     * <p>⚠️ <strong>A library table cannot foreign-key into a product's rows</strong>, so nothing
     * cascades: deleting the membership row leaves the grants standing unless something takes them back
     * by hand. That gap is silent, which is why it is a method rather than a line somebody remembers.
     */
    public void clearEverythingFor(String memberId, String projectId) {
        unassignAllRoles(memberId, projectId);

        Permissions.all().forEach(
                permission -> access.ungrant(memberId, permission, Targets.projectScope(projectId)));
    }
}
