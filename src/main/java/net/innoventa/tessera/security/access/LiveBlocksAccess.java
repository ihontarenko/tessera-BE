package net.innoventa.tessera.security.access;

import org.jmouse.access.enforcement.ExternalAccessRules;
import org.jmouse.access.enforcement.ExternalAccessRules.Declaration;
import org.jmouse.liveblocks.web.DirectiveResolveController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What {@code jmouse-liveblocks}' controller requires — stated here, because a library's handler cannot
 * state it itself (TSSR-84).
 *
 * <h2>⚠️ Signed in, and deliberately no permission at all</h2>
 *
 * <p>This is the same shape as {@code AgentSelfController} in {@link AiManagementAccess}, and for the
 * same reason: <strong>the authorization that matters is inside the handler rather than in front of
 * it.</strong> Every directive is answered by a {@code PageBlockResolver} that already asks what this
 * caller may see — an issue in a project they hold nothing at comes back a miss — so a permission in
 * front would decide nothing the resolver does not decide better, and would decide it wrongly.
 *
 * <p>Wrongly, because there is no permission that would fit. {@code BROWSE_PROJECT} is per project and
 * this route names none; anything at {@code GLOBAL} would gate <em>reading a page in another product</em>
 * behind a power somebody holds installation-wide. A person who may open {@code TSSR-4} in Tessera may
 * see it quoted on a WiQi page, and a person who may not, may not. That is the whole rule.
 *
 * <h2>⚠️ Signed in is doing real work here, though</h2>
 *
 * <p>It is what makes the resolvers' narrowing mean anything: without a caller there is nobody to narrow
 * to, and the adapter answers every directive {@code NO_ACCESS} rather than guessing. And it is what
 * keeps this from being an open endpoint on a server other origins can now reach — see
 * {@code SecurityConfiguration}'s CORS allowlist and accepted audiences, which are the other two halves
 * of that sentence.
 */
@Configuration
public class LiveBlocksAccess {

    @Bean
    public ExternalAccessRules liveBlocksAccessRules() {
        return ExternalAccessRules.builder()
                .type(DirectiveResolveController.class, Declaration.authenticated())
                .build();
    }

}
