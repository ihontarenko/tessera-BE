package net.innoventa.tessera.dto.issue;

import java.util.List;

/**
 * A page of issues from across every project the caller may browse (ticket 10).
 *
 * Each item names its project, which the project-scoped list never had to: a result here is only
 * meaningful once you know where it lives, and looking that up client-side would mean a second request
 * per row.
 */
public record IssueSearchResponse(
    List<Item> items,
    int page,
    int size,
    long total
) {

    public record Item(ProjectRef project, IssueRowResponse issue) {
    }

    public record ProjectRef(String id, String key, String name) {
    }

}
