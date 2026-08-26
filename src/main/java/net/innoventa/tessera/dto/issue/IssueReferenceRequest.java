package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every reference one document mentions, asked about at once.
 *
 * <p>⚠️ <strong>A POST because a document may mention many.</strong> Twenty references in a query string
 * is a URL a proxy may truncate and a log will certainly keep; and the alternative — one request per
 * mention — is the N+1 the batch exists to avoid, on a path that runs while somebody is typing.
 *
 * @param references what each mention carries — an issue key somebody typed, or the permanent hash a
 *                   picker inserted. ⚠️ <strong>The caller does not have to know which</strong>, and
 *                   deliberately is not asked: a document mixes the two freely, and a client sorting
 *                   them by shape would be a second place that decides what a key looks like.
 *                   Deduplicated by the caller, and capped because a request naming ten thousand is not
 *                   a document — a limit refused is better than a query nobody bounded
 */
public record IssueReferenceRequest(
    @NotNull @Size(max = 100, message = "a document may reference at most 100 issues") List<String> references
) {
}
