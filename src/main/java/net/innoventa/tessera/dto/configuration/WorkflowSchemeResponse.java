package net.innoventa.tessera.dto.configuration;

import java.util.List;

public record WorkflowSchemeResponse(
    String id,
    String name,
    String description,
    String defaultWorkflowId,
    List<Mapping> mappings
) {

    /** One issue-type → workflow override within the scheme. */
    public record Mapping(String issueTypeId, String workflowId) {
    }

}
