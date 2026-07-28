package net.innoventa.tessera.dto.project;

import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.dto.MemberSummary;

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
    BoardScopeStrategy boardScopeStrategy,
    MemberSummary lead,
    SchemeSummary issueTypeScheme,
    SchemeSummary workflowScheme,
    String keyStrategy,
    String keyPattern,
    List<String> myPermissions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
