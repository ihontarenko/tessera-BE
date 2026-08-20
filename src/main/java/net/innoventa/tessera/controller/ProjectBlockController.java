package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.dto.block.ResolveBlocksRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.block.PageBlockService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Live-data directives resolved for a document Tessera does <strong>not</strong> own (TSSR-19, TSSR-0097).
 *
 * <h2>⚠️ Why this exists beside {@code WikiPageController}'s page-scoped route</h2>
 *
 * That one answers a directive only when its exact line appears in the <em>stored</em> markdown of a page
 * <em>in Tessera's own database</em>. Once pages live in Kiwi there is no such page to check against: the
 * document is Kiwi's, the reader is on Tessera's screen, and the question — <em>what is TSSR-4 doing right
 * now</em> — is still one only this product can answer. That is exactly the seam
 * {@code WikiPageController.resolveBlocks} names as "the one place that will not survive TSSR-19".
 *
 * <h2>⚠️ Dropping the {@code DirectiveMatcher} gate is a decision, not an oversight</h2>
 *
 * KW-1's fourth finding, argued out during the grilling and recorded there: <strong>the matcher buys
 * nothing for an authenticated reader.</strong> Every resolver already authorises the person — an
 * {@code :::issue} for a project they cannot browse comes back {@code NOT_FOUND} from the resolver itself
 * — so a directive reveals nothing this product's own API would not tell the same caller if asked
 * directly. What the gate protected against was a caller <em>choosing</em> what to look up on a route that
 * had no other check; here {@link Permissions#BROWSE_PROJECT} plus the resolvers are that check.
 *
 * <p>⚠️ <strong>It returns for the anonymous path</strong>, where there is no person to authorise and the
 * caller's choice really is the only input — that is INVT-0092's public republishing, and it is why
 * {@code DirectiveMatcher} is kept rather than deleted.
 *
 * <h2>⚠️ A POST that reads</h2>
 *
 * A page carries several directives, each with a free-text argument. A query string would cap the request
 * at whatever a URL may hold and turn the whole set into a cache key on every proxy between here and the
 * reader. It is a batch, not a mutation.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/blocks")
@RequiredArgsConstructor
@RequiresAccess(scope = Scopes.PROJECT)
public class ProjectBlockController {

    private final PageBlockService pageBlockService;
    private final MemberService memberService;

    @PostMapping("/resolve")
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT)
    public List<PageBlockView> resolve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody ResolveBlocksRequest request
    ) {
        return pageBlockService.resolveUnbound(memberService.resolveMember(jwt), request);
    }

}
