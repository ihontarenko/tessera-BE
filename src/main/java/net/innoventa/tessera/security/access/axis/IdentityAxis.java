package net.innoventa.tessera.security.access.axis;

import net.innoventa.tessera.security.access.AccessAxis;
import net.innoventa.tessera.security.access.AccessReason;
import org.jmouse.access.AccessDecision;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.Subject;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.springframework.stereotype.Component;

/**
 * Axis 1 — who is asking.
 *
 * <p>It refuses exactly one thing: nobody. That is the whole of it, and the smallness is the point —
 * an axis that also decided whether the caller were an administrator would be answering the next axis's
 * question with none of the next axis's information about <em>where</em>.
 *
 * <p><strong>It runs first so that a refusal names the outermost reason.</strong> Somebody who is not
 * signed in has to read <em>sign in</em>; told <em>you do not have permission in this project</em> they
 * would go and ask a project administrator for something no project administrator can give them.
 *
 * <p>⚠️ <strong>Tessera has no share tokens and no impersonation</strong>, so there is nothing here
 * about either. Innoventa's identity axis lets both through because both are identities; adding the
 * branches here before the features exist would be code nothing can reach and nobody can test.
 */
@Component
public class IdentityAxis implements AccessAxisEvaluator {

    @Override
    public AccessAxis axis() {
        return AccessAxis.IDENTITY;
    }

    @Override
    public AccessDecision evaluate(Subject subject, String permission, AccessTarget target) {
        if (subject.isAuthenticated()) {
            return AccessDecision.allowed();
        }

        return AccessDecision.refused(
                AccessReason.UNAUTHENTICATED,
                "You need to be signed in to do this.");
    }
}
