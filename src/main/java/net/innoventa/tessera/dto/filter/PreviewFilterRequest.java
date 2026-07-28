package net.innoventa.tessera.dto.filter;

/**
 * An expression to try without saving it.
 * <p>
 * Deliberately unvalidated: preview's whole job is to explain what is wrong with an expression, so
 * bouncing a blank or over-long one with a bean-validation {@code 400} would hide the very message the
 * editor is asking for. The evaluator applies the real limits and reports them as prose.
 */
public record PreviewFilterRequest(String expression) {
}
