package net.innoventa.tessera.service.block.spi;

import net.innoventa.tessera.domain.Member;

/**
 * What a picker is asking for.
 *
 * <p>⚠️ <strong>The caller travels with the request</strong>, for the reason {@link BlockRequest} gives
 * and one more: this is a search, so the caller is not a detail of the answer — it <em>is</em> the
 * bound on it.
 *
 * @param query  what has been typed so far. ⚠️ Blank is legitimate and means "the first few" — a picker
 *               opens before anybody types, and answering nothing there reads as broken
 * @param limit  the most to return; a resolver may return fewer and never more
 * @param caller who is asking
 */
public record BlockSuggestRequest(String query, int limit, Member caller) {

    /** Whether anything has actually been typed — the difference between "browse" and "search". */
    public boolean isBrowsing() {
        return query == null || query.isBlank();
    }

}
