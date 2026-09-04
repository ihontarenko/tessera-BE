package net.innoventa.tessera.dto.issue;

import java.util.List;

/**
 * An issue a relevance search found, and why (TSSR-156).
 *
 * <h2>⚠️ Why this is not {@link IssueSearchResponse.Item}</h2>
 *
 * <p>That one is a row of a <em>filtered table</em>: the reader chose a project, a status and a sort
 * column, so what they need is the issue's fields. This is a row of a <em>search</em>: the reader chose
 * nothing, and the two questions they actually have are <em>where is this</em> and <em>why is it in
 * front of me</em>.</p>
 *
 * <p>Hence the project on every row — the same reason the filtered search carries one — and hence the
 * two fields the table has no use for:</p>
 *
 * <ul>
 *   <li>{@code snippets} — the passages that matched, out of the description or the comments. Without
 *       them a result cannot be judged without opening it, and a list of twenty-five issues nobody can
 *       judge is twenty-five issues somebody opens.</li>
 *   <li>{@code why} — the reckoning in one line, from {@code Relevance.explain()}:
 *       {@code "key EXACT ×8.0 = 8.00, summary ALL_TERMS ×4.0 = 2.08"}. ⚠️ This is what the library's
 *       structured result exists for, and it is here rather than hidden because a ranking nobody can
 *       question is a ranking nobody can fix.</li>
 * </ul>
 *
 * <p>⚠️ <strong>The snippets carry no highlight markup</strong>, deliberately: the same strings are read
 * on a screen, by a person and by a model through a tool, and only one of those wants markup. Marking
 * the terms is the caller's two lines.
 *
 * <p>{@code score} is on the library's shared {@code Weights} scale and means nothing on its own. Do not
 * render it as a percentage and do not compare one across releases.
 */
public record IssueMatch(
    IssueSearchResponse.ProjectRef project,
    IssueRowResponse issue,
    List<String> snippets,
    double score,
    String why
) {
}
