package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotBlank;

/**
 * Retyping an existing link (TSSR-40).
 *
 * <p>⚠️ <strong>One field, and that is the design.</strong> A link is {@code (source, target, type)};
 * change either endpoint and it is a different link, so those stay delete-and-create. A record that also
 * carried a target would make this route look like an edit while behaving like a replacement.
 */
public record ChangeIssueLinkTypeRequest(
    @NotBlank String linkTypeId
) {
}
