package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Comment;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueMatch;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.repository.CommentRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.jmouse.search.Relevance;
import org.jmouse.search.SearchTerms;
import org.jmouse.search.Weights;
import org.jmouse.search.jpa.SearchPredicates;
import org.jmouse.search.text.DocumentRanking;
import org.jmouse.search.text.Snippets;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finding an issue by what is written in it — <em>"where did we write that down"</em> (TSSR-156).
 *
 * <h2>⚠️ The SECOND search, and deliberately not a mode of the first</h2>
 *
 * <p>{@link IssueSearchService} is a filtered table: a project, a status, an assignee, an explicit sort
 * column and paging in the database. That is the Issues screen and it is right for it. This answers a
 * different question, and the two are incompatible in their mechanics as well as in their intent —
 * relevance ordering cannot coexist with "sort by priority", and ranking in Java cannot coexist with
 * paging in SQL. Folding them together to save a route would give a screen that sorts badly and a search
 * that ranks badly.
 *
 * <h2>⚠️ Comments are searched, and that is the point rather than a nicety</h2>
 *
 * <p>This tracker's own convention is that a decision goes in the thread: the {@code tessera} skill says
 * <em>"half of what a ticket knows is in the thread and not in the description"</em>. A search that reads
 * only the summary — which is what {@code /api/issues/search} does — cannot find any of it.
 *
 * <p>⚠️ Which is why the candidates are a <strong>union of two queries</strong>. An issue whose only
 * match is in a comment is not returned by any query over the issue table, however cleverly written, so
 * the comment half is not an optimisation to skip when tidying this up.
 *
 * <h2>⚠️ Visibility is asked BEFORE both queries, never after</h2>
 *
 * <p>{@link BrowsableProjects} is the one answer to what this caller may see. Both halves are confined
 * to it, and the comment half is confined by joining back to the issue rather than by trusting that a
 * comment on an invisible issue will simply not turn up.
 *
 * <h2>⚠️ The queries narrow; {@code Relevance} decides</h2>
 *
 * <p>Both ask the loose question — rows carrying <strong>any</strong> term. The rule, <em>every term
 * anywhere</em>, is applied once by {@code Relevance.matched()}. Two implementations of one rule is how
 * a screen and a tool start disagreeing about what exists.
 */
@Service
@RequiredArgsConstructor
public class IssueRelevanceSearch {

    /**
     * How many rows are ranked. Ranking reads every candidate's description and its comments, so this
     * bounds what one search can cost.
     */
    private static final int MOST_CANDIDATES = 400;

    /** How many comments are read across the whole candidate set, newest first. */
    private static final int MOST_COMMENTS = 2000;

    public static final int DEFAULT_RESULTS = 25;

    /**
     * ⚠️ The columns the loose issue query looks in, and they are the same three {@link #weigh} scores
     * by text. A column asked about here but not weighed there returns candidates that can never match;
     * one weighed but not asked about is an issue that answers and is never fetched.
     */
    private static final List<String> SEARCHED_COLUMNS =
            List.of("summary", "issueKey", "description");

    private final IssueRepository    issueRepository;
    private final CommentRepository  commentRepository;
    private final ProjectRepository  projectRepository;
    private final BrowsableProjects  browsableProjects;
    private final IssueAssembler     issueAssembler;
    private final MemberService      memberService;

    /**
     * The issues answering this query, best first.
     *
     * @param project narrow to one project, or null for everything this caller may browse. ⚠️ Narrowing
     *                only: a project they cannot browse answers empty rather than reaching into it.
     */
    @Transactional(readOnly = true)
    public List<IssueMatch> find(Jwt jwt, String query, String project, int limit) {
        return find(memberService.resolveMember(jwt), query, project, null, limit);
    }

