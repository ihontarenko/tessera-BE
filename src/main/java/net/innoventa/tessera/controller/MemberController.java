package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.CurrentMemberResponse;
import net.innoventa.tessera.domain.MemberKind;
import net.innoventa.tessera.dto.MemberAvatarView;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.MemberAvatarService;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    private final MemberService       memberService;
    private final MemberAvatarService memberAvatarService;

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

    /**
     * The administration list — people, clients, or both (TSSR-79).
     *
     * <p>⚠️ <strong>A route of its own, behind a permission of its own.</strong> The directory above
     * stays open to every signed-in caller because it is the picker somebody adds a colleague from.
     * This one shows clients, whose they are and whether they are retired, and it is the screen those
     * rows are administered on — so it asks for {@link Permissions#ADMINISTER_MEMBERS}, which exists
     * because {@code configuration:administer} says in as many words that accounts must not inherit it.
     *
     * @param kind {@code PERSON}, {@code AGENT}, or omitted for every member. The interface calls the
     *             three segments <em>People</em>, <em>Clients</em> and <em>All</em>; the wire keeps the
     *             domain's own words so nothing has to translate them twice.
     */
    @GetMapping("/administered")
    @RequiresAccess(permission = Permissions.ADMINISTER_MEMBERS, scope = Scopes.GLOBAL)
    public List<MemberSummary> administered(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "kind",  required = false) MemberKind kind) {
        return memberService.administered(query, kind);
    }

    /**
     * Renames a member — a person, or somebody else's client (TSSR-80).
     *
     * <p>⚠️ For a client this goes through the agent directory, so the mirror follows and every by-line
     * it has ever left starts reading the new name. See {@link MemberService#rename}.
     */
    @PatchMapping("/{memberId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_MEMBERS, scope = Scopes.GLOBAL)
    public MemberSummary rename(
            @PathVariable String memberId,
            @Valid @RequestBody RenameMemberRequest request) {
        return memberService.rename(memberId, request.displayName());
    }

    /** What a member is called. Blank is refused: a nameless row is unreadable everywhere it appears. */
    public record RenameMemberRequest(@NotBlank @Size(max = 200) String displayName) { }

    // ── Somebody else's face (TSSR-80) ───────────────────────────────────────────

    /**
     * ⚠️ <strong>Not {@code /members/me/avatar} with an identifier.</strong> That route takes the
     * signed-in member and structurally cannot name another; these three name one and are gated on
     * {@code member:administer}, the same gate as the rename above.
     *
     * <p>A client is provisioned wearing a generated face seeded from its own identifier, so re-picking
     * a preset is the ordinary case here rather than a fallback.
     */
    @PutMapping("/{memberId}/avatar")
    @RequiresAccess(permission = Permissions.ADMINISTER_MEMBERS, scope = Scopes.GLOBAL)
    public MemberAvatarView choosePresetFor(
            @PathVariable String memberId,
            @Valid @RequestBody ChoosePresetRequest request) {
        return memberAvatarService.choosePreset(memberService.requireMember(memberId), request.preset());
    }

    @PostMapping(value = "/{memberId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAccess(permission = Permissions.ADMINISTER_MEMBERS, scope = Scopes.GLOBAL)
    public MemberAvatarView uploadPictureFor(
            @PathVariable String memberId,
            @RequestParam("file") MultipartFile file) {
        return memberAvatarService.uploadPicture(memberService.requireMember(memberId), file);
    }

    /** Back to drawn initials. ⚠️ On a client this is rarely what anybody wants — its generated face is
     *  its identity — but refusing it here would be a rule invented in a controller. */
    @DeleteMapping("/{memberId}/avatar")
    @RequiresAccess(permission = Permissions.ADMINISTER_MEMBERS, scope = Scopes.GLOBAL)
    public MemberAvatarView clearAvatarFor(@PathVariable String memberId) {
        return memberAvatarService.clear(memberService.requireMember(memberId));
    }

    /** The seed a generated face is drawn from — not a key into a catalogue, the generator is total. */
    public record ChoosePresetRequest(@NotBlank @Size(max = 100) String preset) { }

}
