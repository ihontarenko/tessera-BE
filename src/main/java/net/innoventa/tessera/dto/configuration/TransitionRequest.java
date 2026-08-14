package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One edge of a workflow.
 *
 * <p>⚠️ "Add a status to this workflow" is this request with that status as {@code toStatusId}. A
 * workflow's statuses are derived from its transitions — there is no {@code workflow_statuses} table
 * (ADR-0005) — so membership is not a separate thing that could be edited separately, and an editor that
 * pretended otherwise would be offering a control with nothing behind it.
 *
 * @param fromStatusId null makes this the create transition, and a workflow has exactly one
 */
public record TransitionRequest(
    @NotBlank @Size(max = 64) String name,
    String fromStatusId,
    @NotBlank String toStatusId
) {
}
