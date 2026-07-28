package net.innoventa.tessera.service.report;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.dto.report.SprintReportIssueView;
import net.innoventa.tessera.dto.report.SprintReportResponse;
import net.innoventa.tessera.dto.report.VelocityPointView;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.ProjectPermissionService;
import net.innoventa.tessera.service.ProjectService;
import net.innoventa.tessera.service.SprintService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reads a Reports tab is made of (Phase-3 tickets 06 and 07): one sprint's report and burndown, and
 * the project's velocity across every sprint it has closed. Both are derived on read from data ordinary
 * use already wrote — no snapshot table, no scheduled job (ADR-0013).
 * <p>
 * The division of labour is the point. {@link SprintFactLoader} does the I/O, {@link SprintReportProjection}
 * does the arithmetic and no I/O whatsoever, and this service does neither: it gates, it stitches the two
 * together, and it hydrates the result into something a screen can render. Velocity is therefore not a
 * second definition of "committed" and "completed" — it is the same projection run over more sprints.
 * <p>
 * Reads go through the same visibility gate every project-scoped read uses — non-member {@code 404},
 * member without {@code BROWSE_PROJECT} {@code 403}. Reporting is a read: it needs no
 * {@code MANAGE_SPRINT}, so a viewer can follow the plan without being able to reshape it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SprintReportService {

    private final IssueTypeRepository issueTypeRepository;
    private final StatusRepository statusRepository;
    private final MemberRepository memberRepository;

    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final SprintService sprintService;
    private final SprintFactLoader sprintFactLoader;
    private final SprintReportProjection sprintReportProjection;

    /**
     * Any sprint of the project that has actually run can be reported on, closed or still running — the
     * only refusal is a sprint that never started, which has no window to draw an axis from.
     */
    public SprintReportResponse getReport(Jwt jwt, String projectId, String sprintId) {
        requireVisibleProject(jwt, projectId);

        Sprint sprint = sprintService.requireSprintInProject(projectId, sprintId);

        if (sprint.getStartedAt() == null) {
            throw new BusinessRuleViolationException(
                "Sprint '" + sprint.getName() + "' has not run yet, so there is nothing to report on");
        }

        SprintFactLoader.LoadedFacts loaded = sprintFactLoader.load(List.of(sprintId));
        SprintProjection projection = sprintReportProjection.project(window(sprint), loaded.factsFor(sprintId));
        Catalogs catalogs = loadCatalogs(loaded.issues().values());

        return new SprintReportResponse(
            SprintSummary.from(sprint),
            projection.committedIssues(),
            projection.committedPoints(),
            projection.completedIssues(),
            projection.completedPoints(),
            rows(projection.completed(), loaded.issues(), catalogs),
            rows(projection.incomplete(), loaded.issues(), catalogs),
            rows(projection.removed(), loaded.issues(), catalogs),
            projection.burndown()
        );
    }

    /**
     * The project's velocity: committed against completed for every <strong>closed</strong> sprint,
     * oldest first, so chronic over-commitment reads as a pattern rather than a feeling. Running and
     * future sprints are excluded — a partial sprint would drag the picture down for no reason.
     * <p>
     * Recomputed on read across the project's closed sprints, consistent with ADR-0013. At these volumes
     * that is free: it is two queries and a pass over rows the project already holds, however many
     * sprints it has run. Materialising a per-sprint summary at close remains available as a purely
     * additive change if it ever stops being free.
     */
    public List<VelocityPointView> getVelocity(Jwt jwt, String projectId) {
        requireVisibleProject(jwt, projectId);

        List<Sprint> closedSprints = sprintService.closedSprints(projectId);
        SprintFactLoader.LoadedFacts loaded = sprintFactLoader.load(closedSprints.stream().map(Sprint::getId).toList());

        return closedSprints.stream()
            .map(sprint -> toVelocityPoint(sprint, sprintReportProjection.project(window(sprint), loaded.factsFor(sprint.getId()))))
            .toList();
    }

    private VelocityPointView toVelocityPoint(Sprint sprint, SprintProjection projection) {
        return new VelocityPointView(
            sprint.getId(),
            sprint.getName(),
            projection.committedIssues(),
            projection.committedPoints(),
            projection.completedIssues(),
            projection.completedPoints()
        );
    }

    private Member requireVisibleProject(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        return caller;
    }

    /**
     * The span to report over. A running sprint is measured up to now and a closed one up to the moment
     * it closed, which is the single place the two differ — the projection itself needs no clock and no
     * idea which it is looking at.
     */
    private SprintWindow window(Sprint sprint) {
        LocalDateTime closedAt = sprint.getCompletedAt() == null ? LocalDateTime.now() : sprint.getCompletedAt();
        LocalDate endDate = sprint.getEndDate() == null ? closedAt.toLocalDate() : sprint.getEndDate();

        return new SprintWindow(sprint.getStartedAt(), endDate, closedAt);
    }

    private List<SprintReportIssueView> rows(
        List<SprintMemberFact> facts,
        Map<String, Issue> issues,
        Catalogs catalogs
    ) {
        return facts.stream()
            .map(fact -> {
                Issue issue = issues.get(fact.issueId());

                return new SprintReportIssueView(
                    issue.getId(),
                    issue.getIssueKey(),
                    issue.getSummary(),
                    IssueTypeSummary.from(catalogs.types.get(issue.getIssueTypeId())),
                    StatusSummary.from(catalogs.statuses.get(issue.getStatusId())),
                    memberSummary(catalogs, issue.getAssigneeMemberId()),
                    // The frozen estimate, not the issue's current one — the report is what was committed.
                    fact.storyPointsAtAdd()
                );
            })
            .toList();
    }

    private MemberSummary memberSummary(Catalogs catalogs, String memberId) {
        if (memberId == null) {
            return null;
        }

        Member member = catalogs.members.get(memberId);

        return member == null ? null : MemberSummary.from(member);
    }

    private Catalogs loadCatalogs(Collection<Issue> issues) {
        List<String> memberIds = issues.stream()
            .map(Issue::getAssigneeMemberId)
            .filter(memberId -> memberId != null)
            .distinct()
            .toList();

        return new Catalogs(
            issueTypeRepository.findAll().stream().collect(Collectors.toMap(IssueType::getId, Function.identity())),
            statusRepository.findAll().stream().collect(Collectors.toMap(Status::getId, Function.identity())),
            memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, Function.identity()))
        );
    }

    /** The small global catalogs a report line references, batched once so no line costs a query. */
    private record Catalogs(
        Map<String, IssueType> types,
        Map<String, Status> statuses,
        Map<String, Member> members
    ) {
    }

}
