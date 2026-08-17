package net.innoventa.tessera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * The key of an {@link EntityCategory}: the polymorphic ({@code entityType}, {@code entityId}) pair.
 *
 * <p>⚠️ <strong>It is the key rather than a unique index beside a surrogate one</strong>, because it is
 * where the rule "one category per entity" is written. Folder semantics: a page is in one place. Storing
 * a generated id here and enforcing the pair with an index would leave the same rule true and make it
 * look optional.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EntityCategoryId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 32, nullable = false)
    private CategoryEntityType entityType;

    @Column(name = "entity_id", length = 36, nullable = false)
    private String entityId;

    public static EntityCategoryId of(CategoryEntityType entityType, String entityId) {
        return new EntityCategoryId(entityType, entityId);
    }

}
