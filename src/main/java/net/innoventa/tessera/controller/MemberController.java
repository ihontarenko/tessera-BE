package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.CurrentMemberResponse;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Who a person is, and who else there is.
 *
 * <p>A bare {@code @RequiresAccess} throughout — a signed-in caller and nothing more. Neither route is
 * about a project, so neither has a scope to be refused at, and the member directory is deliberately
 * open to everybody signed in: it is the picker somebody adds a colleague to a project from, and gating
 * it on a project permission would mean only administrators could name a person.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@RequiresAccess
public class MemberController {

    private final MemberService memberService;

    /**
     * The current member's identity and global tier. Also the provisioning point: resolving the
     * caller here creates their {@code Member} row the first time they are ever seen.
     */
    @GetMapping("/me")
    public CurrentMemberResponse me(@AuthenticationPrincipal Jwt jwt) {
        return memberService.describe(memberService.resolveMember(jwt));
    }

    /**
     * All known members, optionally filtered — the picker source for adding someone to a project.
     * Only members who have themselves signed in at least once exist (they are provisioned lazily).
     */
    @GetMapping
    public List<MemberSummary> search(@RequestParam(name = "query", required = false) String query) {
        return memberService.search(query);
    }

}
