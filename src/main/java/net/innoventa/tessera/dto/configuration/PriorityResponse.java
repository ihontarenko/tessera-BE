package net.innoventa.tessera.dto.configuration;

public record PriorityResponse(
    String id,
    String name,
    int sequence,
    String color
) {
}
