package net.innoventa.tessera.dto.filter;

/**
 * A named, server-defined board filter (ADR-0008) as the toolbar receives it.
 *
 * <p>{@code selectedByDefault} and {@code exclusive} are how a filter says how it <em>behaves</em>
 * without the client special-casing it by id. The client owns the rules — a default one is on when
 * nobody has chosen anything, an exclusive one cannot be combined with the rest — while the catalog
 * owns which filter has them. Hard-coding "all-issues" on the client would put the same fact in two
 * places, which is precisely what moving these expressions server-side was for.
 *
 * @param id                stable identifier — what the client remembers as "on", so a renamed label
 *                          or a reworded expression never resets anyone's toggles
 * @param labelKey          translation key, resolved by the client against Central's catalog
 * @param label             the English fallback, so an untranslated key still renders a word
 * @param expression        the jME predicate itself — the client hands it straight back through
 *                          {@code ?filter=} rather than holding a private copy of what the filter means
 * @param selectedByDefault whether this filter is the one a board opens with
 * @param exclusive         whether choosing it clears the others, and choosing another clears it
 */
public record BoardFilterView(
    String id,
    String labelKey,
    String label,
    String expression,
    boolean selectedByDefault,
    boolean exclusive
) {

    /** An ordinary filter: off until chosen, and combines with whatever else is on. */
    public static BoardFilterView of(String id, String labelKey, String label, String expression) {
        return new BoardFilterView(id, labelKey, label, expression, false, false);
    }

}
