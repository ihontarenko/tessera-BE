package net.innoventa.tessera.dto.project;

import java.util.List;

/**
 * What the next issue key would look like under a format nobody has saved yet.
 *
 * <p>⚠️ <strong>Computed from the project's real next sequence, not from a made-up 42.</strong> The
 * whole question a person is asking is "what will my keys look like", and an example built from a
 * number the project is not on answers a different one.
 *
 * @param existingKey a key the project already has, or null where it has no issues — shown beside the
 *                    preview so the divergence is visible <em>before</em> the change rather than
 *                    discovered in a list afterwards. Existing keys are never regenerated.
 */
public record IssueKeyPreview(String nextKey, String existingKey, List<Format> formats) {

    /** One format a project may choose, with what it looks like. */
    public record Format(String name, String example) {
    }

}
