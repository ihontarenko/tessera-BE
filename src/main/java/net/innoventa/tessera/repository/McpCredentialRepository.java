package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.McpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface McpCredentialRepository extends JpaRepository<McpCredential, String> {

    /** The renewal half, looked up by what a client presents rather than by what it is. */
    Optional<McpCredential> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Whether an access token naming this credential is still honoured.
     *
     * <p>⚠️ A projection rather than the entity, because this runs on <strong>every</strong> protocol
     * call while the request is still being authenticated — before any transaction the entity's lazy
     * member could be loaded in.
     */
    boolean existsByIdAndRevokedAtIsNull(String id);

    /** Somebody's connections, newest first, for a screen that lists and revokes them. */
    List<McpCredential> findAllByMemberIdOrderByIssuedAtDesc(String memberId);

    /**
     * Records that a credential was used, without loading it.
     *
     * <p>⚠️ Called at most once every few minutes per credential rather than per call — see
     * {@code McpCredentialService}. "Last used" is worth a write; "used again four seconds later" is
     * not, and a write on the read path of every tool call would be one.
     */
    @Modifying
    @Query("UPDATE McpCredential credential SET credential.lastUsedAt = :usedAt WHERE credential.id = :id")
    void stampLastUsed(@Param("id") String id, @Param("usedAt") LocalDateTime usedAt);
}
