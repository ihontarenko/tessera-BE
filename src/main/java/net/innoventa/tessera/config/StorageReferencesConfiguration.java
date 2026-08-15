package net.innoventa.tessera.config;

import jakarta.persistence.EntityManager;
import org.jmouse.storage.jpa.JpaStoredFileReferences;
import org.jmouse.storage.jpa.StoredFileReferences;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares which of Tessera's tables point at the stored-file registry.
 *
 * <p>Exactly one, because Tessera stores exactly one kind of file. The sweeper takes the union of
 * every declared source and reclaims what appears in none of them, so a product with one binding
 * declares one and a product with three declares three — neither has to know about the other.
 *
 * <p>⚠️ <strong>A query, never a maintained count.</strong> An avatar stops being referenced through
 * paths that never touch the storage layer at all — somebody picks a generated face instead, or clears
 * their picture — and a counter incremented on upload would have to be decremented at each of those.
 * Asking the table cannot drift.
 *
 * <p>⚠️ And it must ask for <strong>every</strong> kind, not only {@code UPLOAD}: the column is what
 * the sweeper needs to see, and a member whose kind is {@code PRESET} has a null there anyway. Filtering
 * on the kind would add a condition that can only ever be wrong later.
 */
@Configuration
public class StorageReferencesConfiguration {

    /** Uploaded member avatars. */
    @Bean
    public StoredFileReferences memberAvatarReferences(EntityManager entityManager) {
        return new JpaStoredFileReferences(
            entityManager, "member avatars",
            "SELECT member.avatarFile.id FROM Member member WHERE member.avatarFile IS NOT NULL");
    }

}
