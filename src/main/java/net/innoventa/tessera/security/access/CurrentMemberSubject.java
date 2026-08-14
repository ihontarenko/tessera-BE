package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.access.Subject;
import org.jmouse.access.enforcement.CurrentSubject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Who is asking, as the engine understands it.
 *
 * <p><strong>The subject identifier is the {@link Member} row's id, and it has to be.</strong> Identity
 * is centralized and authorization is not: Identity's token carries a {@code sub} that means something
 * to Identity, and every grant Tessera holds is keyed on the local member. Handing the engine the
 * token's subject would resolve an empty permission set for everybody, silently — the one failure the
 * whole model is built to avoid.
 *
 * <p>⚠️ <strong>Resolving provisions, and that is deliberate.</strong> The guard runs <em>around</em> a
 * controller method, so it is now the first thing a brand-new caller reaches — earlier than the service
 * call that used to create their row. {@code resolveMember} is find-or-provision and idempotent under a
 * race, so the first request of a member's life still works; a lookup that merely failed would refuse
 * every one of them with "not in this project", which is true and useless.
 *
 * <p>A caller with no token at all reads as {@link Subject#anonymous()}, which
 * {@link net.innoventa.tessera.security.access.axis.IdentityAxis} turns into <em>sign in</em>. That
 * path is only reachable where a route is deliberately public —
 * Spring Security refuses everything else before a controller runs — so it is a real answer rather than
 * a defensive one.
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberSubject implements CurrentSubject {

    private final MemberService members;

    @Override
    public Subject get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Subject.anonymous();
        }

        if (!(authentication.getPrincipal() instanceof Jwt token)) {
            return Subject.anonymous();
        }

        return of(members.resolveMember(token));
    }

    /**
     * One member, as a subject.
     *
     * <p>The description is what a debug line and the management screen print. It names the person
     * rather than their key on purpose: an authorization log reading {@code member-a3f9} is a log nobody
     * can use during an incident, and how much of a person to reveal in one is a product's question
     * rather than a library's.
     */
    public static Subject of(Member member) {
        return member == null
                ? Subject.anonymous()
                : Subject.of(member.getId(), describe(member));
    }

    private static String describe(Member member) {
        return member.getEmail() == null ? member.getDisplayName() : member.getEmail();
    }
}
