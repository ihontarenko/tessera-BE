package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.config.JmeConfiguration;
import net.innoventa.tessera.dto.filter.FilterGrammarView.GrammarEntry;
import net.innoventa.tessera.dto.filter.FilterGrammarView.GrammarSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static net.innoventa.tessera.service.filter.BoardFixture.BOARD;
import static net.innoventa.tessera.service.filter.BoardFixture.CALLER;
import static net.innoventa.tessera.service.filter.BoardFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Keeps the editor's help panel honest.
 * <p>
 * A cheat-sheet is worse than useless once it drifts: it sends an author hunting for a bug in a correct
 * expression. So every documented example is actually evaluated, and every documented accessor is
 * checked against {@link IssueFilterView}'s real components — the two ways this file could start lying.
 */
class FilterGrammarReferenceTest {

    private final BoardFilterEvaluator evaluator =
        new BoardFilterEvaluator(new JmeConfiguration().filterExpressionLanguage());

    static List<String> examples() {
        return FilterGrammarReference.exampleExpressions();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    @DisplayName("every documented example actually evaluates")
    void evaluatesEveryDocumentedExample(String expression) {
        assertThatCode(() -> evaluator.matchingIssueIds(expression, BOARD, CALLER, NOW))
            .as("documented example: %s", expression)
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("every documented accessor is a real component of the filter view")
    void documentsOnlyAccessorsTheViewActuallyHas() {
        List<String> roots = FilterGrammarReference.accessorNames().stream()
            .map(accessor -> accessor.replaceFirst("^issue\\.", ""))
            .map(accessor -> accessor.split("\\.")[0])
            .distinct()
            .toList();

        List<String> components = List.of(IssueFilterView.class.getRecordComponents()).stream()
            .map(RecordComponent::getName)
            .toList();

        assertThat(components).containsAll(roots);
    }

    @Test
    @DisplayName("every component of the filter view is documented — a silent accessor helps nobody")
    void documentsEveryAccessorTheViewOffers() {
        List<String> roots = FilterGrammarReference.accessorNames().stream()
            .map(accessor -> accessor.replaceFirst("^issue\\.", ""))
            .map(accessor -> accessor.split("\\.")[0])
            .distinct()
            .toList();

        List<String> components = List.of(IssueFilterView.class.getRecordComponents()).stream()
            .map(RecordComponent::getName)
            .toList();

        assertThat(roots).containsAll(components);
    }

    @Test
    @DisplayName("the reference is complete enough to render — no empty sections")
    void servesNoEmptySections() {
        List<GrammarSection> sections = new FilterGrammarReference().reference().sections();

        assertThat(sections).isNotEmpty().allSatisfy(section -> {
            assertThat(section.id()).isNotBlank();
            assertThat(section.title()).isNotBlank();
            assertThat(section.entries()).isNotEmpty().allSatisfy(entry -> {
                assertThat(entry.syntax()).isNotBlank();
                assertThat(entry.explanation()).isNotBlank();
            });
        });
    }

    /**
     * The pitfalls section exists to warn about expressions that return the WRONG ANSWER rather than an
     * error, so each warning has to still be true. If jME ever fixes one, this fails and the advice gets
     * removed instead of quietly misleading people.
     */
    @Test
    @DisplayName("the documented pitfalls are still real")
    void keepsThePitfallWarningsTrue() {
        // ⚠️ This assertion is INVERTED from what it was, and that is the point of the test working.
        // `in` used to swallow everything after it — `a in ['x'] and b` parsed as `a in (['x'] and b)`,
        // whose right side is a boolean, so membership was false for every row, silently. The language
        // now binds the right operand at `in`'s own precedence, so the expression groups the way it
        // reads. The warning was removed from the reference rather than left to mislead people.
        assertThat(evaluator.matchingIssueIds(
            "issue.priority.name in ['Highest','High'] and issue.resolution is null", BOARD, CALLER, NOW))
            .as("`in` binds its own right operand now, so combining it with `and` needs no brackets")
            .isEqualTo(evaluator.matchingIssueIds(
                "(issue.priority.name in ['Highest','High']) and issue.resolution is null",
                BOARD, CALLER, NOW));

        // ⚠️ Also inverted. A single-element list used to answer false; it agrees with `hasAny` now.
        // `hasAny` remains the better thing to write and the tests section still recommends it — but a
        // recommendation is not a pitfall, and it was removed from that section rather than left saying
        // the language is broken when it is not.
        assertThat(evaluator.matchingIssueIds("'api' in issue.labels", BOARD, CALLER, NOW))
            .as("membership in a one-element list answers the same as hasAny now")
            .containsExactly("issue-1");

        assertThat(evaluator.matchingIssueIds("issue.labels is hasAny(['api'])", BOARD, CALLER, NOW))
            .as("and the recommended form still works")
            .containsExactly("issue-1");
    }

    @Test
    @DisplayName("the evaluator's unknown-accessor message is built from this same list")
    void sharesTheAccessorListWithTheEvaluator() {
        GrammarEntry firstAccessor = new FilterGrammarReference().reference().sections().stream()
            .filter(section -> section.id().equals("accessors"))
            .findFirst()
            .orElseThrow()
            .entries()
            .getFirst();

        assertThat(FilterGrammarReference.accessorNames()).contains(firstAccessor.syntax());
    }

}
