package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.shipped.ShippedGroupView;
import net.innoventa.tessera.dto.shipped.ShippedResponse;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.SprintIssueRepository;
import net.innoventa.tessera.repository.SprintRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Shipped screen (TSSR-4): a project's finished work, sliced by the time it was finished in.
 *
 * <p><strong>Why not a flat archive list.</strong> A flat list of everything ever closed is a second
 * backlog that only grows and nobody scrolls twice. The question people actually bring to an archive is
 * <em>what did we deliver, and when</em> — so the answer is grouped, newest group first, and each group
 * carries its own count and point total.
 *
 * <p>The slicing follows how the project plans, which is the board's scope strategy and nothing else
 * (ADR-0012/0015): a team running sprints reads its history in sprints, and a team that does not reads
 * it in months. Both are derived on read from data ordinary use already wrote — no snapshot table and no
 * scheduled job, the same rule the reports obey (ADR-0013).
 *
 * <p>⚠️ <strong>Archived and unarchived work sit side by side here.</strong> Archiving takes an issue off
 * the board, the backlog and the issue list; this screen is the one it does <em>not</em> leave, because
 * putting something away is not the same as it never having shipped. Each row carries its
 * {@code archivedAt}, so the interface marks the ones that are put away rather than splitting the list in
 * two and asking the reader to join it back up.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippedService {

    /** The key a month group is ordered by — sortable as a string, which is why it is not the title. */
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /**
     * Where work that belongs to no sprint — or that finished before anybody recorded when — collects.
     * It sorts last under both slicings, because an undated group is the one nobody is looking for.
     */
    private static final String UNGROUPED_KEY = "";

    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final SprintIssueRepository sprintIssueRepository;

    private final ProjectService projectService;
    private final MemberService memberService;
    private final BoardService boardService;
    private final IssueAssembler issueAssembler;

    public ShippedResponse getShipped(Jwt jwt, String projectId) {
        memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        Board board = boardService.requireBoard(projectId);
        boolean groupedBySprint = board.getScopeStrategy() == BoardScopeStrategy.ACTIVE_SPRINT;

        List<Issue> finished = issueRepository.findByProjectIdAndResolutionIdIsNotNullOrderByResolvedAtDesc(projectId);
        Map<String, IssueRowResponse> rowsByIssueId = issueAssembler.rows(finished).stream()
            .collect(Collectors.toMap(IssueRowResponse::id, Function.identity()));

        List<ShippedGroupView> groups = groupedBySprint
            ? bySprint(projectId, finished, rowsByIssueId)
            : byMonth(finished, rowsByIssueId);

        int archived = (int) finished.stream().filter(issue -> issue.getArchivedAt() != null).count();

        return new ShippedResponse(projectId, groupedBySprint, groups, archived);
    }

    /**
     * Grouped by the sprint each issue was last committed to.
     *
     * <p>⚠️ <strong>Every sprint, not only the open ones.</strong> {@link SprintMembershipService} answers
     * "committed right now", which is the wrong question here: a closed sprint's membership rows are
     * deliberately left as they stand, because those rows <em>are</em> its report. This screen reads that
     * history, so it reaches the join table directly rather than borrowing a predicate built for the
     * opposite purpose.
     *
     * <p>An issue carried across sprints holds a row in each, so the latest {@code addedAt} wins — the
     * sprint it actually finished in, not the one it was first planned for.
     */
    private List<ShippedGroupView> bySprint(
        String projectId,
        List<Issue> finished,
        Map<String, IssueRowResponse> rowsByIssueId
    ) {
        List<Sprint> sprints = sprintRepository.findByProjectIdOrderByCreatedAtAsc(projectId);

        if (sprints.isEmpty()) {
            return byMonth(finished, rowsByIssueId);
        }

        Map<String, String> sprintIdByIssue = latestMembershipByIssue(sprints);
        Map<String, Sprint> sprintsById = sprints.stream()
            .collect(Collectors.toMap(Sprint::getId, Function.identity()));

        Map<String, List<IssueRowResponse>> issuesByGroup = new LinkedHashMap<>();
        for (Issue issue : finished) {
            String key = sprintIdByIssue.getOrDefault(issue.getId(), UNGROUPED_KEY);
            issuesByGroup.computeIfAbsent(key, group -> new ArrayList<>()).add(rowsByIssueId.get(issue.getId()));
        }

        return issuesByGroup.entrySet().stream()
            .map(entry -> ShippedGroupView.of(
                entry.getKey(),
                sprintTitle(sprintsById.get(entry.getKey())),
                entry.getValue()))
            .sorted(Comparator.comparing(
                group -> sprintOrder(sprintsById.get(group.key())),
                Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /** Grouped by the month the work was resolved in — the slicing a project without sprints reads. */
    private List<ShippedGroupView> byMonth(List<Issue> finished, Map<String, IssueRowResponse> rowsByIssueId) {
        Map<String, List<IssueRowResponse>> issuesByMonth = new LinkedHashMap<>();

        for (Issue issue : finished) {
            String key = issue.getResolvedAt() == null ? UNGROUPED_KEY : MONTH_KEY.format(issue.getResolvedAt());
            issuesByMonth.computeIfAbsent(key, month -> new ArrayList<>()).add(rowsByIssueId.get(issue.getId()));
        }

        return issuesByMonth.entrySet().stream()
            .map(entry -> ShippedGroupView.of(entry.getKey(), monthTitle(entry.getKey(), entry.getValue()), entry.getValue()))
            .sorted(Comparator.comparing(ShippedGroupView::key, Comparator.reverseOrder()))
            .toList();
    }

    /**
     * The sprint each issue was last added to, across every sprint of the project.
     *
     * <p>A membership that was explicitly removed does not count: the issue was pulled out of that sprint
     * and finished elsewhere, and a sprint's report already records the removal. What is left is the
     * commitment that was still standing when the work was done.
     */
    private Map<String, String> latestMembershipByIssue(List<Sprint> sprints) {
        List<String> sprintIds = sprints.stream().map(Sprint::getId).toList();

        return sprintIssueRepository.findBySprintIdIn(sprintIds).stream()
            .filter(membership -> membership.getRemovedAt() == null)
            .collect(Collectors.toMap(
                SprintIssue::getIssueId,
                SprintIssue::getSprintId,
                (first, second) -> second,
                LinkedHashMap::new));
    }

    /**
     * Sprints are ordered by when they ran, so a sprint planned early and run late lands where the team
     * remembers it. A sprint that never started has no such moment and sorts last, beside the issues that
     * belong to no sprint at all.
     */
    private LocalDateTime sprintOrder(Sprint sprint) {
        return sprint == null ? null : sprint.getStartedAt();
    }

    private String sprintTitle(Sprint sprint) {
        return sprint == null ? "Outside a sprint" : sprint.getName();
    }

    /**
     * A month's own name, taken from an issue in it rather than parsed back out of the key — the key is
     * an ordering device and the title is prose, and deriving one from the other would put a formatter
     * where a lookup does.
     */
    private String monthTitle(String key, List<IssueRowResponse> issues) {
        if (UNGROUPED_KEY.equals(key)) {
            return "Undated";
        }

        return issues.stream()
            .map(IssueRowResponse::resolvedAt)
            .filter(resolvedAt -> resolvedAt != null)
            .findFirst()
            .map(MONTH_TITLE::format)
            .orElse(key);
    }

}
