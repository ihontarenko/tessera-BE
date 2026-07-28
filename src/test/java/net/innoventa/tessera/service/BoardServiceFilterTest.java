package net.innoventa.tessera.service;

import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SwimlaneStrategy;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.dto.filter.FilterPreviewView;
import net.innoventa.tessera.exception.FilterExpressionException;
import net.innoventa.tessera.repository.BoardColumnRepository;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.service.filter.BoardFilterEvaluator;
import net.innoventa.tessera.service.filter.IssueFilterView;
import net.innoventa.tessera.service.filter.IssueFilterViewFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BoardService}'s filtering behaviour: ADR-0008's security property, and the editor's preview.
 * <p>
 * The security property is the one that matters most and is pinned where it could actually be broken —
 * <strong>filtering grants no visibility.</strong> The predicate is evaluated over views built from the
 * slice the permission-gated board query already returned, so a filter can only ever narrow. The way
 * that would regress is someone widening the set handed to the filter — reading the whole issue table
 * "because the filter needs it" — which is why this asserts on what the view factory receives rather
 * than only on what comes back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceFilterTest {

    private static final String VISIBLE_PROJECT = "project-visible";
    private static final String JWT_SUBJECT = "member-1";

    @Mock private BoardRepository boardRepository;
    @Mock private BoardColumnRepository boardColumnRepository;
    @Mock private BoardColumnStatusRepository boardColumnStatusRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private IssueTypeRepository issueTypeRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ProjectService projectService;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private MemberService memberService;
    @Mock private BoardColumnResolver boardColumnResolver;
    @Mock private EpicResolver epicResolver;
    @Mock private SprintService sprintService;
    @Mock private SprintMembershipService sprintMembershipService;
    @Mock private IssueFilterViewFactory issueFilterViewFactory;
    @Mock private BoardFilterEvaluator boardFilterEvaluator;
    @Mock private Jwt jwt;

    @InjectMocks private BoardService boardService;

    private final Issue visibleIssue = issue("issue-visible", VISIBLE_PROJECT);
    private final Issue issueInAnotherProject = issue("issue-hidden", "project-hidden");

    @BeforeEach
    void stubTheBoardRead() {
        Member caller = Member.builder().id(JWT_SUBJECT).displayName("Ivan").build();

        when(memberService.resolveMember(jwt)).thenReturn(caller);
        when(boardRepository.findByProjectId(VISIBLE_PROJECT)).thenReturn(Optional.of(board()));
        when(boardColumnRepository.findByBoardIdOrderByPositionAsc(anyString())).thenReturn(List.of());
        when(boardColumnStatusRepository.findByBoardId(anyString())).thenReturn(List.of());
        when(statusRepository.findAll()).thenReturn(List.of());
        when(issueTypeRepository.findAll()).thenReturn(List.of());
        when(priorityRepository.findAll()).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of());
        when(epicResolver.resolveAll(any(), any(), any())).thenReturn(Map.of());

        // The permission-gated query: only the project the caller may browse. The other project's issue
        // exists in the repository and is deliberately never returned by it.
        when(issueRepository.findByProjectIdOrderByRankAsc(VISIBLE_PROJECT)).thenReturn(List.of(visibleIssue));
    }

    @Test
    @DisplayName("a filter is only ever evaluated over the slice the caller could already read")
    void neverHydratesAnIssueOutsideTheCallersScope() {
        when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of());
        when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any())).thenReturn(Set.of());

        boardService.getBoard(jwt, VISIBLE_PROJECT, "issue.resolution is null");

        ArgumentCaptor<List<Issue>> hydrated = ArgumentCaptor.captor();
        verify(issueFilterViewFactory).build(hydrated.capture(), any());

        assertThat(hydrated.getValue()).containsExactly(visibleIssue);
        assertThat(hydrated.getValue()).doesNotContain(issueInAnotherProject);
    }

    @Test
    @DisplayName("a predicate cannot surface an issue from a project the caller has no access to")
    void cannotMarkAnIssueTheCallerCannotRead() {
        when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of(view(visibleIssue)));

        // Even an evaluator that answered with a foreign id could not put that card on the board: the
        // cards come from the gated slice, and matchedCardIds only marks among them.
        when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any()))
            .thenReturn(Set.of("issue-visible", "issue-hidden"));

        BoardResponse response = boardService.getBoard(jwt, VISIBLE_PROJECT, "issue.resolution is null");

        assertThat(response.cards()).extracting("id").containsExactly("issue-visible");
        assertThat(response.cards()).extracting("id").doesNotContain("issue-hidden");
    }

    @Test
    @DisplayName("`now` is resolved once per request, not once per card")
    void resolvesTheEvaluationInstantOncePerRequest() {
        when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of(view(visibleIssue)));
        when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any())).thenReturn(Set.of());

        Instant before = Instant.now();
        boardService.getBoard(jwt, VISIBLE_PROJECT, "issue.resolution is null");

        ArgumentCaptor<Instant> evaluatedAt = ArgumentCaptor.captor();
        verify(boardFilterEvaluator).matchingIssueIds(anyString(), any(), any(), evaluatedAt.capture());

        assertThat(evaluatedAt.getValue()).isBetween(before, Instant.now());
    }

    /**
     * The filter view-model, with its label/component/version/link joins, is the expensive half of a
     * filtered render — an unfiltered board must not pay for it. This is a performance claim the
     * javadoc makes, so it is worth a test rather than a comment.
     */
    @Test
    @DisplayName("an unfiltered board hydrates no filter views and calls no engine")
    void skipsTheWholeFilterPathWhenNoFilterIsRequested() {
        BoardResponse response = boardService.getBoard(jwt, VISIBLE_PROJECT, null);

        assertThat(response.matchedCardIds()).isNull();
        verify(issueFilterViewFactory, never()).build(any(), any());
        verify(boardFilterEvaluator, never()).matchingIssueIds(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a blank filter is treated as no filter, not as a predicate to evaluate")
    void treatsABlankFilterAsAbsent() {
        BoardResponse response = boardService.getBoard(jwt, VISIBLE_PROJECT, "   ");

        assertThat(response.matchedCardIds()).isNull();
        verify(boardFilterEvaluator, never()).matchingIssueIds(anyString(), any(), any(), any());
    }

    /**
     * Preview runs on every keystroke pause, so a half-written predicate is its normal input. It has to
     * <em>answer</em> — valid false, plus the engine's message — rather than throw, because a 400 per
     * keystroke would be noise and would put the reason somewhere the editor cannot show it inline.
     */
    @Nested
    @DisplayName("the editor's preview")
    class Preview {

        @Test
        @DisplayName("reports how much of the board a valid expression selects")
        void countsWhatAValidExpressionSelects() {
            when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of(view(visibleIssue)));
            when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any()))
                .thenReturn(Set.of("issue-visible"));

            FilterPreviewView preview = boardService.previewFilter(jwt, VISIBLE_PROJECT, "issue.resolution is null");

            assertThat(preview.valid()).isTrue();
            assertThat(preview.matchedCount()).isEqualTo(1);
            assertThat(preview.totalCount()).isEqualTo(1);
            assertThat(preview.message()).isNull();
        }

        @Test
        @DisplayName("answers with the engine's message instead of throwing, so mid-edit is not a 400")
        void reportsAnUnusableExpressionWithoutFailing() {
            when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of(view(visibleIssue)));
            when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any()))
                .thenThrow(new FilterExpressionException("This filter could not be read: unexpected end"));

            FilterPreviewView preview = boardService.previewFilter(jwt, VISIBLE_PROJECT, "issue.assignee ==");

            assertThat(preview.valid()).isFalse();
            assertThat(preview.message()).contains("could not be read");
            assertThat(preview.matchedCount()).isNull();
            // Still reports the board size, so the editor can say what the filter was tried against.
            assertThat(preview.totalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("previews against the caller's own slice, never a wider one")
        void previewsOnlyTheCallersScope() {
            when(issueFilterViewFactory.build(any(), any())).thenReturn(List.of(view(visibleIssue)));
            when(boardFilterEvaluator.matchingIssueIds(anyString(), any(), any(), any())).thenReturn(Set.of());

            boardService.previewFilter(jwt, VISIBLE_PROJECT, "issue.resolution is null");

            ArgumentCaptor<List<Issue>> hydrated = ArgumentCaptor.captor();
            verify(issueFilterViewFactory).build(hydrated.capture(), any());

            assertThat(hydrated.getValue()).containsExactly(visibleIssue);
        }

    }

    private Board board() {
        return Board.builder()
            .id("board-1")
            .projectId(VISIBLE_PROJECT)
            .name("Board")
            .swimlaneStrategy(SwimlaneStrategy.NONE)
            .scopeStrategy(BoardScopeStrategy.ALL_ISSUES)
            .build();
    }

    private Issue issue(String id, String projectId) {
        return Issue.builder()
            .id(id)
            .projectId(projectId)
            .issueKey("TIC-" + id)
            .summary("Summary of " + id)
            .rank("0|100000")
            .build();
    }

    private IssueFilterView view(Issue issue) {
        return new IssueFilterView(
            issue.getId(), issue.getIssueKey(), issue.getSummary(),
            null, null, null, null, null, null, null,
            List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );
    }

}
