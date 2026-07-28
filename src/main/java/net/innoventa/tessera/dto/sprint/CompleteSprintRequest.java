package net.innoventa.tessera.dto.sprint;

import jakarta.validation.constraints.NotNull;
import net.innoventa.tessera.domain.IncompleteIssueDestination;

/**
 * Close a running sprint, saying explicitly where its unfinished work goes. There is no default: the
 * tool does not silently decide someone's re-plan, so {@code moveIncompleteTo} is required and
 * {@code targetSprintId} must accompany {@link IncompleteIssueDestination#SPRINT}.
 * <p>
 * A named target must be a {@code FUTURE} sprint of the same project; anything else — closed, running,
 * unknown, or belonging to another project — is refused with a {@code 409} rather than silently
 * corrected, because a bad target means the caller is closing the wrong thing.
 * <p>
 * "Unfinished" is {@code resolution IS NULL} (ADR-0004) and never a status name. {@code completedAt} is
 * not here: like {@code startedAt}, it is stamped server-side at the moment the request lands.
 */
public record CompleteSprintRequest(
    @NotNull IncompleteIssueDestination moveIncompleteTo,
    String targetSprintId
) {
}
