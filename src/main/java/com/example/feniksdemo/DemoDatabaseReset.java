package com.example.feniksdemo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.flywaydb.core.Flyway;

final class DemoDatabaseReset {
    private DemoDatabaseReset() { }

    static void reset(Flyway flyway, String url, Path projectRoot) throws IOException, SQLException {
        validateTarget(url, projectRoot);
        // Keep a connection open throughout reset: embedded H2 retains the file lock.
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            validateTarget(connection.getMetaData().getURL(), projectRoot);
            var resetFlyway = Flyway.configure().configuration(flyway.getConfiguration()).cleanDisabled(false).load();
            resetFlyway.clean();
            resetFlyway.migrate();
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO cases (id, title, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (int i = 0; i < 200; i++) {
                    int slot = i % 10;
                    var createdAt = Instant.parse("2026-01-01T00:00:00Z")
                            .plus(i / 10, ChronoUnit.DAYS).plus(slot, ChronoUnit.HOURS).atOffset(ZoneOffset.UTC);
                    String status = slot < 4 ? "OPEN" : slot < 7 ? "IN_PROGRESS" : slot < 9 ? "CLOSED" : "CANCELLED";
                    insert.setLong(1, i + 1);
                    insert.setString(2, String.format(Locale.ROOT, "Demo case %03d", i + 1));
                    insert.setString(3, status);
                    insert.setObject(4, createdAt);
                    insert.setObject(5, createdAt);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE cases ALTER COLUMN id RESTART WITH 201");
            }
            System.out.println("Demo database reset: 200 cases (OPEN 80, IN_PROGRESS 60, CLOSED 40, CANCELLED 20).");
        }
    }

    static void validateTarget(String url, Path projectRoot) throws IOException {
        String prefix = "jdbc:h2:file:";
        if (url == null || !url.startsWith(prefix) || url.contains(";")) {
            throw new IllegalArgumentException("Reset requires the dedicated embedded demo file URL without options.");
        }
        Path root = projectRoot.toRealPath();
        Path declaredRoot = projectRoot.toAbsolutePath().normalize();
        Path target = declaredRoot.resolve(url.substring(prefix.length())).normalize();
        Path expected = declaredRoot.resolve("data/feniks-demo");
        if ((!target.equals(expected) && !target.equals(root.resolve("data/feniks-demo")))
                || Files.isSymbolicLink(root.resolve("data"))
                || Files.isSymbolicLink(Path.of(target + ".mv.db"))) {
            throw new IllegalArgumentException("Reset is restricted to data/feniks-demo in this project; links are not allowed.");
        }
    }
}
