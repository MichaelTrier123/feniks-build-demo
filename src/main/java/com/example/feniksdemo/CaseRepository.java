package com.example.feniksdemo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {
    private final JdbcTemplate jdbc;

    public CaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CaseRecord create(String title, CaseStatus status, Instant timestamp) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO cases (title, status, created_at, updated_at) VALUES (?, ?, ?, ?)
                    """, new String[] {"id"});
            statement.setString(1, title);
            statement.setString(2, status.name());
            statement.setObject(3, timestamp.atOffset(ZoneOffset.UTC));
            statement.setObject(4, timestamp.atOffset(ZoneOffset.UTC));
            return statement;
        }, keys);
        return new CaseRecord(keys.getKey().longValue(), title, status, timestamp, timestamp);
    }

    public CaseRecord changeStatus(long id, CaseStatus status, Instant timestamp) {
        jdbc.update("""
                UPDATE cases SET status = ?, updated_at = ? WHERE id = ? AND status <> ?
                """, status.name(), timestamp.atOffset(ZoneOffset.UTC), id, status.name());
        return findById(id).orElseThrow(CaseNotFoundException::new);
    }

    public Optional<CaseRecord> findById(long id) {
        return jdbc.query("""
                SELECT id, title, status, created_at, updated_at FROM cases WHERE id = ?
                """, (row, index) -> new CaseRecord(
                        row.getLong("id"), row.getString("title"), CaseStatus.valueOf(row.getString("status")),
                        row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        row.getObject("updated_at", OffsetDateTime.class).toInstant()), id).stream().findFirst();
    }
}
