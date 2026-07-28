package net.innoventa.tessera.dto.sprint;

import java.time.LocalDate;

/**
 * Start a planned sprint. The {@code endDate} is <strong>required</strong> — without it the burndown has
 * no axis and the team has no deadline — but it is refused with a {@code 409} rather than a validation
 * {@code 400}, because "you cannot start a sprint without an end date" is the product rule the client
 * surfaces, not a malformed request.
 * <p>
 * There is deliberately no start date here: {@code startedAt} is stamped server-side at the moment the
 * request lands, never taken from the client.
 */
public record StartSprintRequest(LocalDate endDate) {
}
