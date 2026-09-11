# Plan: Case statistics endpoint

## Goal

Add a read-only statistics endpoint that returns, for a user-specified period, the total number of cases created in that period and the count grouped by status (OPEN, IN_PROGRESS, CLOSED), excluding CANCELLED. Existing case functionality must remain unchanged.

## Current state

- `CaseController` (`src/main/java/com/example/feniksdemo/CaseController.java`) maps `POST /api/cases`, `GET /api/cases/{id}`, `PATCH /api/cases/{id}/status`. All request/response data flows through records; no query-param endpoints exist yet.
- `CaseService` (`.../CaseService.java`) wraps `CaseRepository`, uses the injected UTC `Clock`, truncates instants to microseconds.
- `CaseRepository` (`.../CaseRepository.java`) uses parameterized JDBC via `JdbcTemplate`; stores `created_at` as `TIMESTAMP(6) WITH TIME ZONE`. `findById` reads `OffsetDateTime` and converts to `Instant`.
- `ApiExceptionHandler` (`.../ApiExceptionHandler.java`) currently maps `TypeMismatchException` and `IllegalArgumentException` to a generic "id must be a positive 64-bit integer" 400. This is the key conflict: query-param timestamp binding failures are `MethodArgumentTypeMismatchException` (a `TypeMismatchException`) and would currently produce the wrong message.
- `CaseStatus` enum: `OPEN, IN_PROGRESS, CLOSED, CANCELLED`.
- Schema (`V1__create_cases.sql`) has `created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL` and a CHECK restricting statuses. No schema change is needed.
- Tests are real integration tests through `MockMvc` + isolated in-memory H2 (`@ActiveProfiles("test")`, `@AutoConfigureMockMvc`, `@Import` of a fixed `Clock`).

## Decisions (confirmed with user)

- Endpoint: `GET /api/cases/statistics?from=..&to=..`
- Bounds: both inclusive — `created_at >= from AND created_at <= to`
- Response shape: `{"from": "...", "to": "...", "total": N, "byStatus": {"OPEN": n, "IN_PROGRESS": n, "CLOSED": n}}`, with all three status keys always present (zero when empty) and CANCELLED excluded from both `total` and `byStatus`.

## Proposed changes

1. **Add response DTO** `CaseStatisticsResponse` in `src/main/java/com/example/feniksdemo/CaseStatisticsResponse.java`:
   ```java
   public record CaseStatisticsResponse(Instant from, Instant to, long total,
       Map<CaseStatus, Long> byStatus) { }
   ```
   Keep it separate from stored records, matching the existing DTO convention (`CaseResponse`).

2. **Add repository method** in `CaseRepository`:
   ```java
   public Map<CaseStatus, Long> countByStatusBetween(Instant from, Instant to)
   ```
   Run one parameterized query:
   ```sql
   SELECT status, COUNT(*) AS n FROM cases
   WHERE created_at >= ? AND created_at <= ? AND status <> 'CANCELLED'
   GROUP BY status
   ```
   with `setObject(..., from.atOffset(ZoneOffset.UTC))` / `to.atOffset(ZoneOffset.UTC)`. Build a `LinkedHashMap<CaseStatus, Long>` initialized to `0` for OPEN, IN_PROGRESS, CLOSED (in that order), then overwrite with actual counts. No schema change.

3. **Add service method** in `CaseService`:
   ```java
   public CaseStatisticsResponse statistics(Instant from, Instant to)
   ```
   Validate `from <= to`; if invalid throw `InvalidStatisticsPeriodException`. Otherwise call the repository and build the response, computing `total` as the sum of the three per-status counts (OPEN+IN_PROGRESS+CLOSED). Use the injected `Clock` only if timestamps need normalizing (they come from request params; no normalization required here).

4. **Add exception** `InvalidStatisticsPeriodException` in `src/main/java/com/example/feniksdemo/` (mirrors `CaseNotFoundException`).

