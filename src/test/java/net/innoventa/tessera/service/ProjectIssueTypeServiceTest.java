package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.IssueTypeScheme;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * The scheme rule itself: which types a project may create, and what happens to one it may not.
 * <p>
 * Deliberately separate from {@link IssueServiceCreationTest}, which mocks this service away to pin
 * <em>when</em> creation consults it. That test would still pass if the rule below were inverted, so
 * the rule needs its own home — otherwise the only thing verified end-to-end is that a method is
 * called.
 * <p>
 * The fixture is the real seeded shape: the Todo scheme grants one Task, while Epic exists in the
 * catalog but sits outside that scheme — the exact situation the ticket describes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectIssueTypeServiceTest {

    private static final String PROJECT_ID = "project-1";
    private static final String JWT_SUBJECT = "member-1";
    private static final String TODO_SCHEME = "scheme-issue-type-todo";

    private static final IssueType TASK =
        IssueType.builder().id("issue-type-task").name("Task").hierarchyLevel(0).iconKey("task").build();
    private static final IssueType BUG =
        IssueType.builder().id("issue-type-bug").name("Bug").hierarchyLevel(0).iconKey("bug").build();
    private static final IssueType EPIC =
        IssueType.builder().id("issue-type-epic").name("Epic").hierarchyLevel(1).iconKey("epic").build();

    @Mock private IssueTypeSchemeRepository issueTypeSchemeRepository;
    @Mock private IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    @Mock private IssueTypeRepository issueTypeRepository;
    @Mock private ProjectService projectService;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private MemberService memberService;

    @InjectMocks private ProjectIssueTypeService projectIssueTypeService;

    private final Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("sub", JWT_SUBJECT)
        .build();

    private final Project project = Project.builder()
        .id(PROJECT_ID)
        .key("TIC")
        .name("Tessera")
        .leadMemberId(JWT_SUBJECT)
        .issueTypeSchemeId(TODO_SCHEME)
        .workflowSchemeId("scheme-workflow-todo")
        .keyStrategy("PREFIXED_SEQUENCE")
        .build();

    @BeforeEach
    void stubTheScheme() {
        when(memberService.resolveMember(jwt))
            .thenReturn(Member.builder().id(JWT_SUBJECT).displayName("Ivan").build());
        when(projectService.requireProject(PROJECT_ID)).thenReturn(project);
        when(issueTypeSchemeRepository.findById(TODO_SCHEME)).thenReturn(Optional.of(
            IssueTypeScheme.builder()
                .id(TODO_SCHEME)
                .name("Todo Issue Type Scheme")
                .defaultIssueTypeId(TASK.getId())
                .build()));
        when(issueTypeRepository.existsById(TASK.getId())).thenReturn(true);
        when(issueTypeRepository.existsById(EPIC.getId())).thenReturn(true);
        when(issueTypeRepository.existsById("issue-type-nonexistent")).thenReturn(false);

        grants(TASK);
    }

    /**
     * Point the project's scheme at {@code types}, in the order given.
     * <p>
     * The type lookup deliberately hands them back <strong>reversed</strong>. {@code findAllById}
     * promises no ordering, so a test whose stub returns them already in scheme order cannot tell
     * "re-orders by the scheme" from "passes the repository's order straight through" — and would keep
     * passing if the ordering guarantee were dropped.
     */
    private void grants(IssueType... types) {
        List<IssueType> granted = List.of(types);

        List<IssueTypeSchemeItem> items = IntStream.range(0, granted.size())
            .mapToObj(position -> IssueTypeSchemeItem.builder()
                .id("itsi-" + position)
                .schemeId(TODO_SCHEME)
                .issueTypeId(granted.get(position).getId())
                .sequence(position)
                .build())
            .toList();

        List<IssueType> shuffled = new ArrayList<>(granted);
        Collections.reverse(shuffled);

        when(issueTypeSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(TODO_SCHEME)).thenReturn(items);
        when(issueTypeRepository.findAllById(granted.stream().map(IssueType::getId).toList()))
            .thenReturn(shuffled);
    }

    @Test
    @DisplayName("a type the scheme grants is accepted")
    void acceptsATypeInsideTheScheme() {
        projectIssueTypeService.requireCreatable(project, TASK.getId());
    }

    @Test
    @DisplayName("a real type outside the scheme is a rule violation, not a missing resource")
    void refusesARealTypeOutsideTheScheme() {
        assertThatThrownBy(() -> projectIssueTypeService.requireCreatable(project, EPIC.getId()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("issue type scheme");
    }

    /**
     * The distinction is worth keeping: "you may not create that here" and "there is no such type" are
     * different mistakes, and collapsing both into one status would make a typo look like a policy.
     */
    @Test
    @DisplayName("a type that does not exist at all stays a 404, as it was before schemes were enforced")
    void refusesAnUnknownTypeAsNotFound() {
        assertThatThrownBy(() -> projectIssueTypeService.requireCreatable(project, "issue-type-nonexistent"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the read lists the scheme's types in the scheme's order, not the catalog's")
    void listsTheSchemesTypesInSchemeOrder() {
        // Bug before Task in the scheme — the reverse of how the global catalog would sort them.
        grants(BUG, TASK);

        ProjectIssueTypesResponse response = projectIssueTypeService.listCreatableIssueTypes(jwt, PROJECT_ID);

        assertThat(response.issueTypes()).extracting(IssueTypeResponse::id)
            .containsExactly(BUG.getId(), TASK.getId());
    }

    @Test
    @DisplayName("the read names the scheme's default so the dialog can preselect it")
    void identifiesTheSchemesDefaultType() {
        assertThat(projectIssueTypeService.listCreatableIssueTypes(jwt, PROJECT_ID).defaultIssueTypeId())
            .isEqualTo(TASK.getId());
    }

    /**
     * A scheme may name a default it does not itself grant — nothing in the schema forbids it. Handing
     * that id back would have the API preselect a type it would then refuse to create, and the client
     * is not the right place to notice the contradiction.
     */
    @Test
    @DisplayName("a default the scheme does not grant falls back to one it does")
    void fallsBackWhenTheDeclaredDefaultIsNotGranted() {
        // The scheme still declares Task as its default (see the fixture) but now grants only Bug.
        grants(BUG);

        assertThat(projectIssueTypeService.listCreatableIssueTypes(jwt, PROJECT_ID).defaultIssueTypeId())
            .isEqualTo(BUG.getId());
    }

    // ⚠️ "a non-member cannot read a project's types" was asserted here and is gone, because what it
    // asserted is gone: the service no longer runs a visibility gate. `@RequiresAccess(BROWSE_PROJECT,
    // scope = PROJECT)` on GET /api/projects/{projectId}/issue-types is what refuses a non-member now,
    // and the engine answers it with NOT_FOUND_OR_HIDDEN — the same 404, decided one layer out. A mock
    // of a collaborator that no longer collaborates would have gone on passing while testing nothing.

}
