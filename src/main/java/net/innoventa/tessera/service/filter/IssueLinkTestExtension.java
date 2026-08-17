package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.service.filter.IssueFilterView.LinkReference;
import org.jmouse.core.reflection.TypeClassifier;
import org.jmouse.el.evaluation.EvaluationContext;
import org.jmouse.el.extension.Arguments;
import org.jmouse.el.extension.Extension;
import org.jmouse.el.extension.Test;
import org.jmouse.el.extension.test.AbstractTest;

import java.util.List;
import java.util.function.Predicate;

/**
 * The link-aware half of ADR-0008's processor surface — {@code issue is blocked},
 * {@code issue is blocks('TIC-42')}, {@code issue is linkedTo('Relates')}.
 * <p>
 * These live here rather than in jMouse-EL because "blocked" is a Tessera fact, not a language one: it
 * means an <em>inward</em> link whose type carries a blocking effect, a reading that depends on this
 * product's link model. The ADR's rule that heavier logic ships as a registered test rather than as
 * {@code @bean} reach is exactly what this is — the expression names a question, and the answer is
 * implemented in Java where we control it.
 * <p>
 * Tests, not filters: {@code is} asks a question about a value, {@code |} transforms one, so a
 * predicate ends in {@code is blocked} and never in a pipe.
 */
public class IssueLinkTestExtension implements Extension {

    @Override
    public List<Test> getTests() {
        return List.of(new BlockedTest(), new BlocksTest(), new LinkedToTest());
    }

    /**
     * {@code issue is blocked} — something on the other end of an inward blocking link is holding it up.
     *
     * <p>⚠️ <strong>Decided by the link type's effect, not by its name</strong> (TSSR-40). This compared
     * the name against the literal {@code "Blocks"}, which stopped being true the moment anybody renamed
     * the row and could never be true for a second blocking type somebody added. There is one definition
     * of blocking now and it is the column.
     */
    private static final class BlockedTest extends IssueLinkTest {

        @Override
        protected Predicate<LinkReference> matcher(Arguments arguments, EvaluationContext context) {
            return link -> link.blocking() && LinkReference.INWARD.equals(link.direction());
        }

        @Override
        public String getName() {
            return "blocked";
        }

    }

    /** {@code issue is blocks('TIC-42')} — the outward half, naming what this issue is holding up. */
    private static final class BlocksTest extends IssueLinkTest {

        @Override
        protected Predicate<LinkReference> matcher(Arguments arguments, EvaluationContext context) {
            String blockedKey = argument(arguments, context);

            return link -> link.blocking()
                && LinkReference.OUTWARD.equals(link.direction())
                && link.issueKey().equals(blockedKey);
        }

        @Override
        public String getName() {
            return "blocks";
        }

    }

    /** {@code issue is linkedTo('Relates')} — any link of a type, either direction. */
    private static final class LinkedToTest extends IssueLinkTest {

        @Override
        protected Predicate<LinkReference> matcher(Arguments arguments, EvaluationContext context) {
            String linkType = argument(arguments, context);
            return link -> link.type().equals(linkType);
        }

        @Override
        public String getName() {
            return "linkedTo";
        }

    }

    /**
     * The shared shape of every link test: does any of this issue's links match?
     * <p>
     * Anything that is not an issue view answers {@code false} rather than throwing — a test asked of
     * the wrong kind of value is a predicate that does not hold, which is how jME's own tests behave
     * and what keeps a mistyped expression from turning into a 500.
     */
    private abstract static class IssueLinkTest extends AbstractTest {

        protected abstract Predicate<LinkReference> matcher(Arguments arguments, EvaluationContext context);

        @Override
        public boolean test(Object value, Arguments arguments, EvaluationContext context, TypeClassifier type) {
            if (!(value instanceof IssueFilterView issue) || issue.links() == null) {
                return false;
            }

            return issue.links().stream().anyMatch(matcher(arguments, context));
        }

        /** The test's single argument as a string, so {@code blocks(key)} accepts a literal or a variable. */
        protected String argument(Arguments arguments, EvaluationContext context) {
            if (arguments.isEmpty()) {
                return null;
            }

            return context.getConversion().convert(arguments.getFirst(), String.class);
        }

    }

}
