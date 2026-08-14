package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A priority as an administrator states it.
 *
 * <p>No {@code sequence}: the order a picker shows is a property of the <em>catalog</em>, not of any one
 * row, and letting each row carry its own number is how two rows come to claim position 3. A new
 * priority is appended and the order is changed as a whole through the reorder route.
 */
public record PriorityRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 16) String color
) {
}
