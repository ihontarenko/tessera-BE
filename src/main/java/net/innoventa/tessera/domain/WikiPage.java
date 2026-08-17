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
 * A Markdown document in a project's wiki (TSSR-16) — the prose a tracker has nowhere else to put: a
 * convention, a runbook, a decision that outlived the ticket that made it.
 *
 * <h2>Where it lives</h2>
 *
 * <p>⚠️ <strong>No parent of its own.</strong> The hierarchy is the {@link Category} tree, and a page is
 * a leaf filed into it through {@link EntityCategory}. Two hierarchies side by side leave "where does
 * this page actually live" with no good answer (TSSR-5), so moving a page is re-filing it and not a
 * column here. A page filed nowhere is <em>uncategorised</em>, which is where every page starts.
 *
 * <h2>Where the text lives</h2>
 *
 * <p><strong>In {@link #contentMarkdown} and nowhere else</strong> — one column, one copy.
 *
 * <p>⚠️ <strong>A deliberate departure from TSSR-16</strong>, which said the canonical bytes should go
 * through jmouse-storage with this column as a synced mirror, the way Innoventa's {@code Page} does. It
 * does not survive contact with this product: {@code tessera.file.upload.profile} is an <em>allowlist</em>
 * of images and office documents, so {@code text/markdown} is refused by {@code UploadPolicy} outright,
 * and the library's {@code CUSTOM} profile does not bind — the only way through would be switching the
 * whole installation to a denylist, and that allowlist is exactly what makes the unauthenticated
 * {@code /api/public/avatars/**} route safe. The size ceiling beside it is an avatar's megabyte.
 *
 * <p>⚠️ And with <strong>no version history</strong> in this pass, the second copy would buy nothing to
 * pay for that: it is rewritten in place on every edit, so it is never a previous version; no read path
 * serves it; and content-addressed deduplication does nothing for text that is unique by definition. The
 * only thing a divergence between the two could produce is a bug.
 *
 * <p>Storage earns its place in the wiki when pages carry <em>attachments and images</em> — bytes that
 * are actually bytes. That is when a stored-file column comes back, pointing at something.
 *
 * <p>⚠️ <strong>Which makes an edit irreversible.</strong> There is one copy of the text and no history
 * behind it, so saving over a page loses what was there. The screen says so rather than leaving it to be
 * discovered.
 */
@Entity
@Table(
    name = "wiki_pages",
    uniqueConstraints = @UniqueConstraint(name = "uq_wiki_pages_slug", columnNames = {"project_id", "slug"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class WikiPage {

    /**
     * The longest page this wiki accepts, in characters.
     *
     * <p>MySQL's {@code MEDIUMTEXT} holds 16 777 215 <strong>bytes</strong> and a utf8mb4 character can
     * take four of them, so 200 000 characters cannot overflow the column whatever alphabet they are
     * written in (200 000 × 4 = 800 000). Derived, not chosen — do not round it up without changing the
     * column type first.
     *
     * <p>⚠️ Twelve times an issue description's ceiling ({@link Issue#MAXIMUM_DESCRIPTION_LENGTH}), and
     * deliberately: that field holds a request and this one holds a manual.
     */
    public static final int MAXIMUM_MARKDOWN_LENGTH = 200_000;

    public static final int MAXIMUM_TITLE_LENGTH = 255;

    /** How much of the prose a listing shows. Sized to two lines in a card, not to a paragraph. */
    public static final int MAXIMUM_EXCERPT_LENGTH = 512;

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = MAXIMUM_TITLE_LENGTH)
    private String title;

    /** Derived from the title, unique within the project — what a link to this page is addressed by. */
    @Column(nullable = false, length = 255)
    private String slug;

    /**
     * ⚠️ Declared {@code TEXT} and created as {@code MEDIUMTEXT} on MySQL — the wider type is in the
     * migration, and {@code columnDefinition} is read only when Hibernate generates DDL, which this
     * application never lets it do ({@code ddl-auto: validate}). The ceiling that actually refuses is
     * {@link #MAXIMUM_MARKDOWN_LENGTH}, enforced by the request DTO, because the column counts bytes and
     * validation counts characters.
     */
    @Column(name = "content_markdown", columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(length = MAXIMUM_EXCERPT_LENGTH)
    private String excerpt;

    /** Who created it. Never reassigned — an edit by somebody else changes {@link #updatedByMemberId}. */
    @Column(name = "author_member_id", nullable = false, length = 36)
    private String authorMemberId;

    @Column(name = "updated_by_member_id", length = 36)
    private String updatedByMemberId;

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
