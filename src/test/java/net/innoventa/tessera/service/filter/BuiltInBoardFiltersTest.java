package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.config.JmeConfiguration;
import net.innoventa.tessera.dto.filter.BoardFilterView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.innoventa.tessera.service.filter.BoardFixture.StubCard;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.innoventa.tessera.service.filter.BoardFixture.BOARD;
import static net.innoventa.tessera.service.filter.BoardFixture.CALLER;
import static net.innoventa.tessera.service.filter.BoardFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0008's example expressions, evaluated as shipped.
 * <p>
 * This is the ticket's acceptance gate: the ADR's table was written before the engine existed, so each
 * row is checked against a real {@code ExpressionLanguage} rather than assumed to work. Two of them did
 * not, and the catalog carries the corrected spellings — {@code in} needs brackets because it binds
 * looser than {@code and}, and single-value collection membership goes through {@code hasAny([…])}
 * because {@code 'x' in list} explodes a lone string into characters. Keeping the check here means a
 * future engine bump that regresses either one fails loudly.
 */
class BuiltInBoardFiltersTest {

    private final BoardFilterEvaluator evaluator =
        new BoardFilterEvaluator(new JmeConfiguration().filterExpressionLanguage());

    private final BuiltInBoardFilters builtInBoardFilters = new BuiltInBoardFilters(evaluator);

    /** What each shipped filter must select from {@link BoardFixture}'s four cards. */
    private static final Map<String, Set<String>> EXPECTED = Map.of(
        // The board's resting state selects everything, which is the one row here that is not a
        // narrowing — and the one whose expectation has to be revisited when the fixture grows a card.
        "all-issues", Set.of("issue-1", "issue-2", "issue-3", "issue-4"),
        "my-issues", Set.of("issue-1", "issue-4"),
        "unassigned", Set.of("issue-3"),
        "unresolved", Set.of("issue-1", "issue-2", "issue-3"),
        "recently-updated", Set.of("issue-1", "issue-2"),
        "only-bugs", Set.of("issue-1", "issue-3"),
        "hot-and-open", Set.of("issue-1", "issue-3"),
        "in-progress-mine", Set.of("issue-1"),
        "blocked", Set.of("issue-2"),
        "stale", Set.of("issue-3")
    );

    /**
     * The catalog is a constant, so the parameter source needs no engine — but building it the same way
     * the application does keeps the test from depending on that staying true.
     */
    static List<BoardFilterView> catalog() {
        return new BuiltInBoardFilters(
            new BoardFilterEvaluator(new JmeConfiguration().filterExpressionLanguage())).catalog();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalog")
    @DisplayName("every built-in filter selects exactly the cards it is defined to")
    void selectsTheDefinedCards(BoardFilterView filter) {
        assertThat(evaluator.matchingIssueIds(filter.expression(), BOARD, CALLER, NOW))
            .as("%s — %s", filter.id(), filter.expression())
            .containsExactlyInAnyOrderElementsOf(EXPECTED.get(filter.id()));
    }

    @Test
    @DisplayName("the catalog and its expectations describe the same set of filters")
    void coversEveryShippedFilter() {
        assertThat(builtInBoardFilters.catalog().stream().map(BoardFilterView::id).toList())
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
    }

    @Test
    @DisplayName("the catalog parses at startup, so a typo fails the context rather than a member's click")
    void validatesEveryExpressionOnStartup() {
        builtInBoardFilters.requireCatalogParses();
    }

    /**
     * ADR-0008 nominates the retired client-side toggles as the acceptance oracle for their jME
     * replacements.
     * <p>
     * The stub ran over {@code BoardCard}, not over {@code IssueFilterView}, and that distinction is the
     * whole value of this test: it evaluates the stub's <em>original</em> predicates against the
     * <em>card</em> projection of the same fixture — {@code card.assigneeId}, {@code card.open} — rather
     * than re-deriving them from the view the jME side already reads. Comparing
     * {@code issue.resolution is null} against a Java {@code resolution() == null} would only prove the
     * engine can dereference a field; comparing it against {@code card.open} proves the replacement
     * preserved the behaviour a member actually had.
     */
    @Test
    @DisplayName("the three replaced toggles agree with the stub they retired, card for card")
    void agreesWithTheRetiredStub() {
        assertThat(matching("my-issues"))
            .isEqualTo(stubSelected(card -> CALLER.getId().equals(card.assigneeId())));
        assertThat(matching("unassigned"))
            .isEqualTo(stubSelected(card -> card.assigneeId() == null));
        assertThat(matching("unresolved"))
            .isEqualTo(stubSelected(StubCard::open));
    }

    private Set<String> matching(String filterId) {
        return evaluator.matchingIssueIds(expressionOf(filterId), BOARD, CALLER, NOW);
    }

    private String expressionOf(String filterId) {
        return builtInBoardFilters.catalog().stream()
            .filter(filter -> filter.id().equals(filterId))
            .findFirst()
            .orElseThrow()
            .expression();
    }

    private Set<String> stubSelected(Predicate<StubCard> stubPredicate) {
        return BoardFixture.stubCards().stream()
            .filter(stubPredicate)
            .map(StubCard::id)
            .collect(Collectors.toSet());
    }

}
