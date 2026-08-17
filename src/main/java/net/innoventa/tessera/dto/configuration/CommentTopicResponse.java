package net.innoventa.tessera.dto.configuration;

public record CommentTopicResponse(
    String id,
    String name,
    String description,
    /** A key from the closed list, or null — the client draws a generic mark for null. */
    String iconKey,
    /** A CSS colour, or null for the muted default. */
    String color
) {
}
