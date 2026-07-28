package net.innoventa.tessera.domain;

/**
 * Where a closing sprint's unfinished work goes. The server never guesses this — the completion request
 * carries it explicitly, because "what happens to the four issues we did not finish" is a re-planning
 * decision belonging to whoever is closing the sprint, not a default belonging to the tool.
 * <p>
 * Not persisted anywhere: this is the completion request's discriminator, and the outcome it selects is
 * already a fact in {@link SprintIssue} once the close has run.
 */
public enum IncompleteIssueDestination {

    /** Unfinished work returns to the product backlog — it simply gains no new membership row. */
    BACKLOG,

    /** Unfinished work rolls into a named {@code FUTURE} sprint, gaining a membership row there. */
    SPRINT

}
