package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.ProjectMembership;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final IssueAssembler issueAssembler;

    @Transactional(readOnly = true)
    public IssueSearchResponse search(
        Jwt jwt,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        int page,
        int size
    ) {
        return search(memberService.resolveMember(jwt), text, projectId, statusId, assigneeMemberId,
            openOnly, page, size);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    public IssueSearchResponse search(
        Member caller,
        String text,
        String projectId,
        String statusId,
        String assigneeMemberId,
        boolean openOnly,
        int page,
        int size
    ) {
        List<String> browsableProjectIds = browsableProjectIds(caller);

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1, MAXIMUM_PAGE_SIZE);

        if (browsableProjectIds.isEmpty()) {
            return new IssueSearchResponse(List.of(), pageNumber, pageSize, 0);
        }

        // Most recently touched first: a search across everything is a search for what is going on,
        // and rank only orders a board within one project.
        Page<Issue> found = issueRepository.search(
            browsableProjectIds,
            blankToNull(projectId),
            blankToNull(statusId),
            blankToNull(assigneeMemberId),
            openOnly,
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
     * The projects this member may browse: membership first, since a non-member sees nothing at all
     * (ADR-0002), then {@code BROWSE_PROJECT}, which an override can deny inside a project one belongs
     * to. Exactly the pair {@code requireVisible} enforces one project at a time.
     */
    private List<String> browsableProjectIds(Member caller) {
        return membershipRepository.findByMemberId(caller.getId()).stream()
            .map(ProjectMembership::getProjectId)
            .distinct()
            .filter(projectId -> projectPermissionService.hasPermission(caller.getId(), projectId, Permissions.BROWSE_PROJECT))
            .toList();
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
