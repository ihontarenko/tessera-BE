package net.innoventa.tessera.dto.configuration;

import java.util.List;
import java.util.Map;

/**
 * Which projects are on which scheme, for both kinds at once, plus which two are the instance defaults.
 *
 * <p>⚠️ <strong>The blast radius is shown permanently, not behind a confirmation.</strong> Editing a
 * scheme is editing every project on it, and a dialog that says so only once Delete is pressed tells an
 * administrator after they have decided. The screen shows it while they are deciding.
 *
 * <p>One request rather than one per scheme: the list page needs all of it at once, and a scheme
 * catalog of a dozen would otherwise open with a dozen round trips.
 *
 * @param byIssueTypeScheme projects keyed by issue-type scheme id — ⚠️ a scheme nothing uses is
 *                          <strong>absent rather than an empty list</strong>, so readers default
 * @param byWorkflowScheme  the same for workflow schemes
 */
public record SchemeUsageReport(
    Map<String, List<ProjectReference>> byIssueTypeScheme,
    Map<String, List<ProjectReference>> byWorkflowScheme,
    String defaultIssueTypeSchemeId,
    String defaultWorkflowSchemeId
) {
}
