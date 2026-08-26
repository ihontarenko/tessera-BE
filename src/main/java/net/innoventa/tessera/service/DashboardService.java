package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.dashboard.AgeingIssue;
import net.innoventa.tessera.dto.dashboard.BlockedIssue;
import net.innoventa.tessera.dto.dashboard.DashboardSummary;
import net.innoventa.tessera.dto.dashboard.FlowPoint;
import net.innoventa.tessera.dto.dashboard.ProjectProgress;
import net.innoventa.tessera.dto.dashboard.StatusMovement;
import net.innoventa.tessera.dto.dashboard.StatusStanding;
import net.innoventa.tessera.dto.dashboard.TypeStanding;
import net.innoventa.tessera.dto.dashboard.WeightPoint;
import net.innoventa.tessera.repository.ActivityLogItemRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LastStatusChange;
import net.innoventa.tessera.repository.IssueMoment;
import net.innoventa.tessera.repository.IssueTypeRepository;
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
import java.util.LinkedHashMap;
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
 * <h2>⚠️ Six questions, kept apart</h2>
 *
 * <p><strong>Flow</strong> — raised against resolved — is the only one of the six that can say whether
 * the backlog is growing. <strong>Weight</strong> asks it again in the team's estimate units rather
 * than in issues, which is why it is beside the flow chart and never inside it: a count and a weight do
 * not share an axis. The two can disagree, and the disagreement is the finding — twelve issues in and
 * twelve out is a week that broke even by count and may have doubled the backlog by weight.
 * <strong>Movement</strong> comes from the activity log and counts
 * moves rather than issues. <strong>Standing</strong> counts issues where they sit right now, and is
 * movement's other half: the two carry the same shape, answer opposite questions, and cannot be read
 * off one another — a week of furious movement can end with the boards exactly as they started.
 * <strong>By type</strong> counts the same open issues by what kind of work they are, which standing
 * cannot show however it is arranged. <strong>Ageing</strong> is how long an open issue has sat where
 * it is, which is the question a board is structurally unable to answer. <strong>Blocked</strong> is the engine's own definition, asked of
 * {@link IssueBlockers} rather than restated here.
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
    private final IssueTypeRepository       issueTypeRepository;
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
                0, 0, 0, 0, weight(List.of(), List.of(), first, today),
                List.of(), List.of(), List.of(), List.of(), List.of(), 0, List.of(), 0, days);
        }

        LocalDateTime windowOpened = first.atStartOfDay();

        List<Issue> open = issueRepository
            .findByProjectIdInAndResolutionIdIsNullAndArchivedAtIsNull(projectIds);

        // ⚠️ Two reads behind four charts. Each side is asked for once and answers both the flow chart,
        // which counts the rows, and the weight chart, which sums their estimates — asking the database
        // separately per chart would be four round trips, and two of them could disagree with the other
        // two if somebody raised or resolved something in between.
        List<IssueMoment> created  = issueRepository.createdAtSince(projectIds, windowOpened);
        List<IssueMoment> resolved = issueRepository.resolvedAtSince(projectIds, windowOpened);

        List<LocalDateTime> createdAt  = created.stream().map(IssueMoment::at).toList();
        List<LocalDateTime> resolvedAt = resolved.stream().map(IssueMoment::at).toList();

        // ⚠️ Read once and handed down. Three of the sections below need the catalogue — movement by
        // name, standing and ageing by id — and re-reading it per section is three round trips for a
        // table that cannot change inside one transaction.
        List<Status>        catalogue = statusRepository.findAll();
        Map<String, Status> byId      = catalogue.stream()
            .collect(Collectors.toMap(Status::getId, Function.identity()));

        List<BlockedIssue> blocked = blocked(open, now);

        return new DashboardSummary(
            countOn(createdAt, today),
            created.size(),
            resolved.size(),
            flow(createdAt, resolvedAt, first, today),
            countEstimated(created),
            countEstimated(resolved),
            pointsOn(created, today),
            pointsOn(resolved, today),
            weight(created, resolved, first, today),
            movement(catalogue, projectIds, windowOpened),
            standing(open, catalogue, byId),
            byType(open),
            progress(projectIds),
            ageing(projectIds, open, byId, now),
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

    // ── Weight ────────────────────────────────────────────────────────────────

    /**
     * The same week as {@link #flow}, weighed instead of counted: estimate in against estimate out.
     *
     * <p>⚠️ <strong>Its own chart rather than a third bar on the flow, because points are not
     * issues.</strong> Two counts share an axis and invite the comparison that is the flow chart's
     * entire content; a weight on that axis invites a comparison that means nothing — "twelve raised,
     * eight delivered" is not eight of the twelve.
     *
     * <p>⚠️ <strong>Both sides, because one side answers nothing.</strong> This was first built as
     * delivered-only, accumulating across the window, and that shape was wrong in a way worth
     * recording: <em>a cumulative line only ever goes up</em>. With no reference on the plot, a good
     * week and a bad one differ by a slope nobody reads. Raised weight is the reference, and it is the
     * right one — it is the only thing that says whether finishing 400 points was keeping up or falling
     * behind.
     *
     * <p>⚠️ <strong>An unestimated issue contributes nothing, and that is under-reporting rather than a
     * zero.</strong> It is the honest arithmetic — nobody said what the work was worth, so nothing can
     * be added for it — but read alone the numbers say a quiet week happened when a busy unestimated
     * one did. Hence the two {@code estimated…InWindow} counts beside them: the chart is obliged to say
     * how much of the week it was actually able to weigh.
     *
     * <p>⚠️ <strong>Every day of the window, zeros included</strong>, for the same reason the flow
     * series fills its quiet days — and here it also keeps the two sides aligned, since a day that only
     * one of them saw would otherwise shift the other's bars onto the wrong date.
     */
    private List<WeightPoint> weight(
        List<IssueMoment> created, List<IssueMoment> resolved, LocalDate first, LocalDate last) {

        Map<LocalDate, Double> arriving = pointsPerDay(created);
        Map<LocalDate, Double> leaving  = pointsPerDay(resolved);

        List<WeightPoint> series = new ArrayList<>();

        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            series.add(new WeightPoint(
                day, arriving.getOrDefault(day, 0.0), leaving.getOrDefault(day, 0.0)));
        }

        return series;
    }

    private Map<LocalDate, Double> pointsPerDay(List<IssueMoment> moments) {
        return moments.stream()
            .filter(IssueMoment::estimated)
            .collect(Collectors.groupingBy(
                moment -> moment.at().toLocalDate(),
                Collectors.summingDouble(IssueMoment::storyPoints)));
    }

    private double pointsOn(List<IssueMoment> moments, LocalDate day) {
        return moments.stream()
            .filter(IssueMoment::estimated)
            .filter(moment -> moment.at().toLocalDate().equals(day))
            .mapToDouble(IssueMoment::storyPoints)
            .sum();
    }

    private long countEstimated(List<IssueMoment> moments) {
        return moments.stream().filter(IssueMoment::estimated).count();
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
    private List<StatusMovement> movement(
        List<Status> catalogue, List<String> projectIds, LocalDateTime from) {

        Map<String, StatusCategory> categories = catalogue.stream()
            .collect(Collectors.toMap(Status::getName, Status::getCategory, (first, second) -> first));

        return activityLogItemRepository.countMovesIntoStatusSince(projectIds, from).stream()
            .map(row -> new StatusMovement(row.key(), categories.get(row.key()), row.count()))
            .sorted(Comparator.comparingLong(StatusMovement::count).reversed()
                .thenComparing(StatusMovement::status))
            .toList();
    }

    // ── Standing ──────────────────────────────────────────────────────────────

    /**
     * Where the open work actually is, busiest status first.
     *
     * <p>⚠️ <strong>Counted from the issues already in hand, not from a second query.</strong> The
     * open set is loaded once for ageing and blocking and it is exactly the population this chart is
     * about — unresolved and unarchived, which is what "on a board" means. A {@code GROUP BY} would
     * be a third round trip returning a subset of rows already in memory.
     *
     * <p>⚠️ <strong>The counts sum to {@code openTotal}, and that is the point of the pairing.</strong>
     * Movement counts moves and can exceed the number of issues that exist; standing counts issues and
     * cannot. Two charts side by side that looked alike and did not agree would be read as a bug.
     *
     * <p>⚠️ <strong>An empty status is reported as a zero, not omitted.</strong> Same reasoning as the
     * flow series filling in its quiet days: a chart built only from the statuses that happen to hold
     * something cannot say <em>nothing is in review</em> — the row simply is not there, and an absent
     * bar and an absent status look identical. "Nothing is in review" is one of the more useful things
     * a board-wide picture can tell somebody.
     *
     * <p>⚠️ <strong>Done-category statuses are zero-filled out, because entering one requires a
     * resolution</strong> — so an open issue can never be sitting in one, and the bar would read zero
     * for ever. One that somehow holds an issue anyway is still reported: the count comes from the
     * issues, and an anomaly worth seeing must not be filtered away by a rule about what should be
     * impossible.
     *
     * <p>⚠️ <strong>An issue whose status the catalogue has lost is named by its identifier rather than
     * skipped.</strong> Same reasoning as the other two sections: it is genuinely sitting somewhere,
     * and a picture of the boards that quietly omits part of them is worse than no picture.
     */
    private List<StatusStanding> standing(
        List<Issue> open, List<Status> catalogue, Map<String, Status> byId) {

        Map<String, StatusStanding> rows = new LinkedHashMap<>();

        for (Status status : catalogue) {
            if (status.getCategory() != StatusCategory.DONE) {
                rows.put(status.getId(), new StatusStanding(status.getName(), status.getCategory(), 0));
            }
        }

        Map<String, Long> counted = open.stream()
            .collect(Collectors.groupingBy(Issue::getStatusId, Collectors.counting()));

        counted.forEach((statusId, count) -> {
            Status status = byId.get(statusId);

            rows.put(statusId, new StatusStanding(
                status == null ? statusId : status.getName(),
                status == null ? null : status.getCategory(),
                count));
        });

        return rows.values().stream()
            .sorted(Comparator.comparingLong(StatusStanding::count).reversed()
                .thenComparing(StatusStanding::status))
            .toList();
    }

    /**
     * What the open work actually <em>is</em>, commonest kind first.
     *
     * <p>⚠️ <strong>Standing's other half, and a genuinely different question.</strong> Standing says
     * where the open work sits; this says what it is made of. A hundred issues spread evenly across the
     * statuses is one picture, and a hundred issues of which seventy are bugs is another — no
     * arrangement of statuses can show the second.
     *
     * <p>⚠️ <strong>Counted from the issues already in hand.</strong> Same population as standing —
     * unresolved and unarchived — so these sum to {@code openTotal} as well, and the two cards agree by
     * construction rather than by a second query happening to run at the same moment.
     *
     * <p>⚠️ <strong>No zero rows, which is the opposite of standing's rule and for a reason.</strong>
     * Statuses are the few a project moves work through, so an empty one is a fact ("nothing is in
     * review"). The issue-type catalogue is global: it holds every kind any project ever configured, so
     * zero-filling it prints a dozen rows for kinds this installation has never raised and buries the
     * ones that mean something.
     *
     * <p>⚠️ <strong>A type the catalogue has lost is named by its identifier rather than skipped</strong>
     * — same reasoning as everywhere else here: the issue is real, and a picture of the boards that
     * quietly omits part of them is worse than no picture.
     */
    private List<TypeStanding> byType(List<Issue> open) {
        Map<String, IssueType> byId = issueTypeRepository.findAll().stream()
            .collect(Collectors.toMap(IssueType::getId, Function.identity()));

        return open.stream()
            .collect(Collectors.groupingBy(Issue::getIssueTypeId, Collectors.counting()))
            .entrySet().stream()
            .map(entry -> {
                IssueType type = byId.get(entry.getKey());

                return new TypeStanding(
                    type == null ? entry.getKey() : type.getName(),
                    type == null ? null : type.getIconKey(),
                    entry.getValue());
            })
            .sorted(Comparator.comparingLong(TypeStanding::count).reversed()
                .thenComparing(TypeStanding::type))
            .toList();
    }

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
    private List<AgeingIssue> ageing(
        List<String> projectIds, List<Issue> open, Map<String, Status> byId, LocalDateTime now) {

        Map<String, LocalDateTime> movedAt = activityLogItemRepository
            .lastStatusChangePerOpenIssue(projectIds).stream()
            .collect(Collectors.toMap(LastStatusChange::issueId, LastStatusChange::at));

        return open.stream()
            .map(issue -> {
                Status status = byId.get(issue.getStatusId());

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
