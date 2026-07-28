package net.innoventa.tessera.dto.filter;

/**
 * What the filter editor learns about an expression it has not applied yet (ADR-0008).
 * <p>
 * An unusable expression is reported here as {@code valid = false} with a message, <strong>not</strong>
 * as a {@code 400}. Preview runs while someone is typing, and a half-written predicate is the ordinary
 * state of a text field rather than a malformed request — the 400 belongs on the endpoints that
 * actually <em>apply</em> or <em>store</em> a filter.
 *
 * @param matchedCount how many cards the predicate selects, or {@code null} when it could not run
 * @param totalCount   how many cards were tried, so the editor can say "4 of 37" instead of a bare 4 —
 *                     and so an author can tell "matches nothing" apart from "the board is empty"
 */
public record FilterPreviewView(boolean valid, String message, Integer matchedCount, int totalCount) {

    public static FilterPreviewView valid(int matchedCount, int totalCount) {
        return new FilterPreviewView(true, null, matchedCount, totalCount);
    }

    public static FilterPreviewView invalid(String message, int totalCount) {
        return new FilterPreviewView(false, message, null, totalCount);
    }

}
