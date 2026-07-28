package net.innoventa.tessera.dto.activity;

/** One field's change within a history event — point-in-time display strings (ADR-0007, ticket 08). */
public record ActivityLogItemResponse(
    String field,
    String oldValue,
    String newValue
) {
}
