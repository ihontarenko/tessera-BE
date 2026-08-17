package net.innoventa.tessera.service.wiki;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.CategoryEntityType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.WikiPage;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.wiki.CreateWikiPageRequest;
import net.innoventa.tessera.dto.wiki.UpdateWikiPageRequest;
import net.innoventa.tessera.dto.wiki.WikiPageDetail;
import net.innoventa.tessera.dto.wiki.WikiPageSummary;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.WikiPageRepository;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.category.CategoryFilingService;
import net.innoventa.tessera.service.category.CategoryService;
import net.innoventa.tessera.service.category.Slugs;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A project's wiki: reading, writing, re-filing and removing pages (TSSR-16).
 *
 * <p><strong>Small on purpose.</strong> TSSR-5's investigation read Innoventa's page engine in full and
 * ruled that nothing be extracted: two thirds of those 2084 lines are that product's policy — spaces,
 * share tokens, a visibility rule written as JPQL — and a page is markdown in a column plus a product's
 * opinions about who may read it. Tessera's opinions are project membership and two permissions, so this
 * is what is left when those are the only ones.
 *
 * <p>⚠️ <strong>Where a page sits is not this class's business.</strong> The hierarchy is the category
 * tree, and {@link CategoryFilingService} owns membership in it. Everything here does with a category is
 * check it belongs to the same project and hand the pair over.
 *
 * <p>⚠️ <strong>There is no version history</strong>, so {@link #update} destroys what was there. That is
 * ruled, not overlooked — and it is the reason nothing in this class quietly rewrites a page as a side
 * effect of something else.
 */
@Service
@RequiredArgsConstructor
public class WikiPageService {

    private final WikiPageRepository wikiPageRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final CategoryService categoryService;
    private final CategoryFilingService filingService;
    private final Supplier<String> idGenerator;

    /** Every page in the project, titled and excerpted, with where each one is filed. */
    @Transactional(readOnly = true)
    public List<WikiPageSummary> list(String projectId) {
        return summarise(wikiPageRepository.findByProjectIdOrderByTitleAsc(projectId));
    }

    /** The pages whose title or prose mentions this, most recently touched first. */
    @Transactional(readOnly = true)
    public List<WikiPageSummary> search(String projectId, String text) {
        if (text == null || text.isBlank()) {
            return list(projectId);
        }

        return summarise(wikiPageRepository.search(projectId, text.trim()));
    }

    @Transactional(readOnly = true)
    public WikiPageDetail read(String projectId, String pageId) {
        return toDetail(requirePage(projectId, pageId));
    }

    /**
     * A page's stored Markdown, and nothing else.
     *
     * <p>Exists for one caller: resolving the live-data directives a page contains (TSSR-18). ⚠️ The
     * block engine deliberately takes <strong>text</strong> rather than a page identifier — it does not
     * know what a page is, which is what keeps it out of the wiki's way when TSSR-19 moves pages to WiQ.
     * This method is the seam that hands it the text, and it must always be the <em>stored</em> text: a
     * copy sent by the client would make the allowlist the caller's to write.
     */
    @Transactional(readOnly = true)
    public String markdownOf(String projectId, String pageId) {
        return requirePage(projectId, pageId).getContentMarkdown();
    }

    /**
     * The same page addressed by its slug — what a link between pages resolves through.
     *
     * <p>⚠️ A slug is re-derived when a page is renamed, so a link written against an old one stops
     * resolving. That is the cost of an address that stays honest about what it names; the alternative is
     * a page called "Deployment" living at {@code /wiki/notes} forever.
     */
    @Transactional(readOnly = true)
    public WikiPageDetail readBySlug(String projectId, String slug) {
        return toDetail(wikiPageRepository.findByProjectIdAndSlug(projectId, slug)
            .orElseThrow(() -> new ResourceNotFoundException("No such page: " + slug)));
    }

    @Transactional
    public WikiPageDetail create(Jwt jwt, String projectId, CreateWikiPageRequest request) {
        Member author = memberService.resolveMember(jwt);
        String categoryId = categoryService.requireOptionalCategory(projectId, request.categoryId());
        String markdown = request.contentMarkdown();

        WikiPage page = wikiPageRepository.save(WikiPage.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .title(request.title().trim())
            .slug(freeSlug(projectId, request.title()))
            .contentMarkdown(markdown)
            .excerpt(Excerpts.of(markdown))
            .authorMemberId(author.getId())
            .updatedByMemberId(author.getId())
            .build());

        filingService.fileInto(CategoryEntityType.PAGE, page.getId(), categoryId);

        return toDetail(page);
    }

    /**
     * Replace a page's title and text.
     *
     * <p>⚠️ <strong>The slug follows the title</strong>, so renaming a page changes its address — see
     * {@link #readBySlug}. It is only re-derived when the title actually changed, which keeps a save that
     * only touched the prose from quietly breaking every link to the page.
     */
    @Transactional
    public WikiPageDetail update(Jwt jwt, String projectId, String pageId, UpdateWikiPageRequest request) {
        Member editor = memberService.resolveMember(jwt);
        WikiPage page = requirePage(projectId, pageId);
        String title = request.title().trim();

        if (!title.equals(page.getTitle())) {
            page.setTitle(title);
            page.setSlug(freeSlugExcept(projectId, title, page.getSlug()));
        }

        page.setContentMarkdown(request.contentMarkdown());
        page.setExcerpt(Excerpts.of(request.contentMarkdown()));
        page.setUpdatedByMemberId(editor.getId());

        return toDetail(wikiPageRepository.save(page));
    }

    /** Move a page into a section, or out of the tree entirely. */
    @Transactional
    public WikiPageDetail fileInto(String projectId, String pageId, String categoryId) {
        WikiPage page = requirePage(projectId, pageId);

        filingService.fileInto(CategoryEntityType.PAGE, page.getId(),
                               categoryService.requireOptionalCategory(projectId, categoryId));

        return toDetail(page);
    }

    /**
     * Remove a page.
     *
     * <p>⚠️ <strong>Permanent</strong>, and there is no archive to soften it the way an issue has one
     * (TSSR-4). A wiki page is not work that finished; it is either current or it is wrong, and a
     * graveyard of superseded runbooks is worse than none. Its filing goes with it.
     */
    @Transactional
    public void delete(String projectId, String pageId) {
        WikiPage page = requirePage(projectId, pageId);

        filingService.fileInto(CategoryEntityType.PAGE, page.getId(), null);
        wikiPageRepository.delete(page);
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    private List<WikiPageSummary> summarise(List<WikiPage> pages) {
        if (pages.isEmpty()) {
            return List.of();
        }

        // Both lookups are one query for the whole list rather than one per row — a wiki index is the
        // screen where an N+1 shows up as a visible pause.
        Map<String, String> categories = filingService.categoriesOf(
            CategoryEntityType.PAGE, pages.stream().map(WikiPage::getId).toList());

        Map<String, MemberSummary> members = membersOf(pages);

        return pages.stream()
            .map(page -> new WikiPageSummary(
                page.getId(),
                page.getTitle(),
                page.getSlug(),
                page.getExcerpt(),
                categories.get(page.getId()),
                members.get(page.getAuthorMemberId()),
                members.get(page.getUpdatedByMemberId()),
                page.getCreatedAt(),
                page.getUpdatedAt()))
            .toList();
    }

    private WikiPageDetail toDetail(WikiPage page) {
        Map<String, MemberSummary> members = membersOf(List.of(page));

        return new WikiPageDetail(
            page.getId(),
            page.getTitle(),
            page.getSlug(),
            page.getContentMarkdown(),
            page.getExcerpt(),
            filingService.categoryOf(CategoryEntityType.PAGE, page.getId()).orElse(null),
            members.get(page.getAuthorMemberId()),
            members.get(page.getUpdatedByMemberId()),
            page.getCreatedAt(),
            page.getUpdatedAt());
    }

    /** Everybody who wrote or last touched any of these, in one read. */
    private Map<String, MemberSummary> membersOf(Collection<WikiPage> pages) {
        List<String> memberIds = pages.stream()
            .flatMap(page -> Stream.of(page.getAuthorMemberId(), page.getUpdatedByMemberId()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<String, MemberSummary> byId = new HashMap<>();

        for (Member member : memberRepository.findAllById(memberIds)) {
            byId.put(member.getId(), MemberSummary.from(member));
        }

        return byId;
    }

    /**
     * The page with this id, in this project.
     *
     * <p>⚠️ A page in another project is a 404 rather than a 403, the same disclosure rule the rest of
     * this product follows: naming one back would confirm it exists to somebody who cannot see it.
     */
    private WikiPage requirePage(String projectId, String pageId) {
        return wikiPageRepository.findByIdAndProjectId(pageId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("No such page: " + pageId));
    }

    private String freeSlug(String projectId, String title) {
        return Slugs.unique(title, candidate -> wikiPageRepository.existsByProjectIdAndSlug(projectId, candidate));
    }

    /** The same, but a page keeping its own slug is not colliding with itself. */
    private String freeSlugExcept(String projectId, String title, String ownSlug) {
        return Slugs.unique(title, candidate ->
            !candidate.equals(ownSlug) && wikiPageRepository.existsByProjectIdAndSlug(projectId, candidate));
    }

}
