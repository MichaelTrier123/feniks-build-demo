package com.example.feniksdemo;

import java.time.Instant;

public record CaseResponse(long id, String title, CaseStatus status, Instant createdAt, Instant updatedAt) {
    static CaseResponse from(CaseRecord record) {
        return new CaseResponse(record.id(), record.title(), record.status(), record.createdAt(), record.updatedAt());
    }
}
