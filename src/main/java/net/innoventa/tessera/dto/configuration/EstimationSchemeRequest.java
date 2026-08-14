package net.innoventa.tessera.dto.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A whole estimation scale, sent as one — see {@link IssueTypeSchemeRequest} for why whole.
 *
 * @param items the options, <strong>in the order the picker offers them</strong>; position is the
 *              ordering, so there is no separate sequence to keep in step
 */
public record EstimationSchemeRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description,
    @NotEmpty @Valid List<Item> items
) {

    public record Item(@NotBlank @Size(max = 32) String label, double weight) {
    }

}
