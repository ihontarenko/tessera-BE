package net.innoventa.tessera.dto.filter;

/**
 * A named, server-defined board filter (ADR-0008) as the toolbar receives it.
 *
 * @param id         stable identifier — what the client remembers as "on", so a renamed label or a
 *                   reworded expression never resets anyone's toggles
 * @param labelKey   translation key, resolved by the client against Central's catalog
 * @param label      the English fallback, so an untranslated key still renders a word
 * @param expression the jME predicate itself — the client hands it straight back through
 *                   {@code ?filter=} rather than holding a private copy of what the filter means
 */
public record BoardFilterView(String id, String labelKey, String label, String expression) {
}
