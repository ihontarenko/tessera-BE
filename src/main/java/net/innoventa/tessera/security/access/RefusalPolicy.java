package net.innoventa.tessera.security.access;

import net.innoventa.tessera.exception.AccessDeniedException;
import org.jmouse.access.enforcement.AccessRefusal;
import org.jmouse.access.enforcement.RefusalHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * What Tessera does about a refused call — the engine's {@link RefusalHandler}.
 *
 * <p>Throwing is the whole answer, and there is deliberately no shadow mode. Innoventa needed one
 * because it switched scoped default-deny on over routes that had never been scoped, so a wrong
 * {@code @RequiresAccess} would have started refusing everybody the day the flag went on. Tessera is
 * not in that position: every route it has was already gated per project by
 * {@code ProjectPermissionService}, and the parallel run is what proves the engine agrees before a
 * single endpoint changes. A flag that let a refusal through would only be a way to ship a hole.
 *
 * <p>⚠️ <strong>Named for what it decides, not for what it reads.</strong> It holds no grants and
 * answers no question about them — it decides what happens <em>after</em> the engine has already
 * refused. The grants are rows, and the document that seeds them is the thing called a policy here.
 */
@Component
public class RefusalPolicy implements RefusalHandler {

    @Override
    public void onRefusal(AccessRefusal refusal, Method method) {
        throw AccessDeniedException.of(refusal.decision());
    }
}
