package net.innoventa.tessera.dto.configuration;

import java.util.List;

public record IssueTypeSchemeResponse(
    String id,
    String name,
    String description,
    String defaultIssueTypeId,
    List<String> issueTypeIds
) {
}
