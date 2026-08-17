package net.innoventa.tessera.dto.wiki;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * A page as a list shows it: enough to decide whether to open it, and no prose.
 *
 * <p>⚠️ <strong>The Markdown is deliberately absent.</strong> A wiki index that carried every document
 * would ship the whole wiki to draw a sidebar — {@link #excerpt} is what the mirror column exists for.
 *
 * <p>{@code categoryId} is null for a page filed nowhere, which is where every page starts and where
 * many stay. That is a state rather than a missing value (TSSR-15).
 */
public record WikiPageSummary(
    String id,
    String title,
    String slug,
    String excerpt,
    String categoryId,
    MemberSummary author,
    MemberSummary updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
