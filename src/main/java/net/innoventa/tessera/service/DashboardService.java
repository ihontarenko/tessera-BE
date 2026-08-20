package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.dashboard.AgeingIssue;
import net.innoventa.tessera.dto.dashboard.BlockedIssue;
import net.innoventa.tessera.dto.dashboard.DashboardSummary;
import net.innoventa.tessera.dto.dashboard.FlowPoint;
import net.innoventa.tessera.dto.dashboard.ProjectProgress;
import net.innoventa.tessera.dto.dashboard.StatusMovement;
import net.innoventa.tessera.repository.ActivityLogItemRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LastStatusChange;
import net.innoventa.tessera.repository.ProjectCategoryCount;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.service.IssueBlockers.Blockage;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The numbers behind the dashboard's charts.
 *
 * <h2>⚠️ Confined to what the caller may browse, and never filtered down to it</h2>
 *
 * <p>{@link BrowsableProjects} answers first and every query takes its answer. The distinction matters:
 * an aggregate computed over everything and then filtered has already been computed over everything,
 * and a total is the one shape in which a tracker leaks without naming anything — "43 open" tells
 * somebody who can see three projects that there are forty issues they cannot. A member on no project
 * gets zeros rather than an error, because "nothing to show" is a true and unremarkable state.
 *
 * <h2>⚠️ Four questions, kept apart</h2>
 *
 * <p><strong>Flow</strong> — raised against resolved — is the only one of the four that can say whether
 * the backlog is growing. <strong>Movement</strong> comes from the activity log and counts moves rather
 * than issues. <strong>Ageing</strong> is how long an open issue has sat where it is, which is the
 * question a board is structurally unable to answer. <strong>Blocked</strong> is the engine's own
 * definition, asked of {@link IssueBlockers} rather than restated here.
 *
 * <p>Answering one and labelling it another is the easy mistake, and it produces charts that cannot
 * change during a busy week that happens to end where it started.
 *
 * <h2>The clock is a bean</h2>
 *
 * <p>Same reason {@code IssueKeyStrategies} injects one: "today", "the last seven days" and "sitting
 * for eleven days" are the whole content of this service, and a boundary read from {@code
 * LocalDate.now()} inside the method is a boundary nothing can assert about.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** What a caller gets for asking for a window nobody would draw. */
    private static final int MINIMUM_DAYS = 1;
    private static final int MAXIMUM_DAYS = 90;

    /**
     * How many rows the two list-shaped charts carry.
     *
     * <p>⚠️ <strong>Bounded, and the total is reported beside it.</strong> Ageing and blocked are
     * per-issue rather than aggregated, so an installation with four hundred open issues would otherwise
     * put four hundred rows through a dashboard. Sorted oldest-first the cap keeps exactly the rows
     * worth looking at — and the screen says "of N", because a truncated list that does not say so reads
     * as the whole picture.
     */
    private static final int LONGEST_ROWS = 25;

    private final IssueRepository           issueRepository;
    private final ActivityLogItemRepository activityLogItemRepository;
    private final StatusRepository          statusRepository;
    private final IssueBlockers             issueBlockers;
    private final BrowsableProjects         browsableProjects;
    private final MemberService             memberService;
    private final Clock                     issueKeyClock;

    /**
     * Everything the dashboard draws, for one member.
     *
     * @param requestedDays the window in days, clamped rather than refused — a dashboard that answers a
     *                      validation error instead of a chart is worse than one that quietly draws a
     *                      sensible week, and the window it used is reported back so the screen labels
     *                      itself with the truth
     */
    @Transactional(readOnly = true)
    public DashboardSummary summarize(Jwt jwt, int requestedDays) {
        Member       caller     = memberService.resolveMember(jwt);
        List<String> projectIds = browsableProjects.idsFor(caller);
        int          days       = Math.clamp(requestedDays, MINIMUM_DAYS, MAXIMUM_DAYS);

        LocalDateTime now   = LocalDateTime.now(issueKeyClock);
        LocalDate     today = now.toLocalDate();
        LocalDate     first = today.minusDays(days - 1L);

        if (projectIds.isEmpty()) {
            return new DashboardSummary(
                0, 0, 0, flow(List.of(), List.of(), first, today),
                List.of(), List.of(), List.of(), 0, List.of(), 0, days);
        }

        LocalDateTime windowOpened = first.atStartOfDay();

        List<LocalDateTime> created  = issueRepository.createdAtSince(projectIds, windowOpened);
        List<LocalDateTime> resolved = issueRepository.resolvedAtSince(projectIds, windowOpened);
        List<Issue>         open     = issueRepository
            .findByProjectIdInAndResolutionIdIsNullAndArchivedAtIsNull(projectIds);

        List<BlockedIssue> blocked = blocked(open, now);

        return new DashboardSummary(
            countOn(created, today),
            created.size(),
            resolved.size(),
            flow(created, resolved, first, today),
            movement(projectIds, windowOpened),
            progress(projectIds),
            ageing(projectIds, open, now),
            open.size(),
            blocked.stream().limit(LONGEST_ROWS).toList(),
            blocked.size(),
            days);
    }

    // ── Flow ──────────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>Every day of the window, zeros included.</strong> A series built only from the days
     * that had activity draws a quiet week and a busy one identically — evenly spaced bars, one per
     * event — and it is the commonest way a small time series misleads.
     */
    private List<FlowPoint> flow(
        List<LocalDateTime> created, List<LocalDateTime> resolved, LocalDate first, LocalDate last) {

        Map<LocalDate, Long> raised = perDay(created);
        Map<LocalDate, Long> closed = perDay(resolved);

        List<FlowPoint> series = new ArrayList<>();

        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            series.add(new FlowPoint(day, raised.getOrDefault(day, 0L), closed.getOrDefault(day, 0L)));
        }

        return series;
    }

    private Map<LocalDate, Long> perDay(List<LocalDateTime> moments) {
        return moments.stream()
            .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
    }

    private long countOn(List<LocalDateTime> moments, LocalDate day) {
        return moments.stream().filter(moment -> moment.toLocalDate().equals(day)).count();
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    /**
     * Which statuses were entered, busiest first.
     *
     * <p>⚠️ <strong>A status the catalogue no longer holds is kept, uncategorised.</strong> The log
     * records names, so a status that has since been renamed or deleted still has a week's worth of
     * moves recorded against it — and dropping those would report a quieter week than actually
     * happened. It comes back with a null category, which the screen draws in neutral rather than
     * pretending to know which bucket it was.
     */
    private List<StatusMovement> movement(List<String> projectIds, LocalDateTime from) {
        Map<String, StatusCategory> categories = statusRepository.findAll().stream()
            .collect(Collectors.toMap(Status::getName, Status::getCategory, (first, second) -> first));

        return activityLogItemRepository.countMovesIntoStatusSince(projectIds, from).stream()
            .map(row -> new StatusMovement(row.key(), categories.get(row.key()), row.count()))
            .sorted(Comparator.comparingLong(StatusMovement::count).reversed()
                .thenComparing(StatusMovement::status))
            .toList();
    }

    // ── Standing ──────────────────────────────────────────────────────────────

    /**
     * Each project's three numbers.
     *
     * <p>⚠️ <strong>A project with no issues still gets a row.</strong> The dashboard lists every
     * project the member belongs to, and a meter that vanished for the empty ones would make a new
     * project look like a rendering fault on the day it matters most.
     */
    private List<ProjectProgress> progress(List<String> projectIds) {
        Map<String, Map<StatusCategory, Long>> counted = new HashMap<>();

        for (ProjectCategoryCount row : issueRepository.countByProjectAndCategory(projectIds)) {
            counted.computeIfAbsent(row.projectId(), project -> new HashMap<>())
                .merge(row.category(), row.count(), Long::sum);
        }

        return projectIds.stream()
            .map(projectId -> {
                Map<StatusCategory, Long> buckets = counted.getOrDefault(projectId, Map.of());

                return new ProjectProgress(
                    projectId,
                    buckets.getOrDefault(StatusCategory.TODO, 0L),
                    buckets.getOrDefault(StatusCategory.IN_PROGRESS, 0L),
                    buckets.getOrDefault(StatusCategory.DONE, 0L));
            })
            .toList();
    }

    // ── Ageing ────────────────────────────────────────────────────────────────

    /**
     * How long each open issue has sat where it is, longest first.
     *
     * <p>⚠️ <strong>An issue that has never moved ages from when it was raised.</strong> The log has no
     * row for it, and treating that as unknown would drop exactly the issues that have been still the
     * longest — the ones this chart exists to surface.
     *
     * <p>⚠️ <strong>A status the catalogue has lost is named, not skipped.</strong> Same reasoning as
     * the movement chart: the issue is still sitting there, and a chart of what is stuck that quietly
     * omits some of it is worse than no chart.
     */
    private List<AgeingIssue> ageing(List<String> projectIds, List<Issue> open, LocalDateTime now) {
        Map<String, LocalDateTime> movedAt = activityLogItemRepository
            .lastStatusChangePerOpenIssue(projectIds).stream()
            .collect(Collectors.toMap(LastStatusChange::issueId, LastStatusChange::at));

        Map<String, Status> statuses = statusRepository.findAll().stream()
            .collect(Collectors.toMap(Status::getId, Function.identity()));

        return open.stream()
            .map(issue -> {
                Status status = statuses.get(issue.getStatusId());

                return new AgeingIssue(
                    issue.getIssueKey(),
                    issue.getSummary(),
                    status == null ? issue.getStatusId() : status.getName(),
                    status == null ? null : status.getCategory(),
                    daysBetween(movedAt.getOrDefault(issue.getId(), issue.getCreatedAt()), now));
            })
            .sorted(Comparator.comparingLong(AgeingIssue::days).reversed()
                .thenComparing(AgeingIssue::issueKey))
            .limit(LONGEST_ROWS)
            .toList();
    }

    // ── Blocked ───────────────────────────────────────────────────────────────

    /**
     * What cannot start, longest-held first.
     *
     * <p>⚠️ <strong>{@link IssueBlockers} decides, not this method.</strong> A dashboard that counted
     * blocked issues its own way would disagree with the board about the same card, and that class
     * exists precisely so the rule has one home.
     *
     * <p>⚠️ <strong>Cannot-START, not cannot-finish.</strong> The two are different rules
     * ({@code BLOCKS_START} against {@code BLOCKS_DONE}) and only the first is what anybody means by
     * "blocked" in ordinary use — it is also the level the shipped link type carries. Folding both into
     * one bar would put two different problems under one label.
     */
    private List<BlockedIssue> blocked(List<Issue> open, LocalDateTime now) {
        List<String> openIds = open.stream().map(Issue::getId).toList();

        List<Blockage> blockages = issueBlockers.blockagesAmong(openIds, StatusCategory.IN_PROGRESS);

        if (blockages.isEmpty()) {
            return List.of();
        }

        Map<String, Issue> byId = open.stream().collect(Collectors.toMap(Issue::getId, Function.identity()));

        return blockages.stream()
            .map(blockage -> {
                Issue issue = byId.get(blockage.issueId());

                return new BlockedIssue(
                    issue.getIssueKey(),
                    issue.getSummary(),
                    blockage.blockerKeys(),
                    daysBetween(blockage.since(), now));
            })
            .sorted(Comparator.comparingLong(BlockedIssue::days).reversed()
                .thenComparing(BlockedIssue::issueKey))
            .toList();
    }

    /**
     * ⚠️ Whole days, floored, and never negative. A row written a moment ago is "0 days", which reads as
     * today; a clock that has moved backwards is somebody else's problem and must not draw a bar going
     * the wrong way.
     */
    private static long daysBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toDays());
    }
}
