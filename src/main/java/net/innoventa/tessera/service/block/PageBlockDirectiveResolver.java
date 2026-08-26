package net.innoventa.tessera.service.block;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.block.BlockStatus;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.BlockSuggestRequest;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.jmouse.liveblocks.Directive;
import org.jmouse.liveblocks.DirectiveResolver;
import org.jmouse.liveblocks.DirectiveStatus;
import org.jmouse.liveblocks.DirectiveSuggestion;
import org.jmouse.liveblocks.ResolvedDirective;

import java.util.List;
import java.util.function.Supplier;

/**
 * One of this product's resolvers, answering a directive that arrived from <em>another product's</em>
 * document (TSSR-84).
 *
 * <h2>⚠️ The resolvers did not move, and this is the whole of what was needed</h2>
 *
 * <p>{@code PageBlockResolver}'s own note predicted this: <em>"the engine's caller changes from a local
 * controller to a remote one and nothing in this package moves."</em> This class is that change — an
 * adapter, not an engine. Everything about what an issue is stays where it was.
 *
 * <h2>⚠️ The rich payload does not cross the wire, deliberately</h2>
 *
 * <p>{@link PageBlockView} carries a typed record per block kind, which is right inside this product and
 * wrong between two. A consumer that parsed an {@code IssueBlock} would have to be released every time
 * Tessera adds a field to one, and a fourth block kind here would be a release of every product that
 * quotes this one. So it flattens to a title, a subtitle and a link — what a live block actually looks
 * like on a page — and the state that would have been separate fields is composed into the subtitle,
 * where a reader sees it and no renderer has to understand it.
 *
 * <h2>⚠️ There is no page here, so there is no {@code DirectiveMatcher} check</h2>
 *
 * <p>The local path answers a directive only if its exact line appears in the page's stored markdown,
 * because otherwise the caller chooses what gets looked up. That check cannot exist on this path — the
 * document lives in a product Tessera has never heard of and holds no copy of. What replaces it is not
 * weaker: the wrapped resolver already narrows every answer to what the caller may see, so a directive
 * discloses nothing {@code GET /api/issues/{key}} would not tell the same person. {@code WIQ-11} makes
 * this argument in full and is where the decision belongs.
 */
public class PageBlockDirectiveResolver implements DirectiveResolver {

    private final PageBlockResolver resolver;
    private final Supplier<Member>  caller;
    private final String            browserUrl;

    /**
     * @param resolver   the product-side resolver this wraps, unchanged
     * @param caller     ⚠️ <strong>resolved per call, never captured.</strong> This bean is a singleton
     *                   and the reader is different on every request
     * @param browserUrl where a <em>person</em> reaches Tessera. ⚠️ Not the API address: the link is
     *                   followed by somebody clicking it, and the API serves no screen
     */
    public PageBlockDirectiveResolver(
            PageBlockResolver resolver, Supplier<Member> caller, String browserUrl) {

        this.resolver   = resolver;
        this.caller     = caller;
        this.browserUrl = browserUrl.endsWith("/")
                ? browserUrl.substring(0, browserUrl.length() - 1)
                : browserUrl;
    }

    @Override
    public String directive() {
        return resolver.directive();
    }

    /**
     * What another product's picker may offer from this one.
     *
     * <p>⚠️ <strong>Nobody reading is an empty list, not a refusal.</strong> A picker asks every
     * namespace its installation knows and draws a tab per answer; a 401 there would put an error in a
     * dialog somebody opened to write a sentence. The same reasoning as a block on a page read without
     * an account, and the same outcome.
     *
     * <p>The absolute address is composed here for the reason a resolved block's is: a resolver knows
     * where a thing lives inside this product, and which host a browser reaches this product at is a
     * deployment fact it has no business holding.
     */
    @Override
    public List<DirectiveSuggestion> suggest(String query, int limit) {
        Member reader = caller.get();

        if (reader == null) {
            return List.of();
        }

        return resolver.suggest(new BlockSuggestRequest(query, limit, reader)).stream()
                .map(suggestion -> new DirectiveSuggestion(
                        suggestion.reference(),
                        suggestion.label(),
                        suggestion.title(),
                        suggestion.subtitle(),
                        browserUrl + suggestion.path()))
                .toList();
    }

    @Override
    public ResolvedDirective resolve(Directive directive) {
        Member reader = caller.get();

        if (reader == null) {
            return ResolvedDirective.miss(directive, DirectiveStatus.NO_ACCESS);
        }

        PageBlockView answer = resolver.resolve(new BlockRequest(directive.argument().trim(), reader));

        if (answer == null || answer.status() != BlockStatus.RESOLVED) {
            return ResolvedDirective.miss(directive, translate(answer));
        }

        return present(directive, answer);
    }

