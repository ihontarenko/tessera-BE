package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.config.JmeConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.innoventa.tessera.service.filter.BoardFixture.BOARD;
import static net.innoventa.tessera.service.filter.BoardFixture.CALLER;
import static net.innoventa.tessera.service.filter.BoardFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeded filter presets (migration {@code V000010}), evaluated against the real engine.
 * <p>
 * These read the <strong>shipped SQL file</strong> rather than a Java copy of it, because a copy is
 * exactly what would drift: a preset row is inserted by Flyway with no service-layer validation in
 * front of it, so a predicate that fails to parse would ship silently and then be broken for every
 * member of every project. Parsing the migration is what makes that a build failure.
 */
class SavedFilterPresetTest {

    private static final String MIGRATION = "db/migration/mysql/V000010__saved_filters_seed.sql";

    private final BoardFilterEvaluator evaluator =
        new BoardFilterEvaluator(new JmeConfiguration().filterExpressionLanguage());

    /** What each preset must select from {@link BoardFixture}'s four cards. */
    private static final Map<String, Set<String>> EXPECTED = Map.of(
        "My open issues", Set.of("issue-1"),
        "Reported by me", Set.of("issue-3"),
        "Unassigned open work", Set.of("issue-3"),
        "High priority open", Set.of("issue-1", "issue-3"),
        "Open bugs", Set.of("issue-1", "issue-3"),
        "Blocked work", Set.of("issue-2"),
        "Stale over two weeks", Set.of("issue-3")
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("every seeded preset parses, returns a boolean, and selects what it claims to")
    void selectsTheDefinedCards(Preset preset) {
        assertThat(evaluator.matchingIssueIds(preset.expression(), BOARD, CALLER, NOW))
            .as("%s — %s", preset.name(), preset.expression())
            .containsExactlyInAnyOrderElementsOf(EXPECTED.get(preset.name()));
    }

    @Test
    @DisplayName("the migration and its expectations describe the same set of presets")
    void coversEverySeededPreset() {
        assertThat(presets().stream().map(Preset::name).toList())
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
    }

    @Test
    @DisplayName("presets are seeded as ownerless, project-less GLOBAL rows")
    void seedsThemAsGlobalPresets() {
        assertThat(presets()).isNotEmpty().allSatisfy(preset -> {
            assertThat(preset.visibility()).isEqualTo("GLOBAL");
            assertThat(preset.projectId()).isNull();
            assertThat(preset.ownerMemberId()).isNull();
        });
    }

    /**
     * The {@code VALUES} rows of the seed migration, one per line. Reading them back out of the file is
     * the point of these tests, so this deliberately parses rather than restates.
     */
    static List<Preset> presets() {
        return migrationLines().stream()
            .filter(line -> line.startsWith("('preset-"))
            .map(SavedFilterPresetTest::parseRow)
            .toList();
    }

    /** Column order of the seed's INSERT, so the indices below read as names rather than numbers. */
    private static final int ID = 0;
    private static final int PROJECT_ID = 1;
    private static final int OWNER_MEMBER_ID = 2;
    private static final int NAME = 3;
    private static final int EXPRESSION = 5;
    private static final int VISIBILITY = 6;

    private static Preset parseRow(String line) {
        List<String> columns = columnsOf(line);

        return new Preset(
            columns.get(ID),
            columns.get(PROJECT_ID),
            columns.get(OWNER_MEMBER_ID),
            columns.get(NAME),
            columns.get(EXPRESSION),
            columns.get(VISIBILITY)
        );
    }

    /**
     * One row's column values. SQL string literals escape a quote by doubling it, and the predicates
     * are full of them ({@code ''Bug''}), so this walks the characters rather than splitting on commas —
     * a comma inside {@code ['Highest','High']} is not a column boundary.
     */
    private static List<String> columnsOf(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideLiteral = false;
        boolean started = false;

        for (int position = line.indexOf('(') + 1; position < line.length(); position++) {
            char character = line.charAt(position);

            if (insideLiteral) {
                if (character == '\'' && position + 1 < line.length() && line.charAt(position + 1) == '\'') {
                    current.append('\'');
                    position++;
                } else if (character == '\'') {
                    insideLiteral = false;
                } else {
                    current.append(character);
                }
                continue;
            }

            if (character == '\'') {
                insideLiteral = true;
                started = true;
            } else if (character == ',' || character == ')') {
                columns.add(started ? current.toString() : null);
                current.setLength(0);
                started = false;

                if (character == ')') {
                    break;
                }
            } else if (!Character.isWhitespace(character)) {
                started = true;
                current.append(character);
            }
        }

        return columns.stream().map(value -> "NULL".equals(value) ? null : value).toList();
    }

    private static List<String> migrationLines() {
        try (InputStream stream = SavedFilterPresetTest.class.getClassLoader().getResourceAsStream(MIGRATION)) {
            if (stream == null) {
                throw new IllegalStateException("Seed migration not on the classpath: " + MIGRATION);
            }

            return List.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** One seeded row, as far as these tests care about it. */
    record Preset(
        String id,
        String projectId,
        String ownerMemberId,
        String name,
        String expression,
        String visibility
    ) {

        @Override
        public String toString() {
            return name;
        }

    }

}
