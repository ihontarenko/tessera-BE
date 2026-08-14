package net.innoventa.tessera.dto.configuration;

/**
 * What a new project starts on, with both schemes named as well as identified.
 *
 * <p>The names are here because the Defaults screen shows them and the refusal that protects them says
 * them: "cannot be deleted, it is the default issue-type scheme for new projects" is a sentence about a
 * name, not an identifier.
 */
public record InstanceDefaultsResponse(
    String defaultIssueTypeSchemeId,
    String defaultIssueTypeSchemeName,
    String defaultWorkflowSchemeId,
    String defaultWorkflowSchemeName
) {
}
