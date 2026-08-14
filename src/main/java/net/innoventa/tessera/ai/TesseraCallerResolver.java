package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolRefusedException;
import org.jmouse.ai.spi.CallerResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Who Tessera is running a tool call as.
 *
 * <p><strong>One identity, not two, and that is the whole of it.</strong> Tessera has no service
 * sub-accounts: an assistant runs under the person's own session, so the caller and the acting subject
 * are the same member and {@link CallerIdentity#of(String)} says exactly that. Nothing below had to
 * learn about it — which is the interesting part, because the library was designed around a product
 * where the two differ.
 *
 * <p>⚠️ <strong>The identifier is the member's, not the token's subject.</strong> Everything Tessera
 * authorizes is keyed on a {@code Member} row, so carrying the identity-provider subject would mean
 * every handler translating it again — and the translation is a query, so it would be thirty of them.
 * The subject travels as an attribute for anything that wants to know where the member came from.
 */
@Component
@RequiredArgsConstructor
public class TesseraCallerResolver implements CallerResolver {

    /** What the member is called, so a trail or a log line reads as a person. */
    public static final String CALLER_NAME = "caller.name";

    /** And the identity-provider subject the member was matched on. */
    public static final String CALLER_SUBJECT = "caller.subject";

    private final MemberService memberService;

    @Override
    public CallerIdentity resolve() {
        Member member = memberService.requireBySubject(currentSubject());

        return CallerIdentity.of(member.getId()).with(Map.of(
                CALLER_NAME,    member.getDisplayName() == null ? member.getEmail() : member.getDisplayName(),
                CALLER_SUBJECT, member.getSubject()));
    }

    private String currentSubject() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Jwt token && token.getClaimAsString(JwtClaimNames.SUB) != null) {
            return token.getClaimAsString(JwtClaimNames.SUB);
        }

        throw new ToolRefusedException(RefusalReason.NO_CALLER,
                "Nothing was authenticated, so there is nobody to run this as. Sign in and try again.");
    }
}
