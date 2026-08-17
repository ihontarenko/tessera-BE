package net.innoventa.tessera.dto.wiki;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.WikiPage;

/**
 * Replace a page's title and its text.
 *
 * <p>⚠️ <strong>A replace, not a patch.</strong> Both fields travel on every save and the whole document
 * is what lands. A partial update would need "absent" and "cleared" to be different things in a JSON
 * body, which they are not, and the one field where that distinction would matter here is the entire
 * page.
 *
 * <p>⚠️ <strong>And the replace is irreversible.</strong> There is no version history in this pass, so
 * what was written before this request is gone when it succeeds. Nothing here can soften that — the
 * screen is where it has to be said.
 *
 * <p>No section: moving a page is {@link FileWikiPageRequest}, deliberately a different route.
 */
public record UpdateWikiPageRequest(
    @NotBlank @Size(max = WikiPage.MAXIMUM_TITLE_LENGTH) String title,

    @Size(max = WikiPage.MAXIMUM_MARKDOWN_LENGTH) String contentMarkdown
) {
}
