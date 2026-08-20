package net.innoventa.tessera.dto.project;

/**
 * What a rekey did — the project as it now stands, and how much of it moved.
 *
 * <p>⚠️ <strong>{@code rewrittenIssues} is reported rather than left to be inferred.</strong> The
 * screen that asked for this has just told somebody every old link is dead; the number is how they find
 * out how much is out there, and it is the only moment anything counts it.
 *
 * @param project          the project, with its new key
 * @param previousKey      what it was called until this call — the prefix every stale link still carries
 * @param rewrittenIssues  how many issue keys were rewritten
 */
public record RekeyProjectResponse(ProjectResponse project, String previousKey, int rewrittenIssues) {
}
