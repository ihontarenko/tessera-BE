package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new name, for the rows whose name is all there is to change.
 *
 * <p>A transition is its endpoints: {@code In Progress → Done} is that edge, and pointing it somewhere
 * else does not modify it, it replaces it with a different one. So retargeting is remove-then-add, and
 * this request carries nothing but the label.
 */
public record RenameRequest(@NotBlank @Size(max = 64) String name) {
}
