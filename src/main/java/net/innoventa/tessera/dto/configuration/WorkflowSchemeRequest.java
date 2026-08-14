package net.innoventa.tessera.dto.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A whole workflow scheme, sent as one — see {@link IssueTypeSchemeRequest} for why whole.
 *
 * @param mappings the per-type overrides; a type with no entry here runs the default workflow, and an
 *                 empty list is a perfectly ordinary scheme where every type does
 */
public record WorkflowSchemeRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description,
    @NotBlank String defaultWorkflowId,
    @Valid List<Mapping> mappings
) {

    public record Mapping(@NotBlank String issueTypeId, @NotBlank String workflowId) {
    }

}
