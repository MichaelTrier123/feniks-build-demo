package com.example.feniksdemo;

import java.time.Instant;

public record CaseRecord(long id, String title, CaseStatus status, Instant createdAt, Instant updatedAt) {
}
