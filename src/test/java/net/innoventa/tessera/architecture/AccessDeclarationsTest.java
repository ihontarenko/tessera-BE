package net.innoventa.tessera.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jmouse.access.enforcement.PublicEndpoint;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * <strong>An endpoint says what it needs, or says out loud that it needs nothing.</strong>
 *
 * <p>The failure this exists to stop has no symptom. An ungated route looks exactly like a carefully
 * written one — the difference is an annotation that is not there — and the only way anybody ever finds
 * one is by trying it. Tessera was one {@code projectPermissionService.require(…)} per service before
 * this cutover, which had the same shape of hole: a service somebody forgot to open with the call
 * refused nobody, and nothing anywhere asked whether it had been given an answer to give.
 *
 * <p>So the rule is that half of the ticket said as a build failure: a handler under {@code /api}
 * carries {@link RequiresAccess}, or {@link PublicEndpoint} with a reason. Two ways to be gated and no
 * way to be silent.
 *
 * <p>⚠️ <strong>{@code @PreAuthorize} is not a third way</strong>, even though {@code @EnableMethodSecurity}
 * is still on. It answers a different question and cannot be told to answer this one: an authority set
 * says what an account holds <em>somewhere</em>, with no target, so it can never reach a project.
 * Accepting it here would let a route be gated on being an installation administrator while looking, to
 * a reader, exactly like one gated on a project role.
 *
 * <p>Reading the class as well as the method is not a convenience — it is the rule. A controller
 * declares once that everything behind it is project-scoped and each method adds only the verb it needs;
 * requiring a declaration on every method would produce a wall of repetition, and a wall of repetition
 * is where a missing line hides.
 *
 * <p>No database and no Spring context: it reads the compiled classes and nothing else.
 */
@AnalyzeClasses(packages = "net.innoventa.tessera", importOptions = ImportOption.DoNotIncludeTests.class)
class AccessDeclarationsTest {

    private static final List<Class<? extends Annotation>> GATES =
            List.of(RequiresAccess.class, PublicEndpoint.class);

    @ArchTest
    static final ArchRule every_api_endpoint_says_what_it_needs =
            methods()
                    .that(handlersUnderTheApi())
                    .should(beGatedOrDeclaredPublic());

    private static DescribedPredicate<JavaMethod> handlersUnderTheApi() {
        return new DescribedPredicate<>("handlers under /api") {
            @Override
            public boolean test(JavaMethod method) {
                return isHandler(method) && servesTheApi(method.getOwner());
            }
        };
    }

    private static ArchCondition<JavaMethod> beGatedOrDeclaredPublic() {
        return new ArchCondition<>("carry @RequiresAccess or @PublicEndpoint") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean gated = GATES.stream().anyMatch(
                        gate -> method.isAnnotatedWith(gate) || method.getOwner().isAnnotatedWith(gate));

                events.add(new SimpleConditionEvent(method, gated, method.getFullName()
                        + " is reachable under /api and names no access declaration. Add "
                        + "@RequiresAccess — bare where the route needs only a signed-in caller — "
                        + "or say @PublicEndpoint(\"why\")."));
            }
        };
    }

    /**
     * Whether this handler answers under {@code /api}, class prefix included.
     *
     * <p>The prefix is where the answer usually is — a controller maps {@code /api/projects} and its
     * methods map {@code /{projectId}} — so reading only the method would find almost nothing. A
     * controller that maps nothing at class level and spells the full path on each method (Tessera has
     * several) is caught by the name, which is why the fallback is there.
     */
    private static boolean servesTheApi(JavaClass controller) {
        List<String> paths = paths(controller);

        return paths.stream().anyMatch(path -> path.startsWith("/api"))
               || (paths.isEmpty() && controller.getSimpleName().endsWith("Controller"));
    }

    private static List<String> paths(JavaClass controller) {
        return controller.isAnnotatedWith(RequestMapping.class)
                ? List.of(controller.getAnnotationOfType(RequestMapping.class).value())
                : List.of();
    }

    private static boolean isHandler(JavaMethod method) {
        return method.getOwner().getSimpleName().endsWith("Controller")
               && method.getModifiers().contains(JavaModifier.PUBLIC)
               && method.isMetaAnnotatedWith(RequestMapping.class);
    }
}
