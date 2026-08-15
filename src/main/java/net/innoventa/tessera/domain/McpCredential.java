package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One agent's standing permission to act as one person, through the Model Context Protocol and nowhere
 * else.
 *
 * <p>⚠️ <strong>What this row is, and what it is not.</strong> It is not the access token — that is a
 * short-lived JWT Tessera signs and never stores. It is the <em>connection</em>: the approval a person
 * gave, the refresh credential that renews it, and the switch that ends it. An access token names this
 * row in its {@code cid} claim, so revoking here refuses a token that has not expired yet, which is the
 * only reason a self-contained credential is safe to hand out at all.
 *
 * <p>⚠️ <strong>The refresh token is stored as a digest, never as itself.</strong> A readable refresh
 * token in a table is a credential anybody with a database session can use as its holder; a digest is
 * something they can only compare against. It is a SHA-256 hex string because the value it hashes is 32
 * random bytes — there is nothing to slow a guesser down for, so a password hash here would be cost
 * without benefit.
 *
 * <p>The credential belongs to a {@link Member} rather than to a subject string, like every other
 * reference to a person in Tessera: what the token carries is Identity's subject, but what the tracker
 * authorizes is always the local row.
 */
@Entity
@Table(name = "mcp_credentials",
       uniqueConstraints = @UniqueConstraint(name = "uq_mcp_credentials_refresh",
                                             columnNames = "refresh_token_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class McpCredential {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** Whose permissions the agent acts with. Tessera has no service accounts — an agent is a person. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_mcp_credentials_member"))
    private Member member;

    /** What the client called itself when it registered — a claim it made, shown as one. */
    @Column(name = "client_name", nullable = false, length = 255)
    private String clientName;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    /** Stamped as the credential is used, so a connection nobody has touched in months is visible. */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** Set once and never unset: a connection somebody ended does not come back. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    void onCreate() {
        issuedAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Whether the renewal half is still usable — the access token's own expiry is in its claims. */
    public boolean canBeRenewed(LocalDateTime now) {
        return !isRevoked() && now.isBefore(refreshExpiresAt);
    }
}
