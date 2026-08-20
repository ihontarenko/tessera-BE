package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.dto.block.ResolveBlocksRequest;
import net.innoventa.tessera.dto.wiki.CreateWikiPageRequest;
import net.innoventa.tessera.dto.wiki.FileWikiPageRequest;
import net.innoventa.tessera.dto.wiki.UpdateWikiPageRequest;
import net.innoventa.tessera.dto.wiki.WikiPageDetail;
import net.innoventa.tessera.dto.wiki.WikiPageSummary;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.block.PageBlockService;
import net.innoventa.tessera.service.wiki.WikiPageService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A project's wiki (TSSR-16).
 *
 * <p>⚠️ <strong>Every route spells the project into the path</strong>, including the ones addressing a
 * single page. A page could have been addressed on its own — an issue is, because an issue page's URL is
 * its key — and doing so would have cost an {@code AccessTargetResolver} plus a lookup on the security
 * path of every request. A wiki page is always reached from inside a project's screen, so the project is
 * already known and there is nothing to resolve.
 *
 * <p>⚠️ <strong>Two permissions, neither of them {@code project:browse}.</strong> A wiki is prose beside
 * the work rather than a view of it, and an installation that wants somebody on the board and not in the
 * handbook has nowhere to say so while the two are one permission — see {@link Permissions#READ_PAGE}.
 * Every project role carries both today, so nothing is narrowed by the split; what it buys is the
 * ability to take one away.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/wiki/pages")
@RequiredArgsConstructor
@RequiresAccess(scope = Scopes.PROJECT)
public class WikiPageController {

    private final WikiPageService wikiPageService;
    private final PageBlockService pageBlockService;
    private final MemberService memberService;

    /**
     * The project's pages — every one of them, titled and excerpted.
     *
     * <p>⚠️ Not filtered by category, deliberately. The screen draws the tree and the pages together and
     * has to know which sections are empty, so a request per section would be a fan of round trips for
     * something one answer already carries. {@code search} narrows it when somebody types.
     */
    @GetMapping
    @RequiresAccess(permission = Permissions.READ_PAGE)
    public List<WikiPageSummary> list(
        @PathVariable String projectId,
        @RequestParam(required = false) String search
    ) {
        return wikiPageService.search(projectId, search);
    }

    @GetMapping("/{pageId}")
    @RequiresAccess(permission = Permissions.READ_PAGE)
    public WikiPageDetail read(@PathVariable String projectId, @PathVariable String pageId) {
        return wikiPageService.read(projectId, pageId);
    }

    /** The same page by its slug — what a link from one page to another resolves through. */
    @GetMapping("/by-slug/{slug}")
    @RequiresAccess(permission = Permissions.READ_PAGE)
    public WikiPageDetail readBySlug(@PathVariable String projectId, @PathVariable String slug) {
        return wikiPageService.readBySlug(projectId, slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAccess(permission = Permissions.WRITE_PAGE)
    public WikiPageDetail create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody CreateWikiPageRequest request
    ) {
        return wikiPageService.create(jwt, projectId, request);
    }

    /** ⚠️ Replaces the document, and there is no history behind it — see {@link UpdateWikiPageRequest}. */
    @PutMapping("/{pageId}")
    @RequiresAccess(permission = Permissions.WRITE_PAGE)
    public WikiPageDetail update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String pageId,
        @Valid @RequestBody UpdateWikiPageRequest request
    ) {
        return wikiPageService.update(jwt, projectId, pageId, request);
    }

    /**
     * Move a page into a section, or out of the tree.
     *
     * <p>⚠️ Gated on {@code page:write} rather than on {@code category:manage}: re-filing changes the
     * page, not the tree. Somebody who may write pages but not edit the sections can still put their own
     * page in the right one.
     */
    @PutMapping("/{pageId}/category")
    @RequiresAccess(permission = Permissions.WRITE_PAGE)
    public WikiPageDetail fileInto(
        @PathVariable String projectId,
        @PathVariable String pageId,
        @RequestBody FileWikiPageRequest request
    ) {
        return wikiPageService.fileInto(projectId, pageId, request.categoryId());
    }

    /**
     * Resolve the live-data directives this page contains (TSSR-18).
     *
     * <p>⚠️ <strong>The page's own text is the allowlist.</strong> The engine answers a directive only
     * when its exact line appears in the <em>stored</em> markdown of the page named in the path. Without
     * that check this route is a way to read any issue in any project by naming it, dressed as rendering
     * a page: the caller supplies the directive, so the caller would be choosing what gets looked up.
     * {@code DirectiveMatcher} is where the argument is made in full, and {@code page:read} is the outer
     * gate on top of it.
     *
     * <p>⚠️ <strong>A POST that reads, and the body is why.</strong> A page carries several directives,
     * each with a free-text argument. A query string would cap the request at whatever a URL may hold and
     * turn the whole set into a cache key on every proxy between here and the reader. It is a batch, not
     * a mutation.
     *
     * <p>⚠️ <strong>This method is the seam TSSR-19 cuts along.</strong> When pages move to WiQi the
     * resolvers stay here — "what is TSSR-4 doing right now" is a question only this product can answer —
     * and the caller changes from a wiki down the hall to one across the network. Everything under
     * {@code service.block} is written to survive that unchanged; this is the one place that will not.
     */
    @PostMapping("/{pageId}/blocks")
    @RequiresAccess(permission = Permissions.READ_PAGE)
    public List<PageBlockView> resolveBlocks(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String pageId,
        @Valid @RequestBody ResolveBlocksRequest request
    ) {
        return pageBlockService.resolve(
            memberService.resolveMember(jwt),
            wikiPageService.markdownOf(projectId, pageId),
            request);
    }

    /** ⚠️ Permanent — a wiki page has no archive the way an issue does. */
    @DeleteMapping("/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAccess(permission = Permissions.WRITE_PAGE)
    public void delete(@PathVariable String projectId, @PathVariable String pageId) {
        wikiPageService.delete(projectId, pageId);
    }

}
