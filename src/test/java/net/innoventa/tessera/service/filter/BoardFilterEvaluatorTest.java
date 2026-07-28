package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.config.JmeConfiguration;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.exception.FilterExpressionException;
import org.jmouse.el.ExpressionLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.innoventa.tessera.service.filter.BoardFixture.BOARD;
import static net.innoventa.tessera.service.filter.BoardFixture.CALLER;
import static net.innoventa.tessera.service.filter.BoardFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The board filter engine against the real {@link ExpressionLanguage} — no mocks, because the whole
 * question these answer is "does jME actually do what ADR-0008 says it does".
 * <p>
 * The engine is built through {@link JmeConfiguration} rather than with {@code new}, so these run
 * against the same extension set production does; a test passing on a plain engine while the shipped
 * one is missing {@code is blocked} would be worse than no test at all.
 */
class BoardFilterEvaluatorTest {

    private final BoardFilterEvaluator evaluator =
        new BoardFilterEvaluator(new JmeConfiguration().filterExpressionLanguage());

    @Test
    @DisplayName("`issue.assignee == currentMember` selects only the caller's issues")
    void selectsIssuesAssignedToTheCaller() {
        assertThat(matching("issue.assignee == currentMember")).containsExactlyInAnyOrder("issue-1", "issue-4");
    }

    @Test
    @DisplayName("member equality is by id — a stale display name must not drop the card")
    void comparesMembersByIdentityRatherThanEveryField() {
        Member renamedCaller = Member.builder().id("member-1").displayName("Ivan Hontarenko").build();

        Set<String> matched = evaluator.matchingIssueIds("issue.assignee == currentMember", BOARD, renamedCaller, NOW);

        assertThat(matched).containsExactlyInAnyOrder("issue-1", "issue-4");
    }

    @Test
    @DisplayName("predicates compose with `and`, which is what keeps saved filters combinable")
    void combinesPredicatesWithAnd() {
        assertThat(matching("issue.assignee == currentMember and issue.resolution is null"))
            .containsExactly("issue-1");
    }

    @Test
    @DisplayName("nested property access resolves through the view's records")
    void readsNestedProperties() {
        assertThat(matching("issue.status.category == 'IN_PROGRESS'"))
            .containsExactlyInAnyOrder("issue-1", "issue-2");
        assertThat(matching("issue.priority.name == 'Highest'")).containsExactly("issue-1");
        assertThat(matching("issue.epic.key == 'TIC-42'")).containsExactlyInAnyOrder("issue-1", "issue-2");
        assertThat(matching("issue.type.hierarchyLevel == 0")).hasSize(4);
    }

    /**
     * The non-boolean check probes a single card, so a filter's validity must not depend on <em>which</em>
     * card that is. Navigating through a null intermediate has to answer false rather than throw —
     * otherwise "issues under an epic" would be a 400 on any board whose first card has no epic, and the
     * probe would be deciding validity for the whole slice from one sample.
     */
    @Test
    @DisplayName("a null intermediate answers false, whichever card the probe happens to land on")
    void toleratesNullsOnTheProbedCard() {
        List<IssueFilterView> nullFirst = List.of(BoardFixture.UNASSIGNED, BoardFixture.MINE);

        assertThat(evaluator.matchingIssueIds("issue.epic.key == 'TIC-42'", nullFirst, CALLER, NOW))
            .containsExactly("issue-1");
        assertThat(evaluator.matchingIssueIds("issue.assignee.displayName == 'Ivan'", nullFirst, CALLER, NOW))
            .containsExactly("issue-1");
        assertThat(evaluator.matchingIssueIds("issue.resolution.name == 'Done'", nullFirst, CALLER, NOW))
            .isEmpty();
    }

    @Test
    @DisplayName("`now` is one instant for the whole run, not a clock read per card")
    void measuresEveryCardAgainstTheSameInstant() {
        // Both open cards were touched exactly a day before NOW, so a 1-day window either takes both or
        // neither. A per-card clock read would let the boundary fall between them.
        assertThat(matching("issue.updatedAt >= (now | minusDays(1))"))
            .containsExactlyInAnyOrder("issue-1", "issue-2");
    }

    /**
     * jME's own registry cannot answer "is this blocked" — that is a fact about Tessera's link model —
     * so {@link IssueLinkTestExtension} supplies it. These prove the registration took effect, which is
     * the part a unit test of the extension alone would miss.
     */
    @Nested
    @DisplayName("link-aware tests")
    class LinkTests {

