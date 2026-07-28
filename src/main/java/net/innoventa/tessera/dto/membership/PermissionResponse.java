package net.innoventa.tessera.dto.membership;

/** A permission from the catalog — the picker source for setting an override. */
public record PermissionResponse(
    String id,
    String name,
    String description
) {
}
