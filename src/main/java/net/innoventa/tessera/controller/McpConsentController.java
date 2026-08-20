package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.ai.McpEndpoint;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import org.jmouse.ai.mcp.authorization.server.AgentCredentials;
import net.innoventa.tessera.dto.agent.ConnectionInfoResponse;
import net.innoventa.tessera.dto.agent.ConnectionResponse;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The connections a person holds, and ending one of them.
 *
 * <p>⚠️ <strong>Reviewing and approving are not here any more.</strong> They are the same two requests in
 * both products that host this flow, so they moved into {@code jmouse-ai-mcp-authorization} along with
 * the screen itself — see {@code McpConsentEndpoints}. What stayed is what is genuinely Tessera's:
 * a connection is a row in {@code mcp_credentials}, and only this product has one.
 *
 * <p>A bare {@code @RequiresAccess} throughout — a signed-in caller and nothing more. There is no
 * permission to invent here, and inventing one would be wrong: a credential grants exactly what the
 * approver already holds, so anybody who may use Tessera may connect a client to their own account, and
 * nobody can connect one to somebody else's.
 *
 * <p>⚠️ <strong>Deliberately not reachable with a protocol credential.</strong> The endpoint an agent
 * speaks to is on its own filter chain and these routes are not on it, so a connected client cannot use
 * its credential to revoke the person's other connections.
 */
@RestController
@RequestMapping(McpConsentController.BASE)
@RequiredArgsConstructor
@RequiresAccess
public class McpConsentController {

    /**
     * ⚠️ Spelled out rather than derived, because {@code @RequestMapping} needs it before any bean
     * exists. It is the same prefix {@code jmouse.mcp.authorization.protocol-prefix} states, and the two
     * have to agree — they are the same screen's two halves.
     */
    static final String BASE = "/api/agents/authorization";

    private final AgentCredentials     credentialService;
    private final McpAuthorizationProperties settings;
    private final MemberService            memberService;

    /** Where to point a client — the address this installation actually serves the protocol on. */
    @GetMapping("/connection-info")
    public ConnectionInfoResponse connectionInfo() {
        return new ConnectionInfoResponse(settings.apiUrl(McpEndpoint.PATH));
    }

    /** The connections this person has approved, newest first, each with the agent it belongs to. */
    @GetMapping("/connections")
    public List<ConnectionResponse> connections(@AuthenticationPrincipal Jwt token) {
        return credentialService.connectionsOf(memberService.resolveMember(token).getId()).stream()
                .map(acting -> ConnectionResponse.from(acting.connection(), acting.agent()))
                .toList();
    }

    /** Ends one of them. Immediate: an access token naming it stops being honoured on its next call. */
    @DeleteMapping("/connections/{connectionId}")
    public void revoke(@PathVariable String connectionId, @AuthenticationPrincipal Jwt token) {
        credentialService.revoke(connectionId, memberService.resolveMember(token).getId());
    }
}
