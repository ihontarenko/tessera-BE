package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.ToolInvocation;
import org.springframework.stereotype.Component;

/**
 * The member behind the identifier a tool call carries.
 *
 * <p>The mechanism hands a handler identifiers and no rows, deliberately: turning one back into an
 * account is the product's business and a library that did it would have to know what an account is.
 * This is where that happens, once.
 *
 * <p>⚠️ <strong>{@code actingSubject}, not {@code callerId}, even though Tessera's are equal.</strong>
 * They are equal because this product has no service sub-accounts, not because the distinction is
 * meaningless — and writing the one that means <em>whose rows are in view</em> is what keeps that true
 * if Tessera ever grows agents. The two accessors exist so that reading one is a decision.
 */
@Component
@RequiredArgsConstructor
public class ToolMembers {

    private final MemberService memberService;

    /** Whose issues are in view — for Tessera, always the person asking. */
    public Member actingSubject(ToolInvocation invocation) {
        return memberService.requireMember(invocation.actingSubject());
    }
}
