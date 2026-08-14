package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;

/** What a new project starts on. Both are required — there is no "no scheme" a project can be created with. */
public record InstanceDefaultsRequest(
    @NotBlank String defaultIssueTypeSchemeId,
    @NotBlank String defaultWorkflowSchemeId
) {
}
