package net.innoventa.tessera.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Change a project's key, rewriting every issue key in it (requires {@code ADMINISTER_PROJECT}).
 *
 * <p>⚠️ <strong>This is the one project edit that changes an identifier other people hold.</strong>
 * `UpdateProjectRequest` says the key is immutable, and it was — ADR-0003 deferred a rekey rather than
 * ruling it out, and stored the raw {@code sequence} beside the string precisely so that this day would
 * be a string rewrite. It is still not an ordinary edit, which is why it has a request of its own, a
 * confirmation, and a danger zone to live in.
 *
 * @param key          the new key, uppercase and unique instance-wide — the same rule creating one
 *                     obeys, because a key minted here has to be a key that could have been minted then
 * @param confirmation ⚠️ <strong>the project's CURRENT key, typed back.</strong> Not belt-and-braces
 *                     over a dialog: a danger zone whose only guard is in the browser is one HTTP call
 *                     away from not being one. Typing the old key is also the last moment somebody
 *                     reads it, which is the moment to notice they are on the wrong project
 */
public record RekeyProjectRequest(
    @NotBlank @Size(max = 32) @Pattern(
        regexp = "^[A-Z][A-Z0-9]*$",
        message = "must be uppercase letters and digits, starting with a letter") String key,
    @NotBlank String confirmation
) {
}
