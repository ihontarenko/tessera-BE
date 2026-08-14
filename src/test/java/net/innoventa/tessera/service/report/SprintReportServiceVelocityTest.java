package net.innoventa.tessera.service.report;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.report.VelocityPointView;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.service.MemberService;
import net.innoventa.tessera.service.ProjectPermissionService;
import net.innoventa.tessera.service.ProjectService;
import net.innoventa.tessera.service.SprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Velocity (Phase-3 ticket 07) — the cross-sprint half of {@link SprintReportService}.
 * <p>
 * Two properties are worth pinning, and they are the two a later change could plausibly break. First,
 * <strong>one definition of committed and completed</strong>: the totals are whatever
 * {@link SprintReportProjection} says they are, so a second, independently written idea of "completed"
 * cannot drift in beside the first — which is exactly what asserting on a projection <em>stub</em>
 * proves, since a service doing its own arithmetic would ignore what the stub returned. Second,
 * <strong>only closed sprints</strong>: a running sprint measured mid-flight would drag the picture down
 * for no reason, and it is a one-word change to accidentally include one.
 * <p>
 * The arithmetic itself is not retested here — it is {@link SprintReportProjectionTest}'s subject, and
 * duplicating it would be the very second definition this ticket exists to avoid.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SprintReportServiceVelocityTest {

    private static final String PROJECT_ID = "project-1";
    private static final Member CALLER = Member.builder().id("member-1").displayName("Ada").build();

    @Mock
    private SprintFactLoader sprintFactLoader;
    @Mock
    private SprintReportProjection sprintReportProjection;
    @Mock
    private SprintService sprintService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectPermissionService projectPermissionService;
    @Mock
    private MemberService memberService;
    @Mock
    private IssueTypeRepository issueTypeRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SprintReportService sprintReportService;

    private final Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "ada").build();

    @BeforeEach
    void resolveTheCaller() {
        when(memberService.resolveMember(any())).thenReturn(CALLER);
        when(sprintFactLoader.load(anyCollection())).thenReturn(new SprintFactLoader.LoadedFacts(Map.of(), Map.of()));
    }

    @Test
    @DisplayName("reads the project's closed sprints, oldest first, and reports one point for each")
    void reportsOnePointPerClosedSprint() {
        Sprint first = closedSprint("sprint-1", "Sprint 1");
        Sprint second = closedSprint("sprint-2", "Sprint 2");

        when(sprintService.closedSprints(PROJECT_ID)).thenReturn(List.of(first, second));
        when(sprintReportProjection.project(any(), any()))
            .thenReturn(projectionOf(8, 21.0, 5, 13.0))
            .thenReturn(projectionOf(6, 18.0, 6, 18.0));

        List<VelocityPointView> velocity = sprintReportService.getVelocity(jwt, PROJECT_ID);

        assertThat(velocity)
            .extracting(VelocityPointView::sprintId, VelocityPointView::sprintName)
            .containsExactly(tuple("sprint-1", "Sprint 1"), tuple("sprint-2", "Sprint 2"));
    }

    @Test
    @DisplayName("takes committed and completed from the shared projection rather than counting again")
    void takesItsTotalsFromTheProjection() {
        when(sprintService.closedSprints(PROJECT_ID)).thenReturn(List.of(closedSprint("sprint-1", "Sprint 1")));
        when(sprintReportProjection.project(any(), any())).thenReturn(projectionOf(8, 21.0, 5, 13.0));

        VelocityPointView point = sprintReportService.getVelocity(jwt, PROJECT_ID).getFirst();

        assertThat(point.committedIssues()).isEqualTo(8);
        assertThat(point.committedPoints()).isEqualTo(21.0);
        assertThat(point.completedIssues()).isEqualTo(5);
        assertThat(point.completedPoints()).isEqualTo(13.0);
    }

    @Test
    @DisplayName("measures each sprint over its own window, closed at the moment it actually closed")
    void measuresEachSprintOverItsOwnWindow() {
        Sprint sprint = closedSprint("sprint-1", "Sprint 1");
        when(sprintService.closedSprints(PROJECT_ID)).thenReturn(List.of(sprint));
        when(sprintReportProjection.project(any(), any())).thenReturn(projectionOf(0, 0.0, 0, 0.0));

        sprintReportService.getVelocity(jwt, PROJECT_ID);

        ArgumentCaptor<SprintWindow> window = ArgumentCaptor.forClass(SprintWindow.class);
        verify(sprintReportProjection).project(window.capture(), any());

        assertThat(window.getValue().startedAt()).isEqualTo(sprint.getStartedAt());
        assertThat(window.getValue().endDate()).isEqualTo(sprint.getEndDate());
        assertThat(window.getValue().closedAt()).isEqualTo(sprint.getCompletedAt());
    }

    @Test
    @DisplayName("returns an empty series for a project that has never closed a sprint")
    void returnsAnEmptySeriesWithoutClosedSprints() {
        when(sprintService.closedSprints(PROJECT_ID)).thenReturn(List.of());

        assertThat(sprintReportService.getVelocity(jwt, PROJECT_ID)).isEmpty();
        verify(sprintReportProjection, never()).project(any(), any());
    }

    @Test
    @DisplayName("still asserts the project exists before reading anything of it")
    void assertsTheProjectExists() {
        when(sprintService.closedSprints(PROJECT_ID)).thenReturn(List.of());

        sprintReportService.getVelocity(jwt, PROJECT_ID);

        // ⚠️ The visibility half of this assertion is gone with the check it asserted. ReportController
        // declares BROWSE_PROJECT at the project, so a non-member never reaches this service — and a
        // verify() against a collaborator that no longer collaborates would pass while testing nothing.
        verify(projectService).requireProject(PROJECT_ID);
    }

    private Sprint closedSprint(String sprintId, String name) {
        return Sprint.builder()
            .id(sprintId)
            .projectId(PROJECT_ID)
            .name(name)
            .state(SprintState.CLOSED)
            .startedAt(LocalDateTime.of(2026, 3, 2, 9, 0))
            .endDate(LocalDate.of(2026, 3, 13))
            .completedAt(LocalDateTime.of(2026, 3, 13, 17, 0))
            .build();
    }

    private SprintProjection projectionOf(
        int committedIssues,
        double committedPoints,
        int completedIssues,
        double completedPoints
    ) {
        return new SprintProjection(
            List.of(), List.of(), List.of(), List.of(),
            committedIssues, committedPoints, completedIssues, completedPoints);
    }

}
