package net.innoventa.tessera.dto.board;

import jakarta.validation.constraints.NotNull;
import net.innoventa.tessera.domain.StatusCategory;

/** Designate a column as the fallback home for a {@link StatusCategory} (Phase-2 ticket 03,
 *  {@code ADMINISTER_PROJECT}) — whichever column previously held that role loses it in the same call,
 *  keeping the "exactly one fallback column per category" invariant intact. */
public record SetColumnFallbackRequest(
    @NotNull StatusCategory category
) {
}
