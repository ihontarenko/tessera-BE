package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every issue key one document mentions, asked about at once.
 *
 * <p>⚠️ <strong>A POST because a document may mention many.</strong> Twenty keys in a query string is a
 * URL a proxy may truncate and a log will certainly keep; and the alternative — one request per mention
 * — is the N+1 the batch exists to avoid, on a path that runs while somebody is typing.
 *
 * @param issueKeys the keys, deduplicated by the caller. Capped because a request naming ten thousand
 *                  is not a document, and a limit refused is better than a query nobody bounded
 */
public record IssueReferenceRequest(
    @NotNull @Size(max = 100, message = "a document may reference at most 100 issues") List<String> issueKeys
) {
}
