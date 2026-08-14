package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A kind of relationship between two issues.
 *
 * <p>Both labels are required, and a symmetric type says the same word twice rather than leaving one
 * blank — the direction is still stored, the labels simply match. A blank inward label would render as
 * an empty half of every link that used it.
 */
public record LinkTypeRequest(
    @NotBlank @Size(max = 64) String name,
    @NotBlank @Size(max = 64) String outwardLabel,
    @NotBlank @Size(max = 64) String inwardLabel
) {
}
