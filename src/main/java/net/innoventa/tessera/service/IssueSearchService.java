package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.service.query.IssueQueries;
import org.jmouse.query.spring.builder.QueryRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Show me everything" — issues from across every project the caller may browse (ticket 10).
 *
 * It is its own service because it is the one read whose scope is the member rather than a project, and
 * that inverts the usual order: every other read is handed a project and asks whether the caller may see
 * it, while this one starts from what the caller may see and searches inside it. Keeping that inversion
 * in one place is what stops it leaking into {@link IssueService}, where every method may safely assume
 * a project it has already gated.
 *
 * The permitted set is resolved first and passed to the query as a fixed argument; every filter narrows
 * it and none can widen it (ADR-0008). A member of nothing searches nothing and gets an empty page —
 * not an error, since having no projects is a state, not a mistake.
 */
@Service
@RequiredArgsConstructor
public class IssueSearchService {

    /** Enough to fill a screen twice over; a caller asking for more than this gets this. */
    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final BrowsableProjects browsableProjects;
    private final MemberService memberService;
    private final IssueAssembler issueAssembler;
    private final IssueQueries issueQueries;

    @Transactional(readOnly = true)
    public IssueSearchResponse search(
        Jwt jwt,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        boolean includeArchived,
        int page,
        int size
    ) {
        return search(jwt, text, projectId, statusId, assigneeMemberId, openOnly, includeArchived,
            null, null, page, size);
    }

    /**
     * The same, with a jMQ expression.
     *
     * <p>Resolving the member stays here rather than in the controller: a route hands over the token it
     * was given, and who that is remains one service's answer.
     */
    @Transactional(readOnly = true)
    public IssueSearchResponse search(
        Jwt jwt,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        boolean includeArchived,
        String jmqFilter,
        String jmqOrder,
        int page,
        int size
    ) {
        return search(memberService.resolveMember(jwt), text, projectId, statusId, assigneeMemberId,
            openOnly, includeArchived, jmqFilter, jmqOrder, page, size);
    }

    /**
     * The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}.
     *
     * @param includeArchived whether work that has been put away is in the answer (TSSR-4). Search is the
     *                        one read where archived issues stay reachable at all: an archive nothing can
     *                        find again is a delete with extra steps, so this defaults to false at the
     *                        edges rather than being unavailable.
     */
    public IssueSearchResponse search(
        Member caller,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        boolean includeArchived,
        int page,
        int size
    ) {
        return search(caller, text, projectId, statusId, assigneeMemberId, openOnly, includeArchived,
            null, null, page, size);
    }

    /**
     * The same search, narrowed by a jMQ expression as well.
     *
     * <p>⚠️ The two ways of narrowing stay separate on purpose. The parameters above are the screen's own
     * controls, each of which the repository query already knows how to ask. A jMQ expression is written
     * over the issue vocabulary — the same words the board filter uses — and is answered by the
     * DATABASE, so paging and the count are real.
     *
     * <p>⚠️ It replaces nothing. {@code BoardFilterEvaluator} marks loaded cards over a rich object graph
     * and stays exactly as it is; this answers <em>which issues, out of everything</em>. Where both a
     * filter and the plain controls arrive, the expression wins and the controls are ignored — two
     * narrowings silently intersecting is a result nobody can explain.
     */
    public IssueSearchResponse search(
        Member caller,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        boolean includeArchived,
        String jmqFilter,
        String jmqOrder,
        int page,
        int size
    ) {
        List<String> browsableProjectIds = browsableProjects.idsFor(caller);

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1, MAXIMUM_PAGE_SIZE);

        if (browsableProjectIds.isEmpty()) {
            return new IssueSearchResponse(List.of(), pageNumber, pageSize, 0);
        }

        if (blankToNull(jmqFilter) != null || blankToNull(jmqOrder) != null) {
            return matching(caller, projectId, includeArchived, jmqFilter, jmqOrder, pageNumber, pageSize);
        }

        // Most recently touched first: a search across everything is a search for what is going on,
        // and rank only orders a board within one project.
        Page<Issue> found = issueRepository.search(
            browsableProjectIds,
            blankToNull(projectId),
            blankToNull(statusId),
            blankToNull(assigneeMemberId),
            openOnly,
            includeArchived,
            likePattern(text),
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );

        List<Issue> issues = found.getContent();
        Map<String, Project> projectsById = projectsOf(issues);
        // Paired by issue id rather than by position: the assembler happens to preserve order, but a
        // row landing under the wrong project is not a mistake anyone would notice from the outside.
        Map<String, String> projectIdByIssueId = issues.stream()
            .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        List<IssueSearchResponse.Item> items = issueAssembler.rows(issues).stream()
            .map(row -> toItem(row, projectsById.get(projectIdByIssueId.get(row.id()))))
            .toList();

        return new IssueSearchResponse(items, pageNumber, pageSize, found.getTotalElements());
    }

    /**
     * The jMQ path: the database says which issues and in what order, and this loads them.
     *
     * <p>⚠️ Re-ordered to the identifier list. {@code findAllById} returns rows in whatever order it
     * finds them, so the sort somebody asked for would be quietly lost between the two queries — which is
     * a list that looks sorted by nothing in particular rather than an error.
     *
     * <p>⚠️ Assembled through the same {@code issueAssembler} as the ordinary path. A second way to build
     * a row would be a second place for an issue to look different.
     */
    private IssueSearchResponse matching(
        Member caller,
        String projectId,
        boolean includeArchived,
        String jmqFilter,
        String jmqOrder,
        int pageNumber,
        int pageSize
    ) {
        QueryRunner.Matches matched = issueQueries.matching(
            caller, blankToNull(projectId), includeArchived, jmqFilter, jmqOrder,
            (long) pageNumber * pageSize, pageSize);

        Map<String, Issue> found = issueRepository.findAllById(matched.identifiers()).stream()
            .collect(Collectors.toMap(Issue::getId, issue -> issue));

        List<Issue> issues = matched.identifiers().stream()
            .map(found::get)
            .filter(Objects::nonNull)
            .toList();

        Map<String, Project> projectsById = projectsOf(issues);
        Map<String, String> projectIdByIssueId = issues.stream()
            .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        List<IssueSearchResponse.Item> items = issueAssembler.rows(issues).stream()
            .map(row -> toItem(row, projectsById.get(projectIdByIssueId.get(row.id()))))
            .toList();

        return new IssueSearchResponse(items, pageNumber, pageSize, matched.total());
    }

    private IssueSearchResponse.Item toItem(IssueRowResponse row, Project project) {
        return new IssueSearchResponse.Item(
            new IssueSearchResponse.ProjectRef(project.getId(), project.getKey(), project.getName()),
            row
        );
    }

    private Map<String, Project> projectsOf(List<Issue> issues) {
        List<String> projectIds = issues.stream().map(Issue::getProjectId).distinct().toList();

        return projectRepository.findByIdInOrderByKeyAsc(projectIds).stream()
            .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** A blank search is no search at all, rather than a `like '%%'` the database has to think about. */
    private static String likePattern(String text) {
        String trimmed = blankToNull(text);

        return trimmed == null ? null : "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
    }

}
