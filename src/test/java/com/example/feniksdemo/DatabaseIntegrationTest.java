package com.example.feniksdemo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseIntegrationTest {

    private static final String INSERT = """
            INSERT INTO cases (title, status, created_at, updated_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String TIME = "2026-01-01T00:00:00Z";

    @Test
    void startupMigratesOnceAndSupportsExplicitIdsAndIdentityRestart() {
        try (var context = start()) {
            var jdbc = jdbc(context);
            var flyway = context.getBean(Flyway.class);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
            jdbc.update(INSERT, "Generated", "OPEN", TIME, TIME);
            assertThat(jdbc.queryForObject("SELECT id FROM cases", Long.class)).isEqualTo(1L);
            jdbc.update("""
                    INSERT INTO cases (id, title, status, created_at, updated_at)
                    VALUES (200, 'Explicit', 'CLOSED', ?, ?)
                    """, TIME, TIME);
            jdbc.execute("ALTER TABLE cases ALTER COLUMN id RESTART WITH 201");
            jdbc.update(INSERT, "After restart", "OPEN", TIME, TIME);
            assertThat(jdbc.queryForObject("SELECT MAX(id) FROM cases", Long.class)).isEqualTo(201L);

            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isEqualTo(3L);
            assertThat(flyway.info().applied()).hasSize(1);
        }
    }

    @Test
    void schemaEnforcesRequiredFieldsTitleLengthStatusAndPositiveUniqueIds() {
        try (var context = start()) {
            var jdbc = jdbc(context);
            for (String status : new String[] {"OPEN", "IN_PROGRESS", "CLOSED", "CANCELLED"}) {
                jdbc.update(INSERT, "x".repeat(200), status, TIME, TIME);
            }
            Object[][] invalidRows = {
                    {null, "OPEN", TIME, TIME},
                    {"", "OPEN", TIME, TIME},
                    {"   ", "OPEN", TIME, TIME},
                    {"x".repeat(201), "OPEN", TIME, TIME},
                    {"Title", null, TIME, TIME},
                    {"Title", "UNKNOWN", TIME, TIME},
                    {"Title", "open", TIME, TIME},
                    {"Title", "OPEN", null, TIME},
                    {"Title", "OPEN", TIME, null}
            };
            for (Object[] row : invalidRows) {
                assertThatThrownBy(() -> jdbc.update(INSERT, row))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
            for (Long id : new Long[] {null, 0L, -1L, 1L}) {
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO cases (id, title, status, created_at, updated_at)
                        VALUES (?, 'Invalid ID', 'OPEN', ?, ?)
                        """, id, TIME, TIME)).isInstanceOf(DataIntegrityViolationException.class);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isEqualTo(4L);
        }
    }

    @Test
    void independentTestContextsUseDifferentMemoryDatabases() throws Exception {
        try (var first = start(); var second = start()) {
            try (var a = first.getBean(DataSource.class).getConnection();
                    var b = second.getBean(DataSource.class).getConnection()) {
                assertThat(a.getMetaData().getURL()).startsWith("jdbc:h2:mem:")
                        .isNotEqualTo(b.getMetaData().getURL());
                assertThat(b.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
            }
            jdbc(first).update(INSERT, "Only in first", "OPEN", TIME, TIME);
            assertThat(jdbc(second).queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isZero();
        }
    }

    @Test
    void fileDataSurvivesClosingAndReopeningTheApplication(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("cases-" + UUID.randomUUID());
        try (var context = start("--spring.datasource.url=" + url)) {
            jdbc(context).update(INSERT, "Persistent", "CANCELLED", TIME, TIME);
        }
        try (var context = start("--spring.datasource.url=" + url)) {
            assertThat(jdbc(context).queryForMap("SELECT title, status, created_at FROM cases"))
                    .containsEntry("TITLE", "Persistent")
                    .containsEntry("STATUS", "CANCELLED")
                    .containsEntry("CREATED_AT", java.time.OffsetDateTime.parse(TIME));
            assertThat(context.getBean(Flyway.class).migrate().migrationsExecuted).isZero();
            assertThat(context.getBean(Flyway.class).info().applied()).hasSize(1);
        }
    }

    private ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(FeniksDemoApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(args);
    }

    private JdbcTemplate jdbc(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class);
    }
}
