package net.innoventa.tessera.dto.configuration;

/**
 * A workflow as it now is, and what the change did to the boards downstream of it.
 *
 * <p>⚠️ The impact travels <em>with</em> the change rather than behind a second request nobody makes.
 * A workflow is shared, so adding an edge quietly alters what every project on it can do; a response
 * that said only "saved" would leave the consequence to be discovered by whoever eventually noticed
 * their board had gone quiet.
 */
public record WorkflowChangeResponse(WorkflowResponse workflow, WorkflowBoardImpact boardImpact) {
}
