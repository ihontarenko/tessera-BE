package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.issue.CreateIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.CommentRepository;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueLinkRepository;
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

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IssueService#create} under the project's issue type scheme.
 * <p>
 * The scheme is a <strong>constraint, not a suggestion</strong>: the create dialog only offers what the
 * scheme grants, but the dialog is not the enforcement point, and a caller reaching the API directly
 * must be refused the same way. The rejection is pinned to happen <em>before</em> anything is written —
 * a refused create that had already burned an issue key would leave a gap in the project's numbering
 * for a request that never produced an issue.
 * <p>
 * Mocked repositories in the style of {@link BoardServiceFilterTest}: the question here is which
 * collaborator gets consulted and what happens to the answer, not whether JPA maps correctly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueServiceCreationTest {

    private static final String PROJECT_ID = "project-1";
    private static final String JWT_SUBJECT = "member-1";
    private static final String IN_SCHEME_TYPE = "issue-type-task";
    private static final String OUTSIDE_SCHEME_TYPE = "issue-type-epic";
    private static final String PRIORITY_ID = "priority-medium";

    @Mock private IssueRepository issueRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private IssueLabelRepository issueLabelRepository;
    @Mock private IssueLinkRepository issueLinkRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ProjectService projectService;
    @Mock private ProjectIssueTypeService projectIssueTypeService;
    @Mock private MemberService memberService;
    @Mock private WorkflowResolver workflowResolver;
    @Mock private RankService rankService;
    @Mock private IssueKeyAllocator issueKeyAllocator;
    @Mock private IssueHierarchyService issueHierarchyService;
    @Mock private ActivityLogService activityLogService;
    @Mock private IssueCatalog issueCatalog;
    @Mock private IssueAssembler issueAssembler;
    @Mock private Supplier<String> idGenerator;

    @InjectMocks private IssueService issueService;

    private final Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("sub", JWT_SUBJECT)
        .build();

    private final Project project = Project.builder()
        .id(PROJECT_ID)
        .key("TIC")
        .name("Tessera")
        .leadMemberId(JWT_SUBJECT)
        .issueTypeSchemeId("scheme-issue-type-todo")
        .workflowSchemeId("scheme-workflow-todo")
        .keyStrategy("PREFIXED_SEQUENCE")
        .build();

    @BeforeEach
    void stubTheHappyPath() {
        Member caller = Member.builder().id(JWT_SUBJECT).displayName("Ivan").build();

        when(memberService.resolveMember(jwt)).thenReturn(caller);
        when(projectService.requireProject(PROJECT_ID)).thenReturn(project);
        when(priorityRepository.existsById(PRIORITY_ID)).thenReturn(true);
        when(workflowResolver.resolveWorkflowId(any(), anyString())).thenReturn("workflow-todo");
        when(workflowResolver.initialStatus(anyString()))
            .thenReturn(Status.builder().id("status-todo").name("To Do").build());
        when(issueKeyAllocator.allocate(project)).thenReturn(new IssueKeyAllocator.Allocation(1, "TIC-1"));
        when(rankService.initialRank()).thenReturn("0|hzzzzz:");
        when(idGenerator.get()).thenReturn("issue-1");
        when(issueRepository.findFirstByProjectIdOrderByRankDesc(PROJECT_ID)).thenReturn(Optional.empty());
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityLogService.changeSet()).thenReturn(new ActivityLogService.ChangeSet());
    }

    private CreateIssueRequest requestFor(String issueTypeId) {
        return new CreateIssueRequest("Write the thing", null, issueTypeId, PRIORITY_ID, null, null, null);
    }

    @Test
    @DisplayName("a type the project's scheme grants is accepted and stored on the issue")
    void createsAnIssueWhoseTypeTheSchemeGrants() {
        issueService.create(jwt, PROJECT_ID, requestFor(IN_SCHEME_TYPE));

        ArgumentCaptor<Issue> saved = ArgumentCaptor.forClass(Issue.class);
        verify(issueRepository).save(saved.capture());

        assertThat(saved.getValue().getIssueTypeId()).isEqualTo(IN_SCHEME_TYPE);
        verify(projectIssueTypeService).requireCreatable(project, IN_SCHEME_TYPE);
    }

    @Test
    @DisplayName("a type outside the project's scheme is refused before anything is written")
    void refusesATypeTheSchemeDoesNotGrant() {
        doThrowOutsideScheme();

        assertThatThrownBy(() -> issueService.create(jwt, PROJECT_ID, requestFor(OUTSIDE_SCHEME_TYPE)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("issue type scheme");

        // No key burned, no row written, no history — the refusal is total.
        verify(issueRepository, never()).save(any());
        verify(issueKeyAllocator, never()).allocate(any());
        verify(activityLogService, never()).record(anyString(), anyString(), any());
    }

    /**
     * The message mirrors what {@link ProjectIssueTypeService} really throws — see
     * {@link ProjectIssueTypeServiceTest}, which owns the rule itself. This test only pins that creation
     * consults the guard and writes nothing when it refuses.
     */
    private void doThrowOutsideScheme() {
        doThrow(new BusinessRuleViolationException(
                "This project's issue type scheme does not offer issue type " + OUTSIDE_SCHEME_TYPE))
            .when(projectIssueTypeService).requireCreatable(project, OUTSIDE_SCHEME_TYPE);
    }

}
