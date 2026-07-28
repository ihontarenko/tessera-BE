package net.innoventa.tessera.dto.project;

import net.innoventa.tessera.dto.configuration.IssueTypeResponse;

import java.util.List;

/**
 * The issue types a project may actually create, in the order its {@code IssueTypeScheme} lists them,
 * with that scheme's default called out separately so the create dialog can preselect it.
 * <p>
 * The default is an id rather than a flag on each entry: exactly one type holds it, and a single field
 * cannot express "two defaults" the way a per-entry boolean silently can. It always names one of
 * {@code issueTypes}, or is null when the scheme grants nothing at all — a client may preselect it
 * without re-checking that the list contains it.
 */
public record ProjectIssueTypesResponse(
    List<IssueTypeResponse> issueTypes,
    String defaultIssueTypeId
) {
}
