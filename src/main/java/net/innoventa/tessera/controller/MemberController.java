package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.CurrentMemberResponse;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
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