        @Test
        @DisplayName("`issue is blocked` reads the inward half of a Blocks link, not the outward one")
        void selectsBlockedIssues() {
            assertThat(matching("issue is blocked")).containsExactly("issue-2");
        }

        @Test
        @DisplayName("`issue is blocks(key)` reads the outward half — the blocker, not the blocked")
        void selectsIssuesBlockingAGivenKey() {
            assertThat(matching("issue is blocks('TIC-2')")).containsExactly("issue-4");
            assertThat(matching("issue is blocks('TIC-1')")).isEmpty();
        }

        @Test
        @DisplayName("`issue is linkedTo(type)` ignores direction")
        void selectsIssuesLinkedByType() {
            assertThat(matching("issue is linkedTo('Blocks')")).containsExactlyInAnyOrder("issue-2", "issue-4");
            assertThat(matching("issue is linkedTo('Relates')")).isEmpty();
        }

    }

    /**
     * ADR-0008 requires a parse error, an unknown accessor and a non-boolean result to be three
     * <em>distinct</em> messages — one generic "invalid filter" would leave an author guessing which of
     * the three they hit. None may be a 500 and none may carry a stack trace.
     */
    @Nested
    @DisplayName("the error contract")
    class ErrorContract {

        @Test
        @DisplayName("an expression that is not a yes/no question is refused, not silently emptied")
        void refusesNonBooleanPredicate() {
            assertThatThrownBy(() -> matching("issue.summary"))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("yes/no");
        }

        @Test
        @DisplayName("an unparsable expression names the reading problem")
        void refusesUnparsableExpression() {
            assertThatThrownBy(() -> matching("issue.assignee =="))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("could not be read");
        }

        @Test
        @DisplayName("an unknown accessor lists what an issue actually has")
        void refusesUnknownAccessor() {
            assertThatThrownBy(() -> matching("issue.severity == 'high'"))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("does not have")
                .hasMessageContaining("labels");
        }

        @Test
        @DisplayName("an unknown filter or test is distinct from a field that does not exist")
        void refusesUnknownProcessor() {
            assertThatThrownBy(() -> matching("issue.summary | shout is contains('x')"))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("does not know");

            assertThatThrownBy(() -> matching("issue is enormous"))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("does not know");
        }

        @Test
        @DisplayName("no refusal leaks an engine class name or a stack trace to the author")
        void keepsEngineInternalsOutOfTheMessage() {
            List<String> broken = List.of("issue.assignee ==", "issue.severity == 'x'", "issue.summary");

            for (String expression : broken) {
                assertThatThrownBy(() -> matching(expression))
                    .isInstanceOf(FilterExpressionException.class)
                    .hasMessageNotContaining("Exception")
                    .hasMessageNotContaining("org.jmouse");
            }
        }

        @Test
        @DisplayName("a blank filter is refused — an empty board filter is a mistake, not 'match everything'")
        void refusesBlankExpression() {
            assertThatThrownBy(() -> matching("   ")).isInstanceOf(FilterExpressionException.class);
        }

        @Test
        @DisplayName("an over-long expression is refused before the engine ever sees it")
        void refusesOverlongExpression() {
            String tooLong = "issue.resolution is null and ".repeat(100) + "issue.resolution is null";

            assertThatThrownBy(() -> matching(tooLong))
                .isInstanceOf(FilterExpressionException.class)
                .hasMessageContaining("at most");
        }

    }

    @Test
    @DisplayName("an empty board short-circuits — no engine call, no error")
    void returnsNothingForAnEmptyBoard() {
        assertThat(evaluator.matchingIssueIds("issue.resolution is null", List.of(), CALLER, NOW)).isEmpty();
    }

    /**
     * The predicate runs over the views it is handed and can only ever return ids from that list, so a
     * filter narrows and never widens — an issue the caller could not read was never in the slice to
     * begin with. This pins the property at the seam; {@code BoardService} is what builds the slice.
     */
    @Test
    @DisplayName("a predicate cannot name an issue outside the slice it was given")
    void neverReturnsAnythingOutsideTheGivenSlice() {
        Set<String> matched = evaluator.matchingIssueIds(
            "issue.id == 'issue-99' or issue.resolution is null", BOARD, CALLER, NOW);

        assertThat(matched).isSubsetOf("issue-1", "issue-2", "issue-3", "issue-4");
        assertThat(matched).doesNotContain("issue-99");
    }

    private Set<String> matching(String predicate) {
        return evaluator.matchingIssueIds(predicate, BOARD, CALLER, NOW);
    }

}
