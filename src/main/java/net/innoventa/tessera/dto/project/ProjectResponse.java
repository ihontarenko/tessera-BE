package net.innoventa.tessera.dto.project;

import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.configuration.EstimationSchemeResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A project as the caller sees it, including {@code myPermissions} — the caller's effective permission
 * names in this project — so the UI can hide or disable actions without a second round-trip.
 * <p>
 * {@code boardScopeStrategy} rides along for the same reason: it decides whether this project shows a
 * Backlog at all, and — since ADR-0015 dropped the project type — it is also what the interface
 * derives the words "Scrum" and "Kanban" from. Carrying it here keeps the project page from fetching
 * the board just to lay out its tabs or name its style.
 */
public record ProjectResponse(
    String id,
    String key,
    String name,
    /** One emoji, or null — every screen falls back to the shared folder glyph (TSSR-7). */
    String icon,
    BoardScopeStrategy boardScopeStrategy,
    MemberSummary lead,
    SchemeSummary issueTypeScheme,
    SchemeSummary workflowScheme,
    /**
     * How this project estimates, with its options — or null where it does not.
     *
     * ⚠️ <strong>The whole scale travels, not just its name</strong>, because every screen that shows a
     * story-point value needs the (label, weight) pairs to render {@code 8} as {@code XL} and to offer
     * the picker. Null means the story-points control disappears rather than showing an empty select.
     */
    EstimationSchemeResponse estimationScheme,
    String keyStrategy,
    String keyPattern,
    /** Which Kiwi section this project's wiki lives in, or null where nobody has chosen one. */
    String kiwiRootCategoryId,
    List<String> myPermissions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
