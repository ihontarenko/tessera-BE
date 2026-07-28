package net.innoventa.tessera.dto.filter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.SavedFilterVisibility;

/**
 * The body for both saving a new filter and rewriting an existing one — the two carry exactly the
 * same fields, and a filter has no lifecycle that would make them diverge.
 * <p>
 * {@code expression} is bounded at the evaluator's own limit so the two never disagree about what is
 * acceptable; that it also <em>parses</em> is checked in the service, where the engine lives.
 */
public record SaveFilterRequest(

    @NotBlank(message = "name must not be blank")
    @Size(max = 128, message = "name must be at most 128 characters")
    String name,

    @Size(max = 500, message = "description must be at most 500 characters")
    String description,

    @NotBlank(message = "expression must not be blank")
    @Size(max = 1024, message = "expression must be at most 1024 characters")
    String expression,

    @NotNull(message = "visibility must be PRIVATE or PROJECT")
    SavedFilterVisibility visibility

) {
}
