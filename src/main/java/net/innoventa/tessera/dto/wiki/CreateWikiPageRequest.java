package net.innoventa.tessera.dto.wiki;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.WikiPage;

/**
 * Raise a new page.
 *
 * <p>⚠️ <strong>The only write that carries a section</strong>, and that is why it is not the same record
 * as {@link UpdateWikiPageRequest}. Creating a page happens <em>inside</em> a section — somebody clicked
 * "new page" while looking at one — so the destination is part of the act. Editing one does not: a save
 * that carried a category would re-file the page every time somebody fixed a typo in a tab whose tree
 * had gone stale. Moving is {@link FileWikiPageRequest} on a route of its own.
 *
 * <p>{@code categoryId} null files it nowhere, which is a legitimate destination — a project with no
 * sections yet has only that one.
 */
public record CreateWikiPageRequest(
    @NotBlank @Size(max = WikiPage.MAXIMUM_TITLE_LENGTH) String title,

    /**
     * ⚠️ The ceiling counts characters and the column counts bytes — see
     * {@link WikiPage#MAXIMUM_MARKDOWN_LENGTH}, which is derived from the column's capacity at four
     * bytes per character so it holds whatever alphabet the page is written in.
     */
    @Size(max = WikiPage.MAXIMUM_MARKDOWN_LENGTH) String contentMarkdown,

    String categoryId
) {
}
