package net.innoventa.tessera.dto.configuration;

public record TransitionResponse(
    String id,
    String name,
    String fromStatusId,
    String toStatusId
) {
}
