package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
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
 * <p>⚠️ <strong>The first question costs a query per project the caller can reach.</strong> That is
 * honest for a tracker where a person belongs to a handful, and it is the price of a scope-free gate over
 * a model that has no scope-free answer. {@code AccessToolAuthorizer} in {@code jmouse-ai-access} answers
 * it in one query instead, through {@code visibilityFor(...).seesNothing()} — adopting it wholesale is
 * the right end state and needs a {@code ScopeCatalog} this product has not published yet.
 */
@Component
@RequiredArgsConstructor
public class TesseraToolAuthorizer implements ToolAuthorizer {

    private final ProjectAccess projectAccess;

    @Override
    public boolean permits(CallerIdentity caller, ToolAction action) {
        Subject subject = CallerSubjects.of(caller);

        // Every project the caller can reach, from the grants — there is no membership table to ask.
        return projectAccess.visibleProjectIds(subject).stream()
                .anyMatch(projectId ->
                        projectAccess.holds(subject, projectId, action.requiredPermission()));
    }

    @Override
    public boolean permitsInScope(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        return projectAccess.holds(
                CallerSubjects.of(caller), scope.id(), action.requiredPermission());
    }
}
