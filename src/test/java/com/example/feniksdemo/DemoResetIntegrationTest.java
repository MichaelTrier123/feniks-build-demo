package com.example.feniksdemo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

class DemoResetIntegrationTest {
    @TempDir Path root;

    private Flyway flyway() {
        return Flyway.configure().dataSource("jdbc:h2:file:" + root.resolve("data/feniks-demo"), "", "")
                .locations("classpath:db/migration").load();
    }

    @Test
    void resetRestoresAllFixturesAndNextIdAcrossReopen() throws Exception {
        var flyway = flyway();
        String url = flyway.getConfiguration().getUrl();
        DemoDatabaseReset.reset(flyway, url, root);
        var jdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        List<Map<String, Object>> original = jdbc.queryForList("SELECT * FROM cases ORDER BY id");
        assertThat(original).hasSize(200);
        for (int i = 0; i < 200; i++) {
            var row = original.get(i);
            assertThat(row.get("ID")).isEqualTo((long) i + 1);
            assertThat(row.get("TITLE")).isEqualTo(String.format(java.util.Locale.ROOT, "Demo case %03d", i + 1));
            var time = OffsetDateTime.parse("2026-01-01T00:00:00Z").plusDays(i / 10).plusHours(i % 10);
            assertThat(row.get("CREATED_AT")).isEqualTo(time);
            assertThat(row.get("UPDATED_AT")).isEqualTo(time);
        }
        assertThat(jdbc.queryForList("SELECT status, COUNT(*) AS n FROM cases GROUP BY status ORDER BY status"))
                .containsExactly(Map.of("STATUS", "CANCELLED", "N", 20L), Map.of("STATUS", "CLOSED", "N", 40L),
                        Map.of("STATUS", "IN_PROGRESS", "N", 60L), Map.of("STATUS", "OPEN", "N", 80L));
        jdbc.update("UPDATE cases SET status = 'CANCELLED' WHERE id = 1");
        jdbc.update("INSERT INTO cases (title,status,created_at,updated_at) VALUES ('New','OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM cases", Long.class)).isEqualTo(201L);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM cases WHERE id = 1", String.class)).isEqualTo("CANCELLED");
        DemoDatabaseReset.reset(flyway, url, root);
        assertThat(jdbc.queryForList("SELECT * FROM cases ORDER BY id")).isEqualTo(original);
        jdbc.update("INSERT INTO cases (title,status,created_at,updated_at) VALUES ('Next','OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM cases", Long.class)).isEqualTo(201L);
    }

    @Test
    void rejectsOtherDatabasesOptionsAndLinksWithoutChangingFiles() throws Exception {
        for (String url : List.of("jdbc:h2:mem:test", "jdbc:h2:tcp://localhost/demo",
                "jdbc:h2:file:./elsewhere", "jdbc:h2:file:./data/feniks-demo;AUTO_SERVER=TRUE",
                "jdbc:h2:file:" + root.resolve("other"))) {
            assertThatThrownBy(() -> DemoDatabaseReset.validateTarget(url, root)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.MAC, org.junit.jupiter.api.condition.OS.LINUX})
    void rejectsLinkedDataDirectory() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("data"), outside);
        assertThatThrownBy(() -> DemoDatabaseReset.validateTarget("jdbc:h2:file:./data/feniks-demo", root))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(outside)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void refusesDatabaseHeldByAnotherProcess() throws Exception {
        var flyway = flyway();
        String url = flyway.getConfiguration().getUrl();
        DemoDatabaseReset.reset(flyway, url, root);
        Path ready = root.resolve("ready");
        String h2Jar = Path.of(Class.forName("org.h2.Driver").getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String testClasses = Path.of(LockHolder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", testClasses + java.io.File.pathSeparator + h2Jar, LockHolder.class.getName(), url, ready.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("lock.log").toFile()).start();
        try {
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertThat(Files.exists(ready)).as(Files.readString(root.resolve("lock.log"))).isTrue();
            assertThatThrownBy(() -> DemoDatabaseReset.reset(flyway, url, root)).isInstanceOf(java.sql.SQLException.class);
        } finally {
            process.destroy();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
            }
        }
        var jdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isEqualTo(200L);
    }

    public static class LockHolder {
        public static void main(String[] args) throws Exception {
            try (var connection = DriverManager.getConnection(args[0], "", "")) {
                Files.writeString(Path.of(args[1]), "ready");
                System.in.read();
            }
        }
    }
}
