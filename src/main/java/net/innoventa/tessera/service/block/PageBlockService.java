package net.innoventa.tessera.service.block;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.block.BlockStatus;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.dto.block.ResolveBlocksRequest;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The live-data directives a document embeds, resolved at view time (TSSR-18).
 *
 * <p>What makes a page worth writing in a tracker rather than in a text file: {@code :::issue TSSR-4}
 * draws the issue's <em>current</em> state, so a runbook written in March is still right in September.
 *
 * <h2>What this class knows, and what it deliberately does not</h2>
 *
 * <p>It knows how to dispatch, how to batch, and one security rule. It does not know what an issue, a
 * sprint or a board is — those arrive as {@link PageBlockResolver}s through the container, each living
 * with the concept that owns its meaning.
 *
 * <p>⚠️ <strong>And it does not know what a page is either.</strong> The caller hands it markdown, not a
 * page identifier. That is what keeps this engine out of the wiki's way when TSSR-19 moves pages to WiQ:
 * the caller changes from a controller down the hall to one across the network, and nothing here moves.
 *
 * <h2>⚠️ The document is the allowlist</h2>
 *
 * <p>A directive is answered only when its exact line appears in the markdown it was asked against.
 * Without that check the endpoint is a way to read any issue in any project by naming it — the caller
 * supplies the directive, so the caller would be choosing what gets looked up. See
 * {@link DirectiveMatcher}, which is where the argument is made in full.
 *
 * <p>⚠️ <strong>The check is applied to every caller, including a signed-in one</strong>, which is
 * stricter than Innoventa's version — that one gates public pages only. Tessera has no public pages, so
 * a second, laxer path would exist purely to be the one somebody eventually calls by mistake.
 */
@Service
@Transactional(readOnly = true)
public class PageBlockService {

    private final Map<String, PageBlockResolver> resolversByDirective;

    public PageBlockService(ObjectProvider<PageBlockResolver> resolvers) {
        this.resolversByDirective = resolvers.stream()
            .collect(Collectors.toUnmodifiableMap(
                resolver -> DirectiveMatcher.normaliseName(resolver.directive()), Function.identity()));
    }

    /**
     * Answer every directive in one document.
     *
     * @param markdown the document's <strong>stored</strong> text — never a copy the client sent, which
     *                 would make the allowlist the caller's to write
     */
    public List<PageBlockView> resolve(Member caller, String markdown, ResolveBlocksRequest request) {
        List<ResolveBlocksRequest.Directive> directives =
            request == null || request.directives() == null ? List.of() : request.directives();

        return directives.stream().map(directive -> resolveOne(caller, markdown, directive)).toList();
    }

    private PageBlockView resolveOne(Member caller, String markdown, ResolveBlocksRequest.Directive directive) {
        String name = DirectiveMatcher.normaliseName(directive.name());
        String argument = directive.argument() == null ? "" : directive.argument().trim();

        // Cheapest refusal first, and the one that does not depend on any resolver existing: this
        // document does not contain that line, so nothing about it is anybody's business.
        if (argument.isEmpty() || !DirectiveMatcher.contains(markdown, name, argument)) {
            return PageBlockView.miss(name, argument, BlockStatus.NOT_ON_THIS_PAGE);
        }

        PageBlockResolver resolver = resolversByDirective.get(name);

        if (resolver == null) {
            return PageBlockView.miss(name, argument, BlockStatus.UNKNOWN_DIRECTIVE);
        }

        return resolver.resolve(new BlockRequest(argument, caller));
    }

}
