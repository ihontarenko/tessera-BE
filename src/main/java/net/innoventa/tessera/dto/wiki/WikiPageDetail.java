package net.innoventa.tessera.dto.wiki;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * A page as it is read: the summary's fields plus the document itself.
 *
 * <p>A separate record rather than a nullable {@code contentMarkdown} on {@link WikiPageSummary},
 * because a client holding a summary should not have to ask whether this particular one happens to
 * carry the text. Two shapes, two answers.
 */
public record WikiPageDetail(
    String id,
    String title,
    String slug,
    String contentMarkdown,
    String excerpt,
    String categoryId,
    MemberSummary author,
    MemberSummary updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
