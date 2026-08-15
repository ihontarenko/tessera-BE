package net.innoventa.tessera.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.MemberAvatarView;
import net.innoventa.tessera.service.MemberAvatarService;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Your own face — the only one anybody may change.
 *
 * <p>⚠️ <strong>There is no route to another member's avatar, and that is the whole authorization
 * model.</strong> Every path here resolves the caller from their own token and edits that member, so
 * "may I change this person's picture" is not a question the server has to answer — it cannot be
 * asked. A bare {@code @RequiresAccess} is therefore exactly right: signed in, and nothing further.
 * An administrator changing somebody else's avatar is not a feature Tessera has, and adding one later
 * means adding a route with a permission on it rather than loosening one of these.
 *
 * <p>Three verbs for three kinds, rather than one endpoint taking a discriminated body: choosing a
 * generated face is JSON, uploading is multipart, and clearing carries nothing at all. Folding those
 * into one route would mean a body that is three different shapes and a handler that unpicks which.
 */
@RestController
@RequestMapping("/api/members/me/avatar")
@RequiredArgsConstructor
@RequiresAccess
public class MemberAvatarController {

    private final MemberService       memberService;
    private final MemberAvatarService memberAvatarService;

    /**
     * Wear a generated pixel face.
     *
     * @param request the seed to draw it from — whatever the picker offered, not a key into any
     *                catalog the server keeps
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public MemberAvatarView choosePreset(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody PresetAvatarRequest request) {
        return memberAvatarService.choosePreset(memberService.resolveMember(jwt), request.preset());
    }

    /**
     * Wear an uploaded picture.
     *
     * <p>The interface has already cropped it square and downscaled it to 256x256 before it arrives —
     * see {@code UI/src/lib/squareImage.ts}. Nothing here depends on that having happened: what is
     * accepted is decided by {@code tessera.file.upload}, and a client that skips the step is refused
     * on size rather than quietly storing a photograph.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MemberAvatarView uploadPicture(@AuthenticationPrincipal Jwt jwt,
                                          @RequestPart("file") MultipartFile file) {
        return memberAvatarService.uploadPicture(memberService.resolveMember(jwt), file);
    }

    /**
     * Drop back to drawn initials.
     *
     * <p>Returns the resulting avatar rather than 204, because the caller's next act is to render it
     * and a body it can trust saves a round trip to find out what "cleared" resolved to.
     */
    @DeleteMapping
    public MemberAvatarView clear(@AuthenticationPrincipal Jwt jwt) {
        return memberAvatarService.clear(memberService.resolveMember(jwt));
    }

    /** The seed a generated pixel face is drawn from. */
    public record PresetAvatarRequest(@NotBlank String preset) {
    }

}