    /**
     * This product's block statuses, said in the vocabulary every product shares.
     *
     * <h3>⚠️ Tessera never answers {@code NO_ACCESS}, and that is a decision rather than a gap</h3>
     *
     * <p>The library offers it; {@code BlockStatus} deliberately does not have it. Something a reader may
     * not see comes back indistinguishable from something that does not exist, because separating them
     * lets anybody enumerate the tracker by writing directives into a page and reading which came back
     * — {@code BlockStatus.NOT_FOUND} makes the argument, and ADR-0002 buys the same thing everywhere
     * else in this product.
     *
     * <p>⚠️ So the consumer's <em>"that exists and you have not been given it"</em> notice will never
     * appear for a Tessera block. It is not broken; it is Tessera declining to say.
     *
     * <p>{@code NOT_ON_THIS_PAGE} cannot occur here either — it is the local path's allowlist refusal and
     * there is no page on this one. It maps rather than being assumed impossible, because "cannot occur"
     * is how a status eventually reaches a client as a null.
     */
    private static DirectiveStatus translate(PageBlockView answer) {
        if (answer == null) {
            return DirectiveStatus.NOT_FOUND;
        }

        return answer.status() == BlockStatus.UNKNOWN_DIRECTIVE
                ? DirectiveStatus.UNKNOWN_DIRECTIVE
                : DirectiveStatus.NOT_FOUND;
    }

    /**
     * What the other product's page draws.
     *
     * <p>⚠️ <strong>The subtitle is where everything that is not the title goes</strong>, joined with
     * {@code ·} because that is what the consumer renders as one grey line. Blank parts are dropped
     * rather than rendered as gaps: a block that reads {@code TSSR-4 ·  · 8 points} looks like a bug in
     * the product being quoted.
     *
     * <p>⚠️ <strong>Only an issue gets a label, and the other two go without one on purpose.</strong> A
     * label is what a badge drawn inside a sentence prints, so it has to be the name the thing is
     * actually known by — an issue has one and it is the key. A sprint and a board do not: the shortest
     * true name either has is its project's key, and a badge in the middle of a paragraph reading
     * {@code TSSR} where a sprint was meant says less than the words the writer chose. Null lets the
     * document's own text stand.
     */
    private ResolvedDirective present(Directive directive, PageBlockView answer) {
        if (answer.issue() != null) {
            PageBlockView.IssueBlock issue = answer.issue();

            return ResolvedDirective.resolved(
                    directive,
                    // ⚠️ The key as it stands NOW, not the argument that was asked for. A reference
                    // written against the permanent hash has to print the current key, or the whole
                    // point of writing it that way is lost.
                    issue.issueKey(),
                    issue.summary(),
                    line(issue.issueKey(), issue.typeName(), issue.statusName(),
                         points(issue.storyPoints()), issue.assigneeName()),
                    browserUrl + "/issues/" + issue.issueKey());
        }

        if (answer.sprint() != null) {
            PageBlockView.SprintBlock sprint = answer.sprint();

            return ResolvedDirective.resolved(
                    directive,
                    sprint.name(),
                    line(sprint.projectKey(), sprint.state(),
                         sprint.completedIssueCount() + " of " + sprint.issueCount() + " done",
                         points(sprint.storyPoints())),
                    browserUrl + "/projects/" + sprint.projectKey() + "/backlog");
        }

        if (answer.board() != null) {
            PageBlockView.BoardBlock board = answer.board();

            return ResolvedDirective.resolved(
                    directive,
                    board.projectName(),
                    line(board.projectKey(), board.columns().stream()
                            .map(column -> column.name() + " " + column.issueCount())
                            .reduce((left, right) -> left + " · " + right)
                            .orElse(null)),
                    browserUrl + "/projects/" + board.projectKey() + "/board");
        }

        // Resolved with no payload is a defect in a resolver, and one that would otherwise render as an
        // empty link on somebody else's page.
        return ResolvedDirective.miss(directive, DirectiveStatus.NOT_FOUND);
    }

    private static String line(String... parts) {
        StringBuilder assembled = new StringBuilder();

        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            if (!assembled.isEmpty()) {
                assembled.append(" · ");
            }

            assembled.append(part);
        }

        return assembled.isEmpty() ? null : assembled.toString();
    }

    /** ⚠️ The stored weight, not the word a team picked for it (ADR-0019) — relabelling needs a scheme. */
    private static String points(Double storyPoints) {
        if (storyPoints == null) {
            return null;
        }

        return storyPoints == Math.floor(storyPoints)
                ? (long) (double) storyPoints + " points"
                : storyPoints + " points";
    }

}
