package net.innoventa.tessera.config;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import net.innoventa.tessera.ai.McpEndpoint;
import org.jmouse.ai.ToolDispatcher;
import org.jmouse.ai.mcp.McpToolServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tessera's tools, published over the Model Context Protocol.
 *
 * <p><strong>Everything here is wiring, and deliberately so.</strong> The catalogue is
 * {@code ToolCatalog}'s, the decisions are {@code ToolDispatcher}'s, and the protocol is the SDK's;
 * this file only says which bean holds which piece. A tool reachable from a client is the <em>same</em>
 * action the in-app assistant calls, through the same dispatcher, with the same permission checked
 * against the same engine — there is no second path into an action, and that is a fact the types hold
 * rather than a promise this class keeps.
 *
 * <h2>⚠️ A servlet, not a Spring MCP module</h2>
 *
 * <p>The SDK's {@code mcp-spring-webmvc} artifact is another dependency for one router function.
 * {@link HttpServletStreamableServerTransportProvider} is a plain {@code HttpServlet} already on the
 * classpath, so it is registered by hand — which also puts it behind the ordinary servlet filter chain,
 * and therefore behind Spring Security. The bearer token is validated and the {@code SecurityContext} is
 * populated exactly as on any other request.
 *
 * <p>⚠️ <strong>It is populated on a thread the tool never runs on</strong> — see
 * {@code AiMcpContextPropagationAutoConfiguration} in {@code jmouse-ai-spring-boot}, which is the only
 * reason {@code TesseraCallerResolver} finds a caller at all.
 *
 * <h2>⚠️ This is what made Tessera move to Boot 4</h2>
 *
 * <p>The SDK ships exactly one JSON binding — {@code mcp-json-jackson3} — and Jackson 3 kept its
 * annotations in the <em>same package</em> Jackson 2 uses. On Boot 3.3.2, whose dependency management
 * pins {@code jackson-annotations} to 2.x, the two cannot share a classpath: the server refused to build
 * at all with {@code NoSuchFieldError: … JsonFormat$Shape does not have member field 'POJO'}, thrown
 * from the SDK's schema validator. Boot 4 is on Jackson 3 itself, so the conflict simply is not there.
 * A Jackson-2 shim was written and thrown away when the upgrade turned out to be the smaller change.
 *
 * <h2>The credential is now confined to this endpoint — and that is where it lives</h2>
 *
 * <p>This block used to describe a gap: a client connected with an ordinary {@code tessera} access token,
 * the same one the browser holds, so a token good for the protocol was equally good for every REST route
 * and a browser's token could drive the tools. It also said the fix belonged to Identity, since Tessera
 * never mints tokens.
 *
 * <p><strong>Both halves have changed.</strong> Identity cannot help — it supports no dynamic client
 * registration, and a protocol client has nowhere to be told a client id, so it never got as far as
 * asking. Tessera therefore issues the protocol's credential itself and verifies it on a filter chain of
 * its own: {@code McpCredentialService} for what is signed, {@code ClientAuthorizationFlow} for who
 * approves it, {@code SecurityConfiguration.mcpSecurityFilterChain} for what accepts it, and ADR-0020 for
 * why. The confinement is a signature that does not verify elsewhere rather than a check somewhere.
 *
 * <p>⚠️ <strong>Authorization did not change and must not.</strong> Every tool call still goes through the
 * dispatcher to {@code TesseraToolAuthorizer} and the same access engine, as the same member. A credential
 * decides <em>whether</em> a caller reaches the protocol; it never decides what they may do there.
 *
 * <p>⚠️ <strong>A tool call runs on a different thread than the request</strong>, and without something
 * carrying the caller across that hop every call here refuses as <em>NO_CALLER</em> while the credential
 * is perfectly valid — read {@code AiMcpContextPropagationAutoConfiguration} before believing a token is
 * at fault. ⚠️ <strong>Nothing in this file does that</strong>, on purpose: it is a property of the
 * transport rather than of Tessera, it bit <strong>Innoventa</strong> identically, and it now lives in
 * {@code jmouse-ai-spring-boot} where neither product has to know it exists. It switches itself on
 * wherever an {@code McpSyncServer} bean is present — including the one built below by hand.
 */
@Configuration
public class McpConfiguration {

    /**
     * What a client is told this server is, and what it is for.
     *
     * <p>The instructions are read by the model before it calls anything, so they carry the two rules
     * that are otherwise learned by being refused: a project is named by its key, and an issue key is
     * never invented.
     */
    @Bean
    public McpToolServer mcpToolServer(
            ToolDispatcher dispatcher, @Value("${tessera.version:0.1.0}") String version) {

        return new McpToolServer(dispatcher, "tessera", version,
                "Tessera is an issue tracker: projects hold issues, issues move through a workflow, and "
              + "a project may plan in sprints. Start with projects_list — every other action takes a "
              + "project key in its 'scope' argument, and a project this person does not belong to does "
              + "not appear and cannot be named. Issue keys look like TES-42 and are never invented: "
              + "find one with issues_search or issues_list. A status is not a field — move an issue "
              + "with issues_transition, and read the refusal when it is refused, because it lists the "
              + "moves that would work.");
    }

    /**
     * The transport, as a servlet.
     *
     * <p>⚠️ <strong>The mapper is resolved here rather than left to the transport's own default.</strong>
     * {@link McpJsonDefaults#getMapper()} is a {@code ServiceLoader} lookup, and a lookup that finds
     * nothing fails at the first call rather than at startup — a protocol that accepts a connection and
     * then cannot answer it. Asking for it while building the bean moves that failure to the boot, where
     * somebody is watching.
     */
    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(McpEndpoint.PATH)
                .build();
    }

    /**
     * The server, bound to the transport.
     *
     * <p>⚠️ It has to be a bean rather than a local: {@code serving} starts the session factory, and a
     * server nothing holds is a server whose lifecycle nothing closes. Spring closing it on shutdown is
     * the whole reason this line exists.
     */
    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpSyncServer(
            McpToolServer tools, HttpServletStreamableServerTransportProvider transport) {

        return tools.serving(transport);
    }

    /**
     * ⚠️ <strong>Registered under the same constant the security rule would use.</strong> A servlet
     * mapped somewhere the filter chain does not cover is the protocol served outside its own security
     * boundary — reachable, unauthenticated, and wrong from neither side alone.
     *
     * <p>{@code loadOnStartup} so a first connection does not pay for initialisation, and because a
     * servlet that fails to initialise should fail at boot where somebody is watching.
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {

        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transport, McpEndpoint.ALL_PATTERN);

        registration.setName("mcp");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);

        return registration;
    }
}
