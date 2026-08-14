package net.innoventa.tessera.dto.configuration;

import java.util.List;

/**
 * An estimation scale, in the order it is offered.
 *
 * <p>⚠️ <strong>Both halves of every item travel</strong> — the label is what a person picks and the
 * weight is what is stored on the issue. A client resolving a stored number back to a label needs the
 * pairs, and a client offering the scale needs them in order (ADR-0019).
 */
public record EstimationSchemeResponse(
    String id,
    String name,
    String description,
    List<Item> items
) {

    /** One option: what it is called, and what it counts as. */
    public record Item(String label, double weight) {
    }

}
