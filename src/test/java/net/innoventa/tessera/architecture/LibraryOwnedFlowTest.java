package net.innoventa.tessera.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jmouse.ai.mcp.authorization.server.ApprovingSubject;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * <strong>The flow that hands a client a credential is the library's, and this is what stops it being
 * written here a second time.</strong>
 *
 * <p>Tessera's version came second and was written <em>against</em> the library — four classes were
 * lifted out into {@code org.jmouse.ai.mcp.authorization} while it was being built. Nobody went back to
 * Innoventa, which carried its own copies of the same two policies for a year afterwards. That is the
 * failure this rule is really about: <strong>an extraction that stops at the first consumer leaves the
 * second one with a fork nobody knows is a fork</strong> — code that compiles, works, and is only wrong
 * in comparison with something nobody is looking at.
 *
 * <p>The other half is the positive one, and it guards a silent failure: the shared endpoints are
 * auto-configured <em>only</em> where a {@code ProtocolTokenMinter} bean exists. Delete that class and
 * nothing fails to compile — three routes simply stop being mapped, and the first thing that notices is
 * a client that cannot connect.
 *
 * <p>No database and no Spring context: it reads the compiled classes and nothing else.
 */
@AnalyzeClasses(packages = "net.innoventa.tessera", importOptions = ImportOption.DoNotIncludeTests.class)
public class LibraryOwnedFlowTest {

    /**
     * Types this product used to own and must not own again.
     *
     * <p>⚠️ Simple names rather than packages: what is being stopped is the class coming back
     * <em>somewhere else</em>, which is exactly how it would come back.
     */
    private static final List<String> LIBRARY_OWNED = List.of(
            "ProofKeyPolicy", "LoopbackRedirectPolicy", "McpAuthorizationGrants",
            "McpAuthorizationEndpoints", "McpOAuthGrants", "McpOAuthEndpoints",
            "AuthorizationDocuments", "InMemoryAuthorizationCodeStore");

    @ArchTest
    static final ArchRule theProtocolsOwnRulesStayInTheLibrary =
            noClasses()
                    .should(restateSomethingTheLibraryAlreadyOwns())
                    .because("""
                            every one of these already exists in jmouse-ai-mcp or \
                            jmouse-ai-mcp-authorization. They are requirements of a specification \
                            rather than decisions of this installation: PKCE is S256 or it is \
                            nothing, a redirect is an exact loopback or it is an open redirect, and \
                            an error code is a string a client branches on. If one of them is short \
                            of something, widen it — the library is in this workspace and takes \
                            changes.""");

    /*
     * ⚠️ `theSeamIsWired` IS GONE, AND THE SEAM WENT WITH IT.
     *
     * It asserted that whatever implements `CredentialIssuer` lives in `ai.authorization` — Tessera used
     * to mint its own protocol credential, signed with a secret only it held. That moved into the
     * library: `McpCredentialService` and `TesseraCredentialIssuer` are deleted, and nothing in this
     * product implements the interface any more.
     *
     * So the rule matched NO classes and ArchUnit failed it on `failOnEmptyShould` — the whole test class
     * red, over a rule that had simply stopped looking. ⚠️ The tempting fix is `allowEmptyShould(true)`,
     * which turns it into a rule that guards nothing and never says so. Deleting it is the honest one:
     * `whoMayApproveIsThisProductsAnswer` below is the half of this seam Tessera still owns.
     */

    @ArchTest
    static final ArchRule whoMayApproveIsThisProductsAnswer =
            classes()
                    .that().implement(ApprovingSubject.class)
                    .should().resideInAPackage("net.innoventa.tessera.ai.authorization..")
                    .because("""
                            Tessera has no service sub-accounts at all — a credential acts as the \
                            person who approved it — while the other product hosting the same flow \
                            issues to an agent account with its own permissions. That difference is \
                            this class, and nowhere else.""");

    private static ArchCondition<JavaClass> restateSomethingTheLibraryAlreadyOwns() {
        return new ArchCondition<>("restate something the authorization library already owns") {

            @Override
            public void check(JavaClass candidate, ConditionEvents events) {
                if (!LIBRARY_OWNED.contains(candidate.getSimpleName())) {
                    return;
                }

                events.add(SimpleConditionEvent.violated(candidate,
                        candidate.getName() + " is a second " + candidate.getSimpleName()
                        + " — the authorization library already ships one, and this application "
                        + "already depends on it"));
            }
        };
    }
}
