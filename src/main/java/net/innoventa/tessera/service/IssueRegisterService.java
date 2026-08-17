package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueLinkView;
import net.innoventa.tessera.dto.issue.IssueRegisterResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.dto.issue.LinkDirection;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The registers — every issue the caller can browse that gathers others, with what it gathers (TSSR-45).
 *
 * <p>Its own service for the reason {@link IssueSearchService} is: the scope is the <em>member</em> rather
 * than a project, which inverts the usual order — resolve what the caller may see, then narrow inside it.
 * Sharing that opening move is what {@link BrowsableProjects} is for.
 *
 * <p>⚠️ <strong>Redaction is not re-decided here.</strong> Every entry comes from
 * {@link IssueAssembler#links(Issue, Member)}, the one place a link becomes a view, so an entry pointing
 * into a project the caller is not in arrives already redacted — the same key-and-status reference the
 * issue rail renders (TSSR-43). A second copy of that rule is exactly how one screen ends up showing a
 * summary the other withholds.
 *
 * <p>⚠️ <strong>And no link type is privileged.</strong> The caller may narrow to one type by identifier;
 * which type means "gathers an effort" is the interface's default and the reader's choice. {@code Tracks}
 * is a row somebody can rename, and TSSR-40 is the receipt for what happens when code decides otherwise.
 */
@Service
@RequiredArgsConstructor
public class IssueRegisterService {

    /** A register is a card with a table inside it, so a page of them is shorter than a page of rows. */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAXIMUM_PAGE_SIZE = 50;

    private final IssueRepository    issueRepository;
    private final ProjectRepository  projectRepository;
    private final BrowsableProjects  browsableProjects;
    private final MemberService      memberService;
    private final IssueAssembler     issueAssembler;

    @Transactional(readOnly = true)
    public IssueRegisterResponse registers(Jwt jwt, String linkTypeId, boolean inward, int page, int size) {
        return registers(memberService.resolveMember(jwt), linkTypeId, inward, page, size);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional(readOnly = true)
    public IssueRegisterResponse registers(Member caller, String linkTypeId, boolean inward, int page, int size) {
        List<String> browsableProjectIds = browsableProjects.idsFor(caller);

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1, MAXIMUM_PAGE_SIZE);

        if (browsableProjectIds.isEmpty()) {
            return new IssueRegisterResponse(List.of(), pageNumber, pageSize, 0);
        }

        String type = linkTypeId == null || linkTypeId.isBlank() ? null : linkTypeId;

        Page<Issue> found = issueRepository.findRegisters(
            browsableProjectIds,
            type,
            inward,
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));

        List<Issue> hubs = found.getContent();
        Map<String, IssueRowResponse> rowsById = issueAssembler.rows(hubs).stream()
            .collect(Collectors.toMap(IssueRowResponse::id, Function.identity()));
        Map<String, Project> projectsById = projectsOf(hubs.stream().map(Issue::getProjectId).toList());

        List<Register> registers = hubs.stream()
            .map(hub -> new Register(hub, linksOnOneSide(hub, caller, type, inward)))
            .toList();

        Map<String, String> projectKeysByIssueKey = projectKeysByIssueKey(registers);

        List<IssueRegisterResponse.Item> items = registers.stream()
            .map(register -> item(register, rowsById, projectsById, projectKeysByIssueKey))
            .toList();

        return new IssueRegisterResponse(items, pageNumber, pageSize, found.getTotalElements());
    }

    /**
     * This issue's links on <em>one</em> side of the arrow.
     *
     * <p>⚠️ <strong>Direction is what makes it a register at all.</strong> A link is stored once as
     * {@code source → target} and read from both ends, so an issue's own view of its links holds the ones it
     * gathers <em>and</em> the ones gathering it. Keeping both here listed every tracked ticket a second time
     * as a register of one — the same fact appearing twice, once at each end.
     *
     * <p>⚠️ <strong>Both sides are worth asking for, which is why this is a parameter and not a constant.</strong>
     * Outward answers "what does this effort cover"; inward answers "what covers this ticket" — and until the
     * second existed, an issue that is only ever a target could not head a group on that screen at all.
     */
    private List<IssueLinkView> linksOnOneSide(Issue hub, Member caller, String linkTypeId, boolean inward) {
        LinkDirection side = inward ? LinkDirection.INWARD : LinkDirection.OUTWARD;

        return issueAssembler.links(hub, caller).stream()
            .filter(link -> side.equals(link.direction()))
            .filter(link -> linkTypeId == null || linkTypeId.equals(link.linkTypeId()))
            .toList();
    }

    private IssueRegisterResponse.Item item(
        Register register,
        Map<String, IssueRowResponse> rowsById,
        Map<String, Project> projectsById,
        Map<String, String> projectKeysByIssueKey
    ) {
        List<IssueRegisterResponse.Entry> entries = register.links().stream()
            .map(link -> new IssueRegisterResponse.Entry(
                link.id(),
                link.linkTypeId(),
                link.linkTypeName(),
                link.label(),
                projectKeysByIssueKey.get(link.issue().issueKey()),
                link.issue()))
            .toList();

        // Finished is `resolution IS NULL` inverted (ADR-0004) — `open` already carries it, so the count
        // is over what the response says rather than a second query asking the same thing.
        int done = (int) entries.stream().filter(entry -> !entry.issue().open()).count();

        return new IssueRegisterResponse.Item(
            projectRefOf(projectsById.get(register.hub().getProjectId())),
            rowsById.get(register.hub().getId()),
            entries,
            done);
    }

    /**
     * Where each gathered issue lives, by key.
     *
     * <p>⚠️ <strong>Looked up rather than read off the key's prefix.</strong> A key <em>looks</em> like
     * {@code PROJECT-42} and is not required to stay that way — the format is configurable per project —
     * so splitting on the hyphen would be a parser quietly deciding what a project is called. One query
     * for the page answers it properly.
     */
    private Map<String, String> projectKeysByIssueKey(List<Register> registers) {
        List<String> issueKeys = registers.stream()
            .flatMap(register -> register.links().stream())
            .map(link -> link.issue().issueKey())
            .distinct()
            .toList();

        if (issueKeys.isEmpty()) {
            return Map.of();
        }

        List<Issue> gathered = issueRepository.findByIssueKeyIn(issueKeys);
        Map<String, Project> projectsById = projectsOf(gathered.stream().map(Issue::getProjectId).toList());

        return gathered.stream()
            .filter(issue -> projectsById.containsKey(issue.getProjectId()))
            .collect(Collectors.toMap(
                Issue::getIssueKey,
                issue -> projectsById.get(issue.getProjectId()).getKey()));
    }

    private Map<String, Project> projectsOf(List<String> projectIds) {
        return projectRepository.findByIdInOrderByKeyAsc(projectIds.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private IssueSearchResponse.ProjectRef projectRefOf(Project project) {
        return project == null
            ? null
            : new IssueSearchResponse.ProjectRef(project.getId(), project.getKey(), project.getName());
    }

    /** The hub and its links, before either has been turned into a response shape. */
    private record Register(Issue hub, List<IssueLinkView> links) {
    }

}
