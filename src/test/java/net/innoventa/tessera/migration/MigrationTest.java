package net.innoventa.tessera.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole H2 migration chain against a throwaway in-memory database.
 * <p>
 * Flyway failures are otherwise only discovered at application start, which is a slow and inconvenient
 * place to learn that a seed row violates a constraint. Deliberately a bare {@link Flyway} against a
 * fresh database rather than a Spring context: no JWKS, no resource-server wiring, nothing to
 * configure — the question is only whether the SQL runs.
 */
class MigrationTest {

    @Test
    @DisplayName("every migration applies to an empty database")
    void appliesTheWholeChain() {
        assertThat(migrate().migrationsExecuted).isPositive();
    }

    @Test
    @DisplayName("the seeded presets land as ownerless GLOBAL rows the API cannot mint")
    void seedsTheGlobalPresets() throws SQLException {
        DataSource dataSource = migratedDataSource();

        List<String> names = query(dataSource, """
            SELECT name FROM saved_filters
             WHERE visibility = 'GLOBAL' AND project_id IS NULL AND owner_member_id IS NULL
             ORDER BY name
            """);

        assertThat(names).containsExactly(
            "Blocked work",
            "High priority open",
            "My open issues",
            "Open bugs",
            "Reported by me",
            "Stale over two weeks",
            "Unassigned open work"
        );
    }

    @Test
    @DisplayName("no preset's expression exceeds the length the evaluator will accept")
    void keepsPresetsWithinTheEvaluatorLimit() throws SQLException {
        List<String> overlong = query(migratedDataSource(),
            "SELECT name FROM saved_filters WHERE LENGTH(expression) > 1024");

        assertThat(overlong).isEmpty();
    }

    private Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/h2")
            .load();
    }

    private MigrateResult migrate() {
        return flyway(freshDataSource()).migrate();
    }

    private DataSource migratedDataSource() {
        DataSource dataSource = freshDataSource();
        flyway(dataSource).migrate();
        return dataSource;
    }

    /** A database named after nothing that outlives the test — never a shared or real schema. */
    private DataSource freshDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migration-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private List<String> query(DataSource dataSource, String sql) throws SQLException {
        List<String> values = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {

            while (results.next()) {
                values.add(results.getString(1));
            }
        }

        return values;
    }

}
