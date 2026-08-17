package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentAdmission;
import org.jmouse.ai.agent.AgentAuthority;
import org.jmouse.ai.agent.AgentConnection;
import org.jmouse.ai.agent.AgentConnections;
import org.jmouse.ai.agent.AgentDirectory;
import org.jmouse.ai.agent.AgentEnrolment;
import org.jmouse.ai.mcp.authorization.server.ApprovingSubject;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The one place a credential that reaches {@code /api/mcp} is minted, renewed, or ended.
 *
 * <h2>⚠️ Tessera signs this itself, and that is the whole design</h2>
 *
 * <p>Identity mints every token a browser uses, and nothing about that changes. What it cannot mint is a
 * credential <em>confined to one endpoint</em>: an audience is a service, not a route, so a token good
 * for the protocol would equally be good for every REST call. So a protocol credential is Tessera's own,
 * signed with a secret only Tessera holds. The protocol filter chain validates HS256 against that
 * secret; every other route validates RS256 against Identity's JWKS. Neither decoder can be made to
 * accept the other's token, so <em>"this credential works nowhere else"</em> is a signature that does not
 * verify rather than a check somebody could forget to write.
 *
 * <h2>⚠️ What changed: it acts as an AGENT that acts for a person, not as the person</h2>
 *
 * <p>It used to act as the person outright, and the cost was that nothing could say afterwards that an
 * agent had been involved: a comment written through the protocol was indistinguishable from one typed
 * into the browser, there was nothing to grant a narrower permission to, and there was nothing to switch
 * off short of ending every connection.
 *
 * <p>So the rows are {@code jmouse-ai}'s two now — an {@link Agent} is the persona, an
 * {@link AgentConnection} is one client holding a credential for it, and one agent has many. What that
 * buys, in the order somebody notices it:
 *
 * <ul>
 *   <li>Every record made through the protocol can name the agent that made it.
 *   <li>An agent can be switched off, or narrowed to less than its owner holds, without ending
 *       connections — {@link AgentAuthority}.
 *   <li>Two clients of the same person are two connections under one persona, so revoking a laptop does
 *       not sign out a desktop.
 * </ul>
 *
 * <p><strong>A new agent is {@link AgentAuthority#INHERITED}</strong>, which is exactly the previous
 * behaviour: it can do what its owner could have done by hand, and follows them into new projects with
 * nothing to go stale. Narrowing it is then an act somebody takes, rather than the default that silently
 * refuses every call.
 *
 * <p>⚠️ <strong>The {@code sub} claim is still the member's Identity subject</strong>, so
 * {@code MemberAuthoritiesConverter} and {@code CurrentMemberSubject} resolve a browser and a protocol
 * caller identically. {@code name} and {@code email} are copied on for the same reason as before —
 * {@code MemberService.resolveMember} refreshes a member's cached claims from whatever token arrives, and
 * a token without them would quietly rename the person to their own subject identifier.
 *
 * <p>⚠️ <strong>A self-contained token cannot be taken back, so it names both rows.</strong> {@code cid}
 * is the connection and {@code aid} is the agent, checked live on every call by {@link #admit}. Without
 * them, revoking would mean waiting out the access token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpCredentialService {

    /** Names the connection an access token was issued against, so revocation can reach it. */
    public static final String CREDENTIAL_CLAIM = "cid";

    /** And the agent it acts as, so provenance and the ceiling do not cost a second lookup. */
    public static final String AGENT_CLAIM = "aid";

    /** What the credential is for, in the one word the discovery documents also publish. */
    public static final String SCOPE_CLAIM = "scope";

    /** ⚠️ Read by {@code MemberService} to keep a member's cached name and email fresh. */
    private static final String NAME_CLAIM  = "name";
    private static final String EMAIL_CLAIM = "email";

    /** Which client holds it, so a log line and a connections screen can name one. */
    private static final String CLIENT_CLAIM = "client";

    private static final int          REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM              = new SecureRandom();

    /**
     * How stale a "last used" stamp is allowed to get. ⚠️ Not zero: every protocol call would otherwise
     * be a write, and a tool call is a read of the tracker rather than of this table.
     */
    private static final Duration USAGE_STAMP_INTERVAL = Duration.ofMinutes(5);

    private final AgentDirectory           agents;
    private final MemberRepository         members;
    private final AgentConnections         connections;
    private final JwtEncoder               mcpTokenEncoder;
    private final McpAuthorizationSettings settings;

    /**
     * A credential pair, as the token endpoint answers it.
     *
     * @param refreshToken ⚠️ returned once and never recoverable — only its digest is stored
     */
    public record IssuedCredential(String accessToken, String refreshToken, long expiresIn) {
    }

    /** An agent and the connection a call arrived over — the pair every check here needs. */
    public record ActingAgent(Agent agent, AgentConnection connection) {
    }

    /**
     * Records a new connection against the agent a person chose, and answers with the pair it is worth.
     *
     * <p>⚠️ <strong>Which agent that is, is the person's answer now and no longer this class's guess.</strong>
     * It used to be derived here — three private methods that matched a registration, then a name, then
     * created one — because the consent screen offered nothing to choose between. It offers the agents
     * now, so the guess is only made for the choice that says <em>a new one</em>, and it is made by
     * {@link AgentEnrolment}, which the other product reads too.
     *
     * <p>⚠️ <strong>Standing is checked here, at redemption, and not only at approval.</strong> Those are
     * two requests minutes apart, and the only party whose standing matters is the one that actually
     * turned up — an agent switched off in between gets a refusal rather than a credential.
     */
    public IssuedCredential issueFor(Agent agent, String clientName, String clientId) {
        AgentAdmission.refusalFor(agent, null, Instant.now()).ifPresent(refusal -> {
            throw new McpAuthorizationException(refusal);
        });

        // ⚠️ The owner is read off the agent rather than carried in. The approval names an agent, and a
        // second party naming its owner would be the same fact written twice — with a window in which a
        // client could be handed a credential for somebody else's persona.
        Member member = members.findById(agent.ownerReference())
                .orElseThrow(() -> new McpAuthorizationException(
                        "The member this agent acts for no longer exists. Nothing was changed."));

        String refreshToken = randomRefreshToken();

        AgentConnection connection = connections.open(
                agent.id(), clientName, clientId,
                refreshToken, Instant.now().plus(settings.refreshTokenLifetime()));

        log.info("Opened MCP connection {} for agent {} ('{}') acting for member {}",
                connection.id(), agent.id(), agent.name(), member.getId());

        return new IssuedCredential(
                accessTokenFor(member, agent, connection), refreshToken, expiresInSeconds());
    }

    /**
     * The agent an approval named, or the one it asked to have made.
     *
     * <p>⚠️ <strong>Created at redemption and not at approval</strong>, which is why the owner travels in
     * the reference rather than being read off a session — there is no session here. An approval nobody
     * comes back for therefore leaves no agent behind, and the row that does get written is one a client
     * is about to hold a credential for.
     *
     * <p>⚠️ <strong>The reference is not trusted for naming an agent.</strong> The shared flow re-reads
     * what this member may authorize and refuses anything outside it before this is ever called, which is
     * what stands between a reference in a request body and somebody else's persona.
     */
    public Agent approvedAgent(String subjectReference, String clientName, String clientId) {
        return ApprovingSubject.ownerOfNewSubject(subjectReference)
                .map(ownerReference -> AgentEnrolment.agentFor(
                        agents, connections, ownerReference, clientName, clientId))
                .orElseGet(() -> agents.find(subjectReference).orElseThrow(() ->
                        new McpAuthorizationException(
                                "The agent this code was approved for no longer exists.")));
    }

    /**
     * Renews a connection, replacing the refresh token as it goes.
     *
     * <p>⚠️ <strong>Rotation, and the renewal window slides.</strong> A refresh token that stayed the
     * same would be a long-lived secret travelling on every renewal; a window that did not slide would
     * end a connection somebody uses daily, on a date nobody chose. The consequence to know is that a
     * refresh token spent twice fails the second time — which is what makes a stolen one visible.
     */
    public IssuedCredential renew(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new McpAuthorizationException("A refresh_token is required to renew a credential.");
        }

        AgentConnection presented = connections.byRefreshToken(presentedRefreshToken)
                .orElseThrow(() -> new McpAuthorizationException(
                        "This refresh token is unknown or has already been replaced. Authorize again to "
                      + "reconnect."));

        Agent agent = agents.find(presented.agentId()).orElse(null);

        // The same four-part question the protocol filter asks on every call, asked here too: an
        // approval and a renewal are different requests, and a connection ended in between must not
        // hand out a fresh month of access.
        AgentAdmission.refusalFor(agent, presented, Instant.now()).ifPresent(refusal -> {
            throw new McpAuthorizationException(refusal);
        });

        // ⚠️ The owner is read off the agent rather than carried in. A renewal arrives with nothing but
        // a refresh token — there is no session behind it and no caller to ask — so the only party who
        // knows whose credential this is, is the row.
        Member member = members.findById(agent.ownerReference())
                .orElseThrow(() -> new McpAuthorizationException(
                        "The member this connection acts for no longer exists. Nothing was changed."));

        String replacement = randomRefreshToken();

        AgentConnection renewed = connections.rotate(
                presented.id(), replacement, Instant.now().plus(settings.refreshTokenLifetime()));

        return new IssuedCredential(
                accessTokenFor(member, agent, renewed), replacement, expiresInSeconds());
    }

    /**
     * Whether a call presenting these two identifiers may proceed, and why not where it may not.
     *
     * <p>⚠️ <strong>Four rows, four sentences, one library decision.</strong> The agent can be gone, the
     * agent can be switched off, the connection can be revoked, and the connection can have expired —
     * and {@link AgentAdmission} is where those are decided so that this product and the next refuse
     * identically. Answering with the sentence rather than an exception is what lets the protocol filter
     * render an OAuth error while a tool call renders a refusal.
     */
    public Optional<String> admit(String agentId, String connectionId) {
        Agent           agent      = agents.find(agentId).orElse(null);
        AgentConnection connection = connections.find(connectionId).orElse(null);

        if (connection == null) {
            return Optional.of("The connection this credential belongs to no longer exists. Authorize "
                             + "the client again to reconnect.");
        }

        return AgentAdmission.refusalFor(agent, connection, Instant.now());
    }

    /** The agent and connection behind a call, for whoever needs the rows rather than a verdict. */
    public Optional<ActingAgent> actingAgent(String agentId, String connectionId) {
        return agents.find(agentId).flatMap(agent -> connections.find(connectionId)
                .map(connection -> new ActingAgent(agent, connection)));
    }

    /**
     * Every connection a person holds, across all of their agents, newest first.
     *
     * <p>Paired with its agent rather than listed bare: a connection on its own cannot answer <em>what
     * may this thing do</em>, and a screen offering a revoke button with no reason to press one is a
     * screen nobody can act on.
     */
    public List<ActingAgent> connectionsOf(String memberId) {
        return agents.ownedBy(memberId).stream()
                .flatMap(agent -> connections.of(agent.id()).stream()
                        .map(connection -> new ActingAgent(agent, connection)))
                .sorted(Comparator.comparing(
                        (ActingAgent acting) -> acting.connection().issuedAt()).reversed())
                .toList();
    }

    /**
     * Ends a connection.
     *
     * <p>Scoped to the member on purpose: a connection identifier is not a secret, and the person who
     * approved a connection is the person who may end it. ⚠️ The ownership check walks connection →
     * agent → owner, because the connection itself does not know whose it is — that is the agent's fact
     * and storing it twice is how the two drift.
     */
    public void revoke(String connectionId, String memberId) {
        AgentConnection connection = connections.find(connectionId)
                .filter(candidate -> isOwnedBy(candidate, memberId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No connection of yours has the id '" + connectionId + "'"));

        if (connection.isRevoked()) {
            return;
        }

        connections.revoke(connectionId);
        log.info("Revoked MCP connection {} for member {}", connectionId, memberId);
    }

    /**
     * Records that a connection was used, at most once every {@link #USAGE_STAMP_INTERVAL}.
     *
     * <p>The agent is stamped too, on the same schedule and for a different question: the connection's
     * stamp answers <em>which of these clients can I safely revoke</em>, and the agent's answers
     * <em>is this persona still in use at all</em>.
     */
    public void noteUsage(String agentId, String connectionId) {
        connections.find(connectionId)
                .filter(connection -> isStampStale(connection.lastUsedAt()))
                .ifPresent(connection -> {
                    Instant now = Instant.now();

                    connections.stampUsed(connectionId, now);
                    agents.stampActive(agentId, now);
                });
    }

    // ── Internal ──────────────────────────────────────────────────────────────────

    private boolean isOwnedBy(AgentConnection connection, String memberId) {
        return agents.find(connection.agentId())
                .map(agent -> agent.ownerReference().equals(memberId))
                .orElse(false);
    }

    private String accessTokenFor(Member member, Agent agent, AgentConnection connection) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.resourceUrl())
                .audience(List.of(settings.audience()))
                .subject(member.getSubject())
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenLifetime()))
                .claim(SCOPE_CLAIM,      AuthorizationRoutes.SCOPE)
                .claim(CREDENTIAL_CLAIM, connection.id())
                .claim(AGENT_CLAIM,      agent.id())
                .claim(CLIENT_CLAIM,     connection.clientName());

        // ⚠️ Skipped when absent rather than passed through: JwtClaimsSet refuses a null value outright,
        // and a member with no email is ordinary — Identity's subject is not always one. Found by minting
        // for exactly such a member, which failed the whole token exchange with "value cannot be null".
        claimIfPresent(claims, NAME_CLAIM,  member.getDisplayName());
        claimIfPresent(claims, EMAIL_CLAIM, member.getEmail());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return mcpTokenEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static void claimIfPresent(JwtClaimsSet.Builder claims, String name, String value) {
        if (value != null && !value.isBlank()) {
            claims.claim(name, value);
        }
    }

    private long expiresInSeconds() {
        return settings.accessTokenLifetime().toSeconds();
    }

    private boolean isStampStale(Instant lastUsedAt) {
        return lastUsedAt == null || lastUsedAt.isBefore(Instant.now().minus(USAGE_STAMP_INTERVAL));
    }

    private static String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
