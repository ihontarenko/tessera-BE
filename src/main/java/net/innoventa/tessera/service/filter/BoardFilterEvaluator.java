package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.config.JmeConfiguration;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.exception.FilterExpressionException;
import org.jmouse.el.ExpressionLanguage;
import org.jmouse.el.evaluation.EvaluationContext;
import org.jmouse.el.node.expression.FilterNotFoundException;
import org.jmouse.el.node.expression.TestNotFoundException;
import org.jmouse.el.parser.ParseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs a board filter — a jME boolean predicate over one issue (ADR-0008) — against a loaded slice
 * and answers which issues survive it.
 * <p>
 * <strong>The predicate is the stored artifact; the pipeline is this class's business.</strong> An
 * author writes {@code issue.assignee == currentMember}; the collection plumbing around it is
 * composed here and never persisted, so a saved filter stays composable ({@code (a) and (b)}) and
 * stays translatable to a {@code WHERE} clause when Phase 4 needs it.
 * <p>
 * Evaluation is in memory, after SQL — sound only because a board's slice is bounded (a Kanban board
 * is not paginated and completed cards age out). {@link #MAXIMUM_EVALUATED_ISSUES} is the backstop
 * for the day that stops being true, and it fails loudly rather than quietly truncating.
 */
@Service
public class BoardFilterEvaluator {

    /**
     * Long enough for anything hand-written, short enough that a pathological expression cannot be
     * pasted in. Saved filters are validated against the same limit on write.
     */
    static final int MAXIMUM_EXPRESSION_LENGTH = 1024;

    /** A board slice this large means the bounded-set assumption no longer holds — refuse, don't crawl. */
    static final int MAXIMUM_EVALUATED_ISSUES = 5_000;

    private static final String PIPELINE = "issues | filter(issue -> (%s)) | map(issue -> issue.id) | list";

    private final ExpressionLanguage expressionLanguage;

    public BoardFilterEvaluator(
        @Qualifier(JmeConfiguration.FILTER_EXPRESSION_LANGUAGE) ExpressionLanguage expressionLanguage
    ) {
        this.expressionLanguage = expressionLanguage;
    }

    /**
     * The ids of the issues matching {@code predicate}.
     *
     * @param caller the member the expression knows as {@code currentMember}
     * @param now    the evaluation instant, resolved <strong>once</strong> by the caller — every issue
     *               in a run is compared against the same point in time, which a {@code now()} call
     *               evaluated per card could not promise
     */
    public Set<String> matchingIssueIds(
        String predicate,
        List<IssueFilterView> issues,
        Member caller,
        Instant now
    ) {
        String expression = requireUsableExpression(predicate);

        if (issues.isEmpty()) {
            return Set.of();
        }

        if (issues.size() > MAXIMUM_EVALUATED_ISSUES) {
            throw new FilterExpressionException(
                "This board holds more issues than a filter can be evaluated over (%d, limit %d)."
                    .formatted(issues.size(), MAXIMUM_EVALUATED_ISSUES));
        }

        Map<String, Object> variables = variables(caller, now);

        requireBooleanPredicate(expression, issues.getFirst(), variables);

        List<?> matchedIds = evaluate(PIPELINE.formatted(expression), variables(issues, caller, now), List.class);

        return matchedIds.stream()
            .filter(issueId -> issueId != null)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    /**
     * Accept an expression for storage, or refuse it now. Parsing is all that can be checked without
     * an issue to evaluate against — whether it yields a boolean only shows up against real data, and
     * {@link #matchingIssueIds} says so when it does. Still worth doing on write: a saved filter that
     * cannot even parse is a broken row that would fail for every viewer, every time.
     */
    public void requireStorable(String predicate) {
        String expression = requireUsableExpression(predicate);

        try {
            expressionLanguage.compile(expression);
        } catch (RuntimeException exception) {
            throw refusal(exception);
        }
    }

    /**
     * A predicate that returns something other than a boolean would not fail — jME's {@code filter}
     * treats any non-{@code true} result as "does not match", so a typo would silently empty the
     * board. One probe against a single issue turns that into a message the author can act on.
     */
    private void requireBooleanPredicate(String expression, IssueFilterView probe, Map<String, Object> variables) {
        Map<String, Object> probeVariables = new LinkedHashMap<>(variables);
        probeVariables.put("issue", probe);

        Object result = evaluate(expression, probeVariables, Object.class);

        if (!(result instanceof Boolean)) {
            throw new FilterExpressionException(
                "A filter must ask a yes/no question. This expression produced %s — did you mean to compare it, or to use `is`?"
                    .formatted(result == null ? "nothing" : "a value of type " + result.getClass().getSimpleName()));
        }
    }

    private <T> T evaluate(String expression, Map<String, Object> variables, Class<T> type) {
        try {
            EvaluationContext context = expressionLanguage.newContext();
            return expressionLanguage.evaluate(expression, context, variables, type);
        } catch (FilterExpressionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw refusal(exception);
        }
    }

    /**
     * The engine's failure translated into something the author can act on, and never its stack. The
     * three ways an expression goes wrong are three different mistakes and deserve three different
     * sentences: it does not parse, it names a field that does not exist, or it names a filter or test
     * that does not exist. jME distinguishes them by exception type, so this reads the type rather
     * than pattern-matching on message text.
     * <p>
     * An unknown accessor arrives as a bare {@link NullPointerException} from the property resolver —
     * unhelpful on its own, and the one case where the engine's own words are worse than none.
     */
    private FilterExpressionException refusal(RuntimeException exception) {
        if (exception instanceof ParseException) {
            return new FilterExpressionException("This filter could not be read: " + reason(exception));
        }

        if (exception instanceof FilterNotFoundException || exception instanceof TestNotFoundException) {
            return new FilterExpressionException("This filter uses something the engine does not know: " + reason(exception));
        }

        if (exception instanceof NullPointerException) {
            return new FilterExpressionException(
                "This filter asks an issue for something it does not have. Available: "
                    + String.join(", ", FilterGrammarReference.accessorNames()) + ".");
        }

        return new FilterExpressionException("This filter could not be evaluated: " + reason(exception));
    }

    /**
     * The engine's own words, never its stack. A message is all the author needs and all they should
     * see — an exception class name from inside the expression engine tells them nothing and tells an
     * attacker something.
     */
    private String reason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "the expression is not valid here." : message;
    }

    private String requireUsableExpression(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            throw new FilterExpressionException("A filter expression must not be empty.");
        }

        String expression = predicate.trim();

        if (expression.length() > MAXIMUM_EXPRESSION_LENGTH) {
            throw new FilterExpressionException(
                "A filter expression may be at most %d characters.".formatted(MAXIMUM_EXPRESSION_LENGTH));
        }

        return expression;
    }

    private Map<String, Object> variables(Member caller, Instant now) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("currentMember", new IssueFilterView.MemberReference(caller.getId(), caller.getDisplayName()));
        variables.put("now", now);
        // ⚠️ Derived from the same instant the caller pinned, never read from the clock again. The
        // schedule dates are days and are compared against a day; taking `LocalDate.now()` here would
        // let one run straddle midnight and judge two cards against different days.
        variables.put("today", now.atZone(ZoneId.systemDefault()).toLocalDate());
        return variables;
    }

    private Map<String, Object> variables(List<IssueFilterView> issues, Member caller, Instant now) {
        Map<String, Object> variables = variables(caller, now);
        variables.put("issues", issues);
        return variables;
    }

}
