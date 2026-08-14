package net.innoventa.tessera.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edit a project's name, lead and schemes (requires {@code ADMINISTER_PROJECT}). The key and type are
 * immutable in Phase 1 — a rekey rewrites every issue key and is deliberately deferred (ADR-0003).
 */
public record UpdateProjectRequest(
    @NotBlank @Size(max = 128) String name,
    @NotNull String leadMemberId,
    @NotNull String issueTypeSchemeId,
    @NotNull String workflowSchemeId,
    /** ⚠️ Nullable, and null means "this project does not estimate" — not a scale named None. */
    String estimationSchemeId
) {
}