5. **Add controller mapping** in `CaseController`:
   ```java
   @GetMapping("/statistics")
   public CaseStatisticsResponse statistics(
       @RequestParam Instant from, @RequestParam Instant to) {
       return service.statistics(from, to);
   }
   ```
   The literal `/statistics` path takes precedence over `/{id}` (Spring ranks exact path over the `{id}` pattern), so no conflict with numeric IDs.

6. **Extend `ApiExceptionHandler`** so query-param errors produce proper validation problems without breaking the existing ID handling:
   - Add `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` that checks `ex.getName()` — if it is `from` or `to`, return a 400 problem with detail like `"from and to must be valid ISO-8601 instants ending in Z."`; otherwise delegate to the existing `invalidId` logic.
   - Add `@ExceptionHandler(MissingServletRequestParameterException.class)` for a missing `from`/`to` → 400 problem `"from and to are required parameters."`
   - Add `@ExceptionHandler(InvalidStatisticsPeriodException.class)` → 400 problem `"from must not be after to."`
   - All use the existing `problem(...)` helper so the body stays `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`.
   - Leave `handleTypeMismatch`/`IllegalArgumentException` as-is so `GET /api/cases/{id}` and `PATCH .../status` still return the ID message.

7. **Add integration test** `StatisticsApiIntegrationTest` (pattern: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import` fixed `Clock`, `@BeforeEach DELETE FROM cases`). Cover:
   - happy path: insert cases at known `created_at` with a mix of statuses including CANCELLED inside the range → assert `total`, `byStatus.OPEN/IN_PROGRESS/CLOSED`, and that CANCELLED is absent and not counted in `total`.
   - zero results in range → `total` 0 and all three status keys present with 0.
   - boundary inclusivity: a case exactly at `from` and one exactly at `to` are both counted.
   - CANCELLED inside range is excluded.
   - missing `from` or `to` → 400 problem.
   - malformed timestamps (`from=abc`, wrong format, non-Z) → 400 problem.
   - `from > to` → 400 problem.
   - assert problem bodies use `application/problem+json` and contain no SQL/stack traces (reuse `assertProblem`-style checks).
   - Optionally assert `/api/cases/123` still works and `/api/cases/statistics` does not collide.

8. **Update `README.md`** HTTP API section: add the `GET /api/cases/statistics?from=..&to=..` row and a short description (period based on `created_at`, inclusive bounds, CANCELLED excluded, `byStatus` always includes OPEN/IN_PROGRESS/CLOSED). Add a PowerShell example.

9. **Verify** from the repo root:
   - `.\gradlew.bat test build --no-daemon`
   - `git diff --check`
   - Inspect the diff before committing; report what was actually verified. No schema migration, no `demoReset`, no Flyway commands are needed (no schema change).

## Risks / open questions

- **Query-param binding vs existing type-mismatch handler**: the new `MethodArgumentTypeMismatchException` handler must be more specific than the overridden `handleTypeMismatch` so timestamp errors get the right message while ID errors keep theirs. This is the main correctness risk; mitigated by the dedicated handler delegating to `invalidId` for non-from/to params.
- **Missing-parameter message**: Spring's default for `MissingServletRequestParameterException` is a non-problem body; the new handler ensures problem+json. Confirm the exact detail wording is acceptable.
- **Response field naming**: `byStatus` keys are the raw enum names (`OPEN`, `IN_PROGRESS`, `CLOSED`). Confirmed with user as the desired shape.
- **`total` derivation**: computed as the sum of the three status counts from the single grouped query. Because the DB CHECK constrains statuses to the four values and CANCELLED is filtered out, this equals a `COUNT(*)` over the period. If a separate explicit `COUNT` is preferred, that is a trivial alternative.
- **Endpoint path**: `/api/cases/statistics` does not collide with `/{id}` because Spring prefers the exact literal path. Confirmed no conflict.
