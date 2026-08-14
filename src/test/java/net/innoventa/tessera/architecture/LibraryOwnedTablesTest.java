package net.innoventa.tessera.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * <strong>Whoever owns the table owns the mapping.</strong>
 *
 * <p>Two libraries keep rows in this service's database and ship the entities for them:
 * {@code jmouse-access-jpa} owns every {@code access_*} table and {@code jmouse-ai-jpa} owns the
 * {@code ai_*} ones. A Tessera {@code @Entity} over one of those would compile, start, and be kept
 * honest only by {@code ddl-auto: validate} — which is a hope rather than a contract: it checks that the
 * columns a mapping names exist, never that the mapping is the right one, and it says nothing at all
 * about a column the library adds later.
 *
 * <p>The failure mode when the two drift is the worst available. A product entity that has fallen a
 * release behind reads the table successfully and writes it wrongly — an assignment missing its
 * condition, a grant missing its provenance — and nothing anywhere reports it.
 *
 * <p>So the rule: no {@code @Entity} in this codebase maps a table with either prefix. Reading those
 * rows is what the libraries' own stores and ports are for.
 */
@AnalyzeClasses(packages = "net.innoventa.tessera", importOptions = ImportOption.DoNotIncludeTests.class)
class LibraryOwnedTablesTest {

    /** Every prefix a library has claimed, with the library that claimed it. */
    private static final List<Claim> CLAIMED = List.of(
            new Claim("access_", "jmouse-access-jpa"),
            new Claim("ai_", "jmouse-ai-jpa"));

    @ArchTest
    static final ArchRule no_entity_maps_a_library_owned_table =
            classes().that().areAnnotatedWith(Entity.class)
                    .should(mapOnlyThisProductsTables());

    private static ArchCondition<JavaClass> mapOnlyThisProductsTables() {
        return new ArchCondition<>("map only tables this product owns") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String table = tableOf(entity);

                CLAIMED.stream()
                        .filter(claim -> table.startsWith(claim.prefix()))
                        .findFirst()
                        .ifPresentOrElse(
                                claim -> events.add(SimpleConditionEvent.violated(entity,
                                        entity.getName() + " maps '" + table + "', which belongs to "
                                        + claim.library() + ". That library ships the entity and the "
                                        + "migration for it; a mapping here is kept honest only by "
                                        + "ddl-auto: validate, which cannot tell a correct mapping from "
                                        + "a plausible one. Read the rows through the library's own "
                                        + "store or administration port instead.")),
                                () -> events.add(SimpleConditionEvent.satisfied(entity, table)));
            }
        };
    }

    /**
     * The table an entity maps.
     *
     * <p>⚠️ An entity with no {@code @Table} takes its class name, which Hibernate then turns into a
     * table name by whatever naming strategy is configured — so this cannot be exact. It does not have to
     * be: every one of the claimed prefixes is written out explicitly by the libraries, so anybody
     * mapping one of those tables by accident has typed the prefix.
     */
    private static String tableOf(JavaClass entity) {
        return entity.isAnnotatedWith(Table.class)
                ? entity.getAnnotationOfType(Table.class).name()
                : entity.getSimpleName();
    }

    private record Claim(String prefix, String library) {}
}