    /**
     * The same, for a caller that already holds the member — the tool path.
     *
     * <p>⚠️ <strong>One implementation, two doors.</strong> A screen arrives with a token and a client
     * arrives with a resolved member; giving each its own search is how the two start disagreeing about
     * what exists, which is the failure this whole class was written to avoid one layer down.
     *
     * @param assigneeMemberId narrow to one person's issues, or null for everyone's. Applied in the
     *                         candidate query rather than over the ranked answer — filtering afterwards
     *                         would cut a page of results down to three and call it the best three.
     */
    @Transactional(readOnly = true)
    public List<IssueMatch> find(
            Member caller, String query, String project, String assigneeMemberId, int limit) {

        SearchTerms terms = SearchTerms.of(query);

        // ⚠️ Empty answers empty rather than everything — a cleared box must not list the tracker.
        if (terms.empty()) {
            return List.of();
        }

        List<String> browsable = browsableProjects.idsFor(caller);

        if (browsable.isEmpty()) {
            return List.of();
        }

        Set<String> projects = project == null || project.isBlank()
                ? new LinkedHashSet<>(browsable)
                : browsable.stream()
                        .filter(project::equals)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (projects.isEmpty()) {
            return List.of();
        }

        List<Issue> candidates = candidates(terms, projects, blankToNull(assigneeMemberId));

        if (candidates.isEmpty()) {
            return List.of();
        }

        return rank(candidates, terms, Math.max(1, limit));
    }

    /**
     * Every issue worth ranking: the ones whose own text carries a term, and the ones carrying a comment
     * that does.
     */
    private List<Issue> candidates(SearchTerms terms, Set<String> projects, String assignee) {
        Map<String, Issue> byId = new LinkedHashMap<>();

        for (Issue issue : issuesCarryingATerm(terms, projects, assignee)) {
            byId.put(issue.getId(), issue);
        }

        Set<String> commented = issueIdsWithMatchingComments(terms);

        // ⚠️ Only the ones not already found, and only inside the visible projects — the comment query
        // knows nothing about who may see what, so the restriction is applied here rather than assumed.
        List<String> extra = commented.stream()
                .filter(issueId -> !byId.containsKey(issueId))
                .toList();

        if (!extra.isEmpty()) {
            for (Issue issue : issueRepository.findAllById(extra)) {
                // ⚠️ The assignee narrows this half too. It is applied in the issue query above and has
                // to be repeated here, because the comment query cannot know who an issue is assigned
                // to — a filter honoured on one half of a union and not the other is worse than none.
                boolean assignedRight =
                        assignee == null || assignee.equals(issue.getAssigneeMemberId());

                if (projects.contains(issue.getProjectId()) && assignedRight) {
                    byId.put(issue.getId(), issue);
                }
            }
        }

        return List.copyOf(byId.values());
    }

