package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;

/**
 * What a new project starts on.
 *
 * <p>The first two are required — there is no "no scheme" a project can be created with. ⚠️ The third
 * is nullable on purpose: "new projects do not estimate" is a real answer, and a scale named None would
 * be a worse way to say it.
 */
public record InstanceDefaultsRequest(
    @NotBlank String defaultIssueTypeSchemeId,
    @NotBlank String defaultWorkflowSchemeId,
    String defaultEstimationSchemeId
) {
}
