package net.innoventa.tessera.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.innoventa.tessera.security.access.AiManagementAccess;
import net.innoventa.tessera.security.access.AttachmentsAccess;
import net.innoventa.tessera.security.access.AvatarAccess;
import net.innoventa.tessera.security.access.LiveBlocksAccess;
import org.jmouse.access.enforcement.ExternalAccessRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>A handler this application serves but did not write still says what it needs.</strong>
 *
 * <p>{@code AccessDeclarationsTest} reads {@code net.innoventa.tessera}, which is the whole of the
 * application right up until a library ships controllers. {@code jmouse-ai-management} ships six; they
 * carry no annotation, because a library's handler cannot. An un-gated one would serve the tool
 * catalogue and everybody's usage totals to any signed-in caller, and nothing anywhere would say so.
 *
 * <p>⚠️ <strong>It checks the library's classes rather than how Tessera happens to mount them.</strong>
 * The obvious rule — "every {@code @Bean} method returning a foreign controller is declared about" —
 * passes here today and would go silently vacuous the moment those beans came from an auto-configuration
 * instead, which is exactly how Innoventa mounts the same six. A check that stops checking when the
 * wiring changes is worse than none.
 *
 * <p>It instantiates the declaration rather than asking a Spring context for it: a rule that needed the
 * application to start would be a rule checked only where it is least useful.
 */
class ForeignControllerAccessTest {

    /** Every module whose controllers this application mounts. One entry per adopted library. */
    private static final List<String> FOREIGN_CONTROLLER_PACKAGES =
            List.of("org.jmouse.ai.management", "org.jmouse.liveblocks.web", "org.jmouse.avatar",
                    "org.jmouse.files.management", "org.jmouse.storage.administration");

    private static JavaClasses foreignControllers;

    @BeforeAll
    static void importTheForeignControllers() {
        foreignControllers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(FOREIGN_CONTROLLER_PACKAGES);
    }

    @Test
    @DisplayName("every controller Tessera mounts but did not write is declared about")
    void everyForeignControllerIsDeclaredAbout() {
        ExternalAccessRules declared = ExternalAccessRules.all(List.of(
                new AiManagementAccess().aiManagementAccessRules(),
                new LiveBlocksAccess().liveBlocksAccessRules(),
                new AvatarAccess().avatarAccessRules(),
                new AttachmentsAccess().attachmentAccessRules()));

        List<String> undeclared = foreignControllers.stream()
                .filter(candidate -> candidate.getSimpleName().endsWith("Controller"))
                .filter(candidate -> !declared.covers(candidate.reflect()))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(undeclared)
                .describedAs("Controllers mounted from a library with no ExternalAccessRules declaration "
                           + "naming them. A library's handler cannot carry @RequiresAccess, so state the "
                           + "requirement about it in AiManagementAccess — otherwise it is served to any "
                           + "signed-in caller with nothing gating it.")
                .isEmpty();
    }

    /**
     * ⚠️ The rule above is vacuously true if nothing was imported — a renamed package, a module dropped
     * from the pom — and a passing empty check is worse than no check.
     */
    @Test
    @DisplayName("the foreign controllers were actually found")
    void theForeignControllersWereFound() {
        assertThat(foreignControllers.stream()
                .filter(candidate -> candidate.getSimpleName().endsWith("Controller"))
                .toList())
                .describedAs("No controller was imported from %s, so the check above proved nothing. "
                           + "Either the module is gone from the pom or its package was renamed.",
                        FOREIGN_CONTROLLER_PACKAGES)
                .isNotEmpty();
    }
}
