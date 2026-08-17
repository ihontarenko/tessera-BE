package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.jmouse.access.Subject;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.InvocationScope;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.access.CallerSubjects;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.springframework.stereotype.Component;

/**
 * What an action costs, asked of the authorization model Tessera has.
 *
 * <p><strong>This is the case {@code ToolAuthorizer} exists as a seam for</strong>, and the answer has
 * moved underneath it twice without the seam noticing. It used to read Tessera's own tables; it then read
 * the engine, over the same grants every route resolves from; and it now reads the engine about the right
 * <em>subject</em>, which is the part that was wrong.
 *
 * <h2>⚠️ What was wrong, and what it cost</h2>
 *
 * <p>It resolved {@code caller.callerId()} into a member and asked about that member — throwing away
 * {@code actsOnBehalfOf} entirely. For a person at the assistant the two are equal and nothing showed.
 * For an agent restricted to less than its owner holds, the ceiling would have been silently ignored:
 * somebody narrows an agent, the screen says restricted, and every tool call resolves the owner's full
 * set anyway. A restriction with no effect is worse than no restriction, because somebody is relying on
 * it.
 *
 * <p>{@link CallerSubjects} is the shared translation the access bridge already uses, so the two cannot
 * disagree. A caller acting for itself becomes an ordinary subject; one acting for somebody becomes the
 * engine's service sub-account, capped in every scope.
 *
 * <h2>Asked twice, and the two questions differ</h2>
 *
 * <ul>
 *   <li>{@link #permits} runs before any project is resolved and asks whether the caller holds the
 *       permission <em>in any project at all</em>. A caller holding it nowhere is refused without
 *       learning which projects exist, which is the entire reason the dispatcher checks permission
 *       before scope.
 *   <li>{@link #permitsInScope} runs at the project the call resolved to. This is where Tessera's model
 *       actually lives: a person may transition issues in one project and not in the next, and a DENY
 *       override on them personally beats the role that grants it.
 * </ul>
 *
 * <h2>⚠️ The first question is about breadth, not about places</h2>
 *
 * <p>{@link #permits} used to iterate every project the caller could reach and ask about each. That
 * costs a query per project, and — worse — it answered a narrower question than it appeared to: a
 * caller holding the permission <em>installation-wide</em> reaches no project by name, because a
 * visibility scope says {@code EVERYTHING} rather than listing them. The stream was then empty and
 * every action was refused, naming a permission the caller genuinely held. It now asks
 * {@code ProjectAccess.holdsAnywhere}, which reads the breadth in one query — the same
 * {@code seesNothing()} shape {@code AccessToolAuthorizer} in {@code jmouse-ai-access} uses, adopted
 * here without waiting for the {@code ScopeCatalog} this product has still not published.
 *
 * <p>⚠️ <strong>A scope-free yes is not a yes.</strong> It only says the caller is not a stranger to
 * this permission; {@link #permitsInScope} is what decides the project the call actually names, and a
 * DENY override there still wins. An action that is not scope-confined — {@code projects_create}, which
 * has no project to be confined to — is gated by this question alone, so the permission it declares has
 * to be one that means something installation-wide.
 */
@Component
@RequiredArgsConstructor
public class TesseraToolAuthorizer implements ToolAuthorizer {

    private final ProjectAccess projectAccess;

    @Override
    public boolean permits(CallerIdentity caller, ToolAction action) {
        // Anywhere at all — installation-wide, at some project, or over the caller's own rows. Asking
        // for the places instead would miss the first of those; see ProjectAccess.holdsAnywhere.
        return projectAccess.holdsAnywhere(
                CallerSubjects.of(caller), action.requiredPermission());
    }

    @Override
    public boolean permitsInScope(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        return projectAccess.holds(
                CallerSubjects.of(caller), scope.id(), action.requiredPermission());
    }

    /**
     * Why the permission is missing, for the one cause that reads as a broken installation and is not.
     *
     * <p>⚠️ <strong>On a freshly created database every tool refuses at once, and the refusal names a
     * permission.</strong> That sentence sent whoever read it — a person, or a model reporting to
     * one — to debug the token, the agent's authority flag and the caller resolver, all of which were
     * correct. The cause is structural: {@code MemberProvisioner} makes the first member a global access
     * administrator, and {@code policy/tessera.jmp} deliberately gives that role no project permission at
     * all, because every one of them is carried by the three {@code @PROJECT} roles and those are handed
     * out by {@code ProjectService.create}. Belonging to no project is therefore holding nothing, and no
     * amount of granting fixes it — only a project does.
     *
     * <p>Two cases are deliberately left to the plain refusal, because for them this diagnosis would be
     * false:
     *
     * <ul>
     *   <li>{@link Permissions#CREATE_PROJECT} — the way out of the state cannot also be the thing the
     *       state is blocking, and this one is installation-wide precisely so that it never is. A caller
     *       refused <em>here</em> has a genuinely misconfigured role.
     *   <li>Anybody who browses a project somewhere. They belong to something, so their missing
     *       permission is an ordinary missing permission and pointing them at project creation would be
     *       nonsense. This also covers every scope-confined refusal for free — reaching a project by
     *       name means browsing it.
     * </ul>
     */
    @Override
    public String refusalAdvice(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        if (Permissions.CREATE_PROJECT.equals(action.requiredPermission())) {
            return null;
        }

        if (projectAccess.holdsAnywhere(CallerSubjects.of(caller), Permissions.BROWSE_PROJECT)) {
            return null;
        }

        return "This caller belongs to no project, and in Tessera every project permission is carried by "
               + "belonging to one — so nothing was granted wrongly, there is simply nowhere the grant "
               + "could have come from. Create the first project with 'projects_create', which needs no "
               + "project to exist, or ask somebody who administers an existing one to add this caller "
               + "to it.";
    }
}
