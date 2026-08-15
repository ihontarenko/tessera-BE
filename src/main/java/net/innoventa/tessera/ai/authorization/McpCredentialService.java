package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.innoventa.tessera.domain.McpCredential;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.McpCredentialRepository;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one place a credential that reaches {@code /api/mcp} is minted, renewed, or ended.
 *
 * <h2>⚠️ Tessera signs this itself, and that is the whole design</h2>
 *
 * <p>Identity mints every token a browser uses, and nothing about that changes. What it cannot mint is a
 * credential <em>confined to one endpoint</em>: an audience is a service, not a route, so a token good
 * for the protocol would equally be good for every REST call — the gap {@code McpConfiguration} has
 * named since the protocol was first served here. So a protocol credential is Tessera's own, signed with
 * a secret only Tessera holds.
 *
 * <p><strong>The confinement is cryptographic rather than declared.</strong> The protocol filter chain
 * validates HS256 against that secret; every other route validates RS256 against Identity's JWKS.
 * Neither decoder can be made to accept the other's token, so "this credential works nowhere else" is
 * not a claim checked in a filter somebody could forget to write — it is a signature that does not
 * verify.
 *
 * <p><strong>And it acts as a person, not as a robot.</strong> The {@code sub} claim is the approving
 * member's Identity subject, so {@code MemberAuthoritiesConverter}, {@code CurrentMemberSubject} and
 * {@code TesseraCallerResolver} resolve the caller exactly as they do for a browser — the agent can do
 * what that person can do, in the projects that person belongs to, and nothing else. ⚠️ Which is why
 * {@code name} and {@code email} are copied onto the token: {@code MemberService.resolveMember} refreshes
 * a member's cached claims from whatever token arrives, so a token without them would quietly rename the
 * person to their own subject identifier.
 *
 * <p>⚠️ <strong>A self-contained token cannot be taken back, so it names its row.</strong> The {@code cid}
 * claim is the {@link McpCredential} it was issued against, checked live on every call by
 * {@link #isHonoured}. Without it, revoking a connection would mean waiting out the access token — with
 * it, revoking is immediate and the token becomes a string.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpCredentialService {

    /** Names the row an access token was issued against, so revocation can reach it. */
    public static final String CREDENTIAL_CLAIM = "cid";

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

    private final McpCredentialRepository    credentials;
    private final JwtEncoder                 mcpTokenEncoder;
    private final McpAuthorizationSettings   settings;
    private final Supplier<String>           idGenerator;

    /**
     * A credential pair, as the token endpoint answers it.
     *
     * @param accessToken   the confined JWT a client presents on every protocol call
     * @param refreshToken  ⚠️ returned once and never recoverable — only its digest is stored
     * @param expiresIn     the access token's remaining life, in seconds, as OAuth reports it
     */
    public record IssuedCredential(String accessToken, String refreshToken, long expiresIn) {
    }

    /** Records a new connection for a person and answers with the pair it is worth. */
    @Transactional
    public IssuedCredential issueFor(Member member, String clientName) {
        String refreshToken = randomRefreshToken();

        McpCredential credential = credentials.save(McpCredential.builder()
                .id(idGenerator.get())
                .member(member)
                .clientName(clientName)
                .refreshTokenHash(digestOf(refreshToken))
                .refreshExpiresAt(LocalDateTime.now().plus(settings.refreshTokenLifetime()))
                .build());

        log.info("Issued MCP credential {} to '{}' acting as member {}",
                credential.getId(), clientName, member.getId());

        return new IssuedCredential(accessTokenFor(credential), refreshToken, expiresInSeconds());
    }

    /**
     * Renews a connection, replacing the refresh token as it goes.
     *
     * <p>⚠️ <strong>Rotation, and the renewal window slides.</strong> A refresh token that stayed the
     * same would be a long-lived secret travelling on every renewal; a window that did not slide would
     * end a connection somebody uses daily, on a date nobody chose. The consequence to know is that a
     * refresh token spent twice fails the second time — which is what makes a stolen one visible.
     */
    @Transactional
    public IssuedCredential renew(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new McpAuthorizationException("A refresh_token is required to renew a credential.");
        }

        McpCredential credential = credentials.findByRefreshTokenHash(digestOf(presentedRefreshToken))
                .filter(candidate -> candidate.canBeRenewed(LocalDateTime.now()))
                .orElseThrow(() -> new McpAuthorizationException(
                        "This refresh token is unknown, already replaced, expired or revoked. Authorize "
                      + "again to reconnect."));

        String replacement = randomRefreshToken();

        credential.setRefreshTokenHash(digestOf(replacement));
        credential.setRefreshExpiresAt(LocalDateTime.now().plus(settings.refreshTokenLifetime()));
        credential.setLastUsedAt(LocalDateTime.now());

        return new IssuedCredential(accessTokenFor(credential), replacement, expiresInSeconds());
    }

    /** Every connection a person holds, newest first. */
    @Transactional(readOnly = true)
    public List<McpCredential> connectionsOf(String memberId) {
        return credentials.findAllByMemberIdOrderByIssuedAtDesc(memberId);
    }

    /**
     * Ends a connection.
     *
     * <p>Scoped to the member on purpose: a credential id is not a secret, and the person who approved a
     * connection is the person who may end it.
     */
    @Transactional
    public void revoke(String credentialId, String memberId) {
        McpCredential credential = credentials.findById(credentialId)
                .filter(candidate -> candidate.getMember().getId().equals(memberId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No connection of yours has the id '" + credentialId + "'"));

        if (credential.isRevoked()) {
            return;
        }

        credential.setRevokedAt(LocalDateTime.now());
        log.info("Revoked MCP credential {} for member {}", credentialId, memberId);
    }

    /**
     * Whether an access token naming this credential is still honoured — asked on every protocol call.
     *
     * <p>⚠️ <strong>Read-only and outside any transaction of its own</strong>, because it runs while the
     * request is still being authenticated. It answers on a projection rather than an entity for the same
     * reason: there is nowhere here to load a lazy association in.
     */
    @Transactional(readOnly = true)
    public boolean isHonoured(String credentialId) {
        return credentialId != null && credentials.existsByIdAndRevokedAtIsNull(credentialId);
    }

    /** Records that a credential was used, at most once every {@link #USAGE_STAMP_INTERVAL}. */
    @Transactional
    public void noteUsage(String credentialId) {
        credentials.findById(credentialId)
                .filter(credential -> isStampStale(credential.getLastUsedAt()))
                .ifPresent(credential -> credentials.stampLastUsed(credential.getId(), LocalDateTime.now()));
    }

    // ── Internal ──────────────────────────────────────────────────────────────────

    private String accessTokenFor(McpCredential credential) {
        Member  member = credential.getMember();
        Instant now    = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.resourceUrl())
                .audience(List.of(settings.audience()))
                .subject(member.getSubject())
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenLifetime()))
                .claim(SCOPE_CLAIM,      AuthorizationRoutes.SCOPE)
                .claim(CREDENTIAL_CLAIM, credential.getId())
                .claim(CLIENT_CLAIM,     credential.getClientName());

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

    private boolean isStampStale(LocalDateTime lastUsedAt) {
        return lastUsedAt == null || lastUsedAt.isBefore(LocalDateTime.now().minus(USAGE_STAMP_INTERVAL));
    }

    private static String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digestOf(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