    private List<Issue> issuesCarryingATerm(SearchTerms terms, Set<String> projects, String assignee) {
        Specification<Issue> specification = (root, query, builder) -> builder.and(
                root.get("projectId").in(projects),
                assignee == null
                        ? builder.conjunction()
                        : builder.equal(root.get("assigneeMemberId"), assignee),
                // ⚠️ Archived issues are out. Putting something away is what takes it off the board and
                // the lists; a search that resurrected it would be the one place the gesture did not
                // hold. The filtered table has an `includeArchived` switch for the deliberate case.
                builder.isNull(root.get("archivedAt")),
                SearchPredicates.anyTermIn(builder, root, terms, SEARCHED_COLUMNS));

        return issueRepository.findAll(
                specification,
                PageRequest.of(0, MOST_CANDIDATES, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .getContent();
    }

    private Set<String> issueIdsWithMatchingComments(SearchTerms terms) {
        Specification<Comment> specification = (root, query, builder) ->
                SearchPredicates.anyTermIn(builder, root, terms, List.of("body"));

        return commentRepository.findAll(
                specification,
                PageRequest.of(0, MOST_COMMENTS, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(Comment::getIssueId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The candidates weighed, sorted and cut.
     *
     * <p>⚠️ <strong>This method is the whole of Tessera's opinion about relevance</strong>, and each
     * weight is a claim worth disagreeing with out loud:
     *
     * <ul>
     *   <li><strong>the key is {@code CRITICAL}</strong> — somebody who types {@code TSSR-42} is
     *       <em>naming</em> an issue, not describing one. It is the least ambiguous thing anybody
     *       ever types into this box</li>
     *   <li><strong>the summary is {@code PRIMARY}</strong> — one line somebody wrote on purpose</li>
     *   <li><strong>the description is {@code SUPPORTING}</strong> — long and mostly unstructured</li>
     *   <li><strong>the comments are {@code SUPPORTING}</strong>, all of them as one field. ⚠️ One
     *       field, not one per comment: a thread of forty remarks would otherwise outweigh a title
     *       simply by being long, which is a ranking by conversation length</li>
     *   <li><strong>the project is a {@code boost}</strong> — it reorders and never admits. Searching
     *       for a project's name must not answer with everything in it</li>
     * </ul>
     */
    private List<IssueMatch> rank(List<Issue> candidates, SearchTerms terms, int limit) {
        Map<String, Project> projectsById = projectsOf(candidates);
        Map<String, String>  threads      = threadsOf(candidates);

        List<DocumentRanking.Ranked<Issue>> best = DocumentRanking.best(
                candidates,
                issue -> weigh(issue, terms, projectsById.get(issue.getProjectId()),
                               threads.get(issue.getId())),
                // ⚠️ Recency is the tie-break and never the sort — which is exactly what the filtered
                // search does by default, and why a passing mention outranks an exact key there.
                Comparator.comparing(Issue::getUpdatedAt).reversed(),
                limit);

        Map<String, Issue> byId = best.stream()
                .map(DocumentRanking.Ranked::subject)
                .collect(Collectors.toMap(Issue::getId, issue -> issue, (first, second) -> first,
                                          LinkedHashMap::new));

        // Paired by issue id rather than by position — the assembler preserves order, but a row landing
        // under the wrong project is not a mistake anybody would notice from the outside.
        Map<String, IssueRowResponse> rows = issueAssembler.rows(List.copyOf(byId.values())).stream()
                .collect(Collectors.toMap(IssueRowResponse::id, row -> row));

        List<IssueMatch> matches = new ArrayList<>(best.size());

        for (DocumentRanking.Ranked<Issue> ranked : best) {
            Issue            issue   = ranked.subject();
            IssueRowResponse row     = rows.get(issue.getId());
            Project          project = projectsById.get(issue.getProjectId());

            if (row == null || project == null) {
                continue;
            }

            matches.add(new IssueMatch(
                    new IssueSearchResponse.ProjectRef(
                            project.getId(), project.getKey(), project.getName()),
                    row,
                    passagesOf(issue, threads.get(issue.getId()), terms),
                    ranked.score(),
                    ranked.relevance().explain()));
        }

        return matches;
    }

    private Relevance weigh(Issue issue, SearchTerms terms, Project project, String thread) {
        Relevance relevance = Relevance.of(terms)
                .weigh("key",         Weights.CRITICAL,   issue.getIssueKey())
                .weigh("summary",     Weights.PRIMARY,    issue.getSummary())
                .weigh("description", Weights.SUPPORTING, issue.getDescription())
                .weigh("comments",    Weights.SUPPORTING, thread);

        if (project != null && terms.touchedBy(project.getKey(), project.getName())) {
            relevance.boost("in a matching project", Weights.CONTEXTUAL);
        }

        return relevance;
    }

    /**
     * The passage that matched — from the description if it is there, and from the thread otherwise.
     *
     * <p>⚠️ Description first, deliberately. Both may carry the words, and the description is what the
     * issue <em>is</em>; a comment is what somebody said about it. Showing the comment when the
     * description answers would be quoting the argument instead of the ticket.
     */
    private List<String> passagesOf(Issue issue, String thread, SearchTerms terms) {
        List<String> fromDescription = Snippets.from(issue.getDescription(), terms);

        return fromDescription.isEmpty() ? Snippets.from(thread, terms) : fromDescription;
    }

    /**
     * Every candidate's comments, joined into one text per issue.
     *
     * <p>⚠️ <strong>One field per issue rather than one per comment</strong>, which is a ranking decision
     * as much as a loading one — see {@link #rank}. Joined with blank lines so a passage cut across two
     * comments still reads as two things somebody said.
     */
    private Map<String, String> threadsOf(List<Issue> candidates) {
        List<String> issueIds = candidates.stream().map(Issue::getId).toList();

        if (issueIds.isEmpty()) {
            return Map.of();
        }

        Map<String, StringBuilder> byIssue = new LinkedHashMap<>();

        // One read for every candidate's whole thread. Per issue it is the same query four hundred
        // times, on the one screen that draws the most rows.
        for (Comment comment : commentRepository.findByIssueIdInOrderByCreatedAtAsc(issueIds)) {
            byIssue.computeIfAbsent(comment.getIssueId(), issueId -> new StringBuilder())
                    .append(comment.getBody())
                    .append("\n\n");
        }

        return byIssue.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Project> projectsOf(List<Issue> issues) {
        List<String> projectIds = issues.stream().map(Issue::getProjectId).distinct().toList();

        return projectRepository.findByIdInOrderByKeyAsc(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, project -> project));
    }

}
