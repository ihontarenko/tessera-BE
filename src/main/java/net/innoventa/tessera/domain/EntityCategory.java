package net.innoventa.tessera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Files one thing into one {@link Category}.
 *
 * <p>The membership table, kept apart from both sides on purpose: the tree does not know what it holds
 * and the page does not know it is in a tree, so either can be read, listed or moved without the other
 * being loaded. A row's absence is a meaningful state — a page filed nowhere is <em>uncategorised</em>,
 * which is where every page starts.
 */
@Entity
@Table(name = "entity_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class EntityCategory {

    @EmbeddedId
    private EntityCategoryId id;

    @Column(name = "category_id", length = 36, nullable = false)
    private String categoryId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
