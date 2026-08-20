package net.innoventa.tessera.config;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.block.PageBlockDirectiveResolver;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.jmouse.liveblocks.DirectiveResolution;
import org.jmouse.liveblocks.DirectiveResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Tessera as something another product's document can quote (TSSR-84).
 *
 * <p>The route, the wire contract and the dispatcher are {@code jmouse-liveblocks}' (JMF-18). This class
 * is the only Tessera-shaped part: which resolvers exist, who is asking, and where a link points.
 *
 * <h2>⚠️ It builds the library's {@code DirectiveResolution} itself rather than registering beans</h2>
 *
 * <p>The library's autoconfiguration collects {@code DirectiveResolver} beans and stands aside for a
 * {@code DirectiveResolution} that already exists. Tessera takes that door because its resolvers are not
 * known one by one: they are however many {@code PageBlockResolver}s the container holds, each wrapped.
 * Adding a directive stays what it was — one {@code PageBlockResolver} bean — and nothing here is edited.
 */
@Configuration
public class LiveBlocksConfiguration {

    /**
     * Where a <em>person</em> reaches Tessera.
     *
     * <p>⚠️ <strong>Not {@code tessera.security.resource}.</strong> That is where a client reaches the
     * API, and the API serves no screen — a block whose link went there would land somebody on JSON. The
     * two are different servers in development and different hostnames in a deployment.
     */
    @Value("${tessera.liveblocks.browser-url:${TESSERA_BROWSER_BASE_URL:http://localhost:5050}}")
    private String browserUrl;

    /**
     * Every resolver this product has, wrapped for the remote path.
     *
     * <p>⚠️ <strong>The caller is a supplier, resolved per call.</strong> These are singletons and the
     * reader changes every request; capturing a {@code Member} at construction would answer every future
     * question as whoever happened to make the first one.
     */
    @Bean
    public DirectiveResolution directiveResolution(
            ObjectProvider<PageBlockResolver> resolvers, MemberService memberService) {

        List<DirectiveResolver> adapted = resolvers.stream()
                .map(resolver -> (DirectiveResolver) new PageBlockDirectiveResolver(
                        resolver, () -> callingMember(memberService), browserUrl))
                .toList();

        return new DirectiveResolution(adapted);
    }

    /**
     * Who is reading the other product's page.
     *
     * <p>⚠️ <strong>Read from the security context, which is correct here and would not be everywhere.</strong>
     * This runs on a real request thread, so the context is populated. The reason {@code BlockRequest}
     * carries the caller instead of reading one is a different path entirely — a protocol tool call has
     * neither a {@code SecurityContext} nor a request scope, and a resolver reading one there would work
     * from a screen and silently answer as nobody from a tool.
     *
     * <p>Answers null rather than throwing when there is no token: the route is authenticated, so this
     * should not happen, and a directive that comes back {@code NO_ACCESS} is a better way to find out
     * than a stack trace on somebody else's page.
     */
    private Member callingMember(MemberService memberService) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return memberService.resolveMember(jwt);
    }

}
