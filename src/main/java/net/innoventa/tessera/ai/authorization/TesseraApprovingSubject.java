package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.jmouse.ai.mcp.authorization.server.ApprovingSubject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Who is approving, in a product that has <strong>no service sub-accounts at all</strong>.
 *
 * <p>⚠️ <strong>The list has exactly one entry, and that is the whole of Tessera's identity model.</strong>
 * A connected client acts <em>as the person who approved it</em> — the same permissions, in the same
 * projects, refused by the same rules that answer for their own browser. There is nothing to choose
 * between, so the shared screen renders no picker and the approval carries no choice.
 *
 * <p>The other product using this flow answers the same call with several: a person there owns agent
 * accounts and picking one is the most important thing on the screen. Both shapes go through one
 * interface, which is what keeps the flow blind to what a credential is issued <em>against</em>.
 */
@Component
@RequiredArgsConstructor
public class TesseraApprovingSubject implements ApprovingSubject {

    private final MemberService memberService;

    @Override
    public Approver current() {
        Member member = memberService.resolveMember(signedInToken());

        return new Approver(
                member.getDisplayName(),
                member.getEmail(),
                List.of(Choice.of(member.getId(), member.getDisplayName(), member.getEmail())));
    }

    /**
     * ⚠️ Read from the security context rather than a controller argument, because the endpoint asking is
     * the library's and it has no business taking a {@code Jwt} parameter. The chain has already refused
     * anybody who is not signed in, so the failure below is a wiring mistake rather than a caller's.
     */
    private Jwt signedInToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            throw new McpAuthorizationException(
                    "Only somebody signed in to Tessera can approve a client, and this request is not.");
        }

        return token;
    }
}
