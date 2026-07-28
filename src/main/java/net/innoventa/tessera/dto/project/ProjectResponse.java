package net.innoventa.tessera.dto.project;

import net.innoventa.tessera.domain.ProjectType;
import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A project as the caller sees it, including {@code myPermissions} — the caller's effective permission
 * names in this project — so the UI can hide or disable actions without a second round-trip.
 */
public record ProjectResponse(
    String id,
    String key,
    String name,
    ProjectType type,
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
