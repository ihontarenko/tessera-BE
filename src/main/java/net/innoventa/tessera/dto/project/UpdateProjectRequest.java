package net.innoventa.tessera.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edit a project's name, lead and schemes (requires {@code ADMINISTER_PROJECT}).
 *
 * <p>⚠️ <strong>The key is not here, and that is deliberate rather than left over.</strong> Changing it
 * rewrites every issue key in the project and kills every link anybody wrote — so it is
 * {@code POST /api/projects/{id}/key}, behind a typed confirmation, in the settings screen's danger
 * zone. A field on this request would make it something a screen could do while saving a name.
 */
public record UpdateProjectRequest(
    @NotBlank @Size(max = 128) String name,
    /** ⚠️ Blank clears it — no icon is a project's ordinary state, not a missing value (TSSR-7). */
    @Size(max = 16) String icon,
    @NotNull String leadMemberId,
    @NotNull String issueTypeSchemeId,
    @NotNull String workflowSchemeId,
    /** ⚠️ Nullable, and null means "this project does not estimate" — not a scale named None. */
    String estimationSchemeId,
    /** One of {@code IssueKeyFormat}; changing it affects issues raised afterwards and nothing else. */
    @NotNull String keyStrategy,
    /** ⚠️ Read only by {@code CUSTOM}, and validated to contain {@code ${sequence}} when it is. */
    String keyPattern
,

    /**
     * Which Kiwi section this project's wiki lives in — an identifier from ANOTHER service, or null
     * where nobody has chosen one (KW-10; KW-1 §3).
     *
     * <p>⚠️ <strong>Not validated here, and it cannot be.</strong> The category lives in Kiwi's
     * database; this service has no way to ask whether it exists without becoming a client of Kiwi,
     * which is precisely the backend-to-backend call KW-1 §1 refuses. The browser picked it from
     * Kiwi's own tree, and a root that stops resolving is a state the wiki tab handles.
     */
    String kiwiRootCategoryId
) {
}
