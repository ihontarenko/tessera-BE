package net.innoventa.tessera.dto.issue;

/**
 * Set (or clear) an issue's parent (ticket 10). A null {@code parentId} detaches the issue. The parent
 * must be strictly higher in {@code hierarchyLevel} (else 409) and in the same project.
 */
public record SetParentRequest(
    String parentId
) {
}
