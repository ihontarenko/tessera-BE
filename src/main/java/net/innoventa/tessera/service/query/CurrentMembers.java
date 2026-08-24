package net.innoventa.tessera.service.query;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Who is behind the current request, for the places that are not a controller.
 *
 * <h2>⚠️ A controller takes {@code @AuthenticationPrincipal Jwt} and must go on doing so</h2>
 *
 * <p>An argument is visible in the signature, testable without a security context and impossible to
 * forget. This exists for the cases where there is no signature to put it in — a bean implementing a
 * library's port, called by that library's controller, where the seam is deliberately free of anything
 * Tessera-shaped.</p>
 *
 * <p>⚠️ Reaching for this from an ordinary service is how a request's identity stops being traceable
 * through the call stack.</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
@Component
@RequiredArgsConstructor
public class CurrentMembers {

    private final MemberService members;

    /**
     * @return the signed-in member, or {@code null} where nobody is
     */
    public Member current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (!(authentication.getPrincipal() instanceof Jwt token)) {
            return null;
        }

        return members.resolveMember(token);
    }

    /**
     * ⚠️ The identifier alone, which is what a library port wants — a library holding this product's
     * member type would make every product's member the same type.
     *
     * @return the identifier, or {@code null} where nobody is signed in
     */
    public String identifier() {
        Member caller = current();

        return caller == null ? null : caller.getId();
    }
}
