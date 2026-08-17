package net.innoventa.tessera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A folder in a project's tree — and deliberately a folder of <em>nothing in particular</em> (TSSR-15).
 *
 * <p>The tree knows nothing about what is filed into it. What is filed where lives in
 * {@link EntityCategory}, keyed by a ({@link CategoryEntityType}, id) pair, so a second kind of content
 * costs a constant on that enum rather than a migration of anything here.
 *
 * <p>⚠️ <strong>This is the only hierarchy in the wiki</strong> (TSSR-5). {@link WikiPage} carries no
 * parent of its own: two hierarchies side by side leave "where does this page actually live" with no
 * good answer. A page is a leaf here, and moving it is re-filing it.
 *
 * <p>⚠️ <strong>A plain {@code parentId}, not a nested set.</strong> Innoventa's categories carry
 * {@code treeLeft}/{@code treeRight} beside the pointer, which buys the whole subtree in one statement
 * and costs a renumbering on every move. A project's wiki has tens of categories, so the subtree is
 * walked in memory by {@code CategoryTree} instead. The nested set can be added later without changing a
 * signature, which is the argument for not adding it now.
 *
 * <p>The slug is unique per <em>project</em> rather than per parent — see the migration for why the
 * obvious constraint does not work at the root, where {@code parentId} is null.
 */
@Entity
@Table(
    name = "categories",
    uniqueConstraints = @UniqueConstraint(name = "uq_categories_slug", columnNames = {"project_id", "slug"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Category {

    /**
     * How deep the tree may go, counting the root as level one.
     *
     * <p>Not a storage limit — nothing in the schema cares. It is a readability limit: a sidebar tree
     * indented past four levels stops being navigable, and a person who needs a fifth almost always
     * wants a second top-level section instead.
     */
    public static final int MAXIMUM_DEPTH = 4;

    public static final int MAXIMUM_NAME_LENGTH = 128;

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** Null at the root. A category never leaves the project it was made in, so this is always a sibling's. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    /** Derived from the name on creation and on rename, and unique across the project. */
    @Column(nullable = false, length = 128)
    private String slug;

    /** Position among siblings. Gaps are tolerated; only the order of the values is read. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
