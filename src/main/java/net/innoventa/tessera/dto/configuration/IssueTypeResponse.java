package net.innoventa.tessera.dto.configuration;

public record IssueTypeResponse(
    String id,
    String name,
    int hierarchyLevel,
    String iconKey,
    String description
) {
}
