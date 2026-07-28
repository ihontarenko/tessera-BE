package net.innoventa.tessera.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Add or edit a comment (ticket 13) — just its body. Author and timestamps are set by the service. */
public record SaveCommentRequest(
    @NotBlank @Size(max = 4000) String body
) {
}
