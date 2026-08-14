package net.innoventa.tessera.security.access.axis;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.security.access.AccessAxis;
import net.innoventa.tessera.security.access.AccessReason;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.AccessDecision;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.Subject;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Axis 2 — may <em>this person</em> do <em>this</em>, <em>in this project</em>?
 *
 * <p>The last axis, and the one that absorbed both mechanisms Tessera used to have beside its
 * permissions:
 *
 * <ul>
 *   <li><strong>Membership.</strong> Not a check somebody has to remember to write. No assignment
 *       covering a project means no permissions there, so <em>there is no such project</em> is what this
 *       axis says by default — {@link AccessReason#NOT_FOUND_OR_HIDDEN}, which is ADR-0002's isolation
 *       rule arriving as a property of the model rather than as a call every author has to remember.
 *   <li><strong>Ownership.</strong> Not a comparison of two identifiers. A subject holding a permission
 *       at {@link net.innoventa.tessera.security.access.TesseraScope#SELF} holds it over the rows it
 *       owns, wherever they live, and the covering chain is what decides that rather than an
 *       {@code equals} somebody wrote in a service.
 * </ul>
 *
 * <p>⚠️ <strong>A blank permission allows.</strong> A route carrying a bare {@code @RequiresAccess} is
 * saying <em>a signed-in caller and nothing more</em>, and axis 1 has already answered that. Refusing
 * here would make the bare form unusable and push every such route back out of the engine.
 *
 * <p>⚠️ <strong>What this axis never sees is what deny-wins is worth.</strong> The subtraction runs
 * inside {@link EffectivePermissionsResolver}, at every level, before the set reaches here — which is
 * why a personal DENY beats the role that grants it without this class knowing either exists. Tessera's
 * old resolver applied overrides in insertion order, so a late ALLOW quietly won; the same two rows now
 * refuse.
 */
@Component
@RequiredArgsConstructor
public class PermissionAxis implements AccessAxisEvaluator {

    private final ObjectProvider<EffectivePermissionsResolver> effectivePermissions;

    @Override
    public AccessAxis axis() {
        return AccessAxis.PERMISSION;
    }

    @Override
    public AccessDecision evaluate(Subject subject, String permission, AccessTarget target) {
        if (permission == null || permission.isBlank()) {
            return AccessDecision.allowed();
        }

        EffectivePermissions effective = effectivePermissions.getObject().resolve(subject, target);

        if (effective.contains(permission)) {
            return AccessDecision.allowed();
        }

        return outsideTheProject(effective, target)
                ? AccessDecision.refused(AccessReason.NOT_FOUND_OR_HIDDEN, "Project not found.")
                : AccessDecision.refused(
                        AccessReason.NO_PERMISSION,
                        "You do not have permission to do this (" + permission + "). Somebody who "
                        + "administers this project has to give it to you.");
    }

    /**
     * Whether the refusal is about the place rather than about the permission.
     *
     * <p>Nothing reaching the subject at this project means they are outside it; something reaching them
     * and this permission missing means they are inside it without this power. The two answer with
     * different status codes and — deliberately — with very different amounts of information:
     *
     * <p>⚠️ <strong>Outside a project reads as "no such project", and the sentence has to be that
     * short.</strong> This is {@code ProjectPermissionService.requireVisible}'s rule kept whole
     * (ADR-0002): a person who is not a member must not learn a project exists, which a 403 explaining
     * why would tell them outright. Inside it, being short would be unkind for no benefit — they already
     * know the project is there, so the refusal names the permission and who to ask.
     */
    private boolean outsideTheProject(EffectivePermissions effective, AccessTarget target) {
        return Targets.hasProject(target)
               && !effective.reaches(Targets.projectScope(Targets.projectIdOf(target)));
    }
}
