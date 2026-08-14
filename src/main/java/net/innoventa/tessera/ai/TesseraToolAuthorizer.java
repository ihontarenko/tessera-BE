package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.InvocationScope;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.springframework.stereotype.Component;

/**
 * What an action costs, asked of the authorization model Tessera has.
 *
 * <p><strong>This is the case {@code ToolAuthorizer} exists as a seam for</strong>, and the answer has
 * moved underneath it without the seam noticing — which is the interesting part. It used to read
 * Tessera's own tables through {@code ProjectPermissionService}; it now reads the engine, over the same
 * grants every route resolves from. The library asked one question twice both times and neither the
 * interface nor the dispatcher changed.
 *
 * <p>⚠️ <strong>That closes ticket 19's actual worry.</strong> A tool handler reaches a service
 * directly, so this check is the only authorization on the path — and until the cutover it was checking
 * against a permission model no policy document described. It is the same model as the routes' now.
 *
 * <p><strong>Asked twice, and the two questions differ.</strong>
 *
 * <ul>
 *   <li>{@link #permits} runs before any project is resolved and asks whether the caller holds the
 *       permission <em>in any project at all</em>. A caller holding it nowhere is refused without
 *       learning which projects exist, which is the entire reason the dispatcher checks permission
 *       before scope.
 *   <li>{@link #permitsInScope} runs at the project the call resolved to. This is where Tessera's
 *       model actually lives: a person may transition issues in one project and not in the next, and a
 *       DENY override on them personally beats the role that grants it.
 * </ul>
 *
 * <p>⚠️ <strong>The first question costs a query per project the caller is a member of.</strong> That
 * is honest for a tracker where a person belongs to a handful, and it is the price of a scope-free
 * gate over a model that has no scope-free answer. If it ever stops being cheap, the fix is a query
 * that asks it directly rather than a cache here — the number is read once per call and must not go
 * stale mid-conversation.
 */
@Component
@RequiredArgsConstructor
public class TesseraToolAuthorizer implements ToolAuthorizer {

    private final ProjectAccess               projectAccess;
    private final MemberService               members;
    private final ProjectMembershipRepository memberships;

    @Override
    public boolean permits(CallerIdentity caller, ToolAction action) {
        Member member = members.requireMember(caller.callerId());

        return memberships.findByMemberId(caller.callerId()).stream()
                .map(ProjectMembership::getProjectId)
                .distinct()
                .anyMatch(projectId ->
                        projectAccess.holds(member, projectId, action.requiredPermission()));
    }

    @Override
    public boolean permitsInScope(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        return projectAccess.holds(
                members.requireMember(caller.callerId()), scope.id(), action.requiredPermission());
    }
}
