package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Something a comment can be about, as an administrator states it.
 *
 * <p>The icon is a key from a closed list and is refused when it is not one; the colour is any CSS
 * colour and is refused for nothing, the same split {@code IssueTypeRequest} and {@code StatusRequest}
 * already make — a drawing the client has to own, a colour a browser does.
 */
public record CommentTopicRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description,
    @Size(max = 64) String iconKey,
    @Size(max = 16) String color
) {
}
