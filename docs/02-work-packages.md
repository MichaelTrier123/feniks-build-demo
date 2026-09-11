# Toolkit work packages — statistics change request

Create all five items under the real parent case. Names, descriptions, acceptance criteria and DoD below are English Toolkit content. Replace only actual IDs/relationships; do not invent system fields or statuses. No package is automatically complete merely because it has been created.

These packages replace the responsibility split in the earlier standalone preparation drafts. WP-03 owns implementation and its verification, WP-04 owns the acceptance report, and WP-05 owns external PR delivery. Shared evidence can be linked rather than repeated. Human review/acceptance remains a parent-case gate.

## WP-01 — Confirm the statistics API contract

### Description

Confirm and record the requested statistics behavior against the existing case API. Use the full WP-03 specification in this document as the proposed contract. Resolve ambiguities without expanding scope; do not implement code.

### Dependencies

The parent case and the supplied WP-03 specification are available.

### Acceptance criteria

1. Record the endpoint, exact query-parameter rules, response fields and problem-response contract.
2. Explicitly distinguish creation-time filtering from current-status grouping; define inclusive/exclusive bounds and cancellation exclusion.
3. Record full-fixture, single-day, empty-period and cancellation examples with expected counts.
4. Record the V2 index requirement and confirm that frontend work, new infrastructure and historical status reporting are excluded.
5. Capture unresolved questions, or explicitly record that the supplied contract is sufficiently defined. Escalate only decisions that would change scope or the agreed API contract.

### Definition of Done

The contract and decisions are saved to Toolkit and reread to confirm persistence. WP-03 is linked as the implementation package. No unresolved product decision prevents implementation. No code change or build result is required for this analysis package.

### Expected evidence

Actual case/package IDs, recorded contract, identified decisions and links to dependent work.

## WP-02 — Verify the baseline and development environment

### Description

Inspect the intended baseline and confirm that the target machine can build and run it before implementation. Read AGENTS.md and README.md, identify the controller → service → JDBC structure and shared database configuration, and document a small implementation plan. Do not implement statistics.

### Dependencies

WP-01; repository access and the target machine's approved Java/OpenCode environment.

### Acceptance criteria

1. Record repository URL, branch, commit, Git status and relationship to baseline tag feniks-baseline. Preserve existing work and report divergence rather than overwriting it.
2. Verify Java 21 for both Gradle launcher and toolchain, Gradle 8.14.5 through the Wrapper, and a successful full test/build run. Record actual test counts, including skips.
3. With the app stopped, initialize the dedicated disposable demo data using demoReset, then run flywayMigrate and flywayInfo sequentially. Confirm the baseline contains V1 and no statistics implementation or V2.
4. Start the app, verify create → fetch → cancel → fetch, then stop it cleanly. Reset the intended disposable demo data again and leave a populated V1 database ready for the later upgrade demonstration.
5. Identify relevant classes and planned changes, test approach and migration verification approach without adding generic infrastructure.
6. Report actual Toolkit read/write access and GitHub feature-branch/PR prerequisites; do not infer write access from public clone access. Record missing access precisely.

### Definition of Done

Baseline evidence and the scoped implementation plan are stored in Toolkit and reread. The local build/runtime checks pass and all started processes are stopped. External prerequisites are explicitly recorded; an unavailable PR integration can remain a blocker for WP-05 without blocking local WP-03 implementation.

### Expected evidence

Actual OS and runtime versions, commit/status, test report summary, commands/outcomes, V1 migration status, HTTP results, stopped-process confirmation and access limitations.

## WP-03 — Add case statistics for a creation-time period

### Description

Implement a read-only statistics endpoint using the existing controller → service → concrete JDBC repository pattern. Preserve existing operations, error conventions, database separation and migrations. Use separate response DTOs and parameterized SQL. Use a single aggregate query so total and groups describe the same database result.

`GET /api/cases/statistics?from=2026-01-01T00:00:00Z&to=2026-01-21T00:00:00Z`

Both query parameters are required exactly once. Accept ISO-8601 UTC timestamps ending in Z, with optional fractional seconds of at most six digits. Reject date-only/local values, offsets other than the required Z notation, invalid calendar values, unsupported precision, missing/repeated parameters, and from >= to. Return HTTP 400, application/problem+json, with type=about:blank, title, status, actionable English detail and request-path instance. Never expose SQL or stack traces.

Filter using created_at >= from and created_at < to. Count current OPEN, IN_PROGRESS and CLOSED; exclude CANCELLED from total and groups, with no CANCELLED response key. Include all three eligible statuses even when zero. Counts are non-negative 64-bit values. Echo normalized UTC bounds ending in Z.

Full reset fixture response:

```json
{
  "from": "2026-01-01T00:00:00Z",
  "to": "2026-01-21T00:00:00Z",
  "total": 180,
  "byStatus": {"OPEN": 80, "IN_PROGRESS": 60, "CLOSED": 40}
}
```

Add V2__add_cases_created_at_index.sql creating idx_cases_created_at on cases(created_at). Do not edit V1. No measured speedup is required or claimed for this small dataset.

### Dependencies

WP-01 contract; WP-02 verified local baseline. Use the real Toolkit IDs. External delivery access is needed for WP-05, not for local implementation.

### Acceptance criteria

1. Valid periods return HTTP 200 and exactly from, to, total and byStatus with OPEN, IN_PROGRESS and CLOSED. Total equals the sum of groups, including under concurrent changes by deriving counts from one aggregate result.
2. January 1–21, 2026 returns total 180 / OPEN 80 / IN_PROGRESS 60 / CLOSED 40. January 1–2 returns 9 / 4 / 3 / 2.
3. Real service/JDBC integration tests prove inclusion at from and exclusion at to, including microsecond boundaries. Creation time, not modification time, controls inclusion.
4. Empty and cancelled-only periods return all zero counts. A missing status in an otherwise populated period is returned with zero.
5. Tests cover every invalid parameter category in the description and the existing problem-response fields/media type. Existing GET-by-ID routing still works alongside /statistics.
6. Status is evaluated at query time. Cancelling an included case removes it from the period's subsequent counts, even when updatedAt is outside the selected interval.
7. Actual HTTP verification creates a case in a current interval, fetches statistics, cancels it and fetches statistics using exactly the same bounds. Verify the actual createdAt is within those bounds. Total and OPEN decrease by one; after reset and with an interval excluding fixtures, demonstrate 1 → 0.
8. V2 applies on a populated V1 database and a fresh database, preserves existing rows and creates the named index. Repeated migrate makes no further change. Stop the app before separate file-database migration commands.
9. Targeted tests use isolated H2 and controlled clocks with real service/JDBC behavior. File tests use temporary directories. The full regression suite and build pass; tests neither create nor modify the normal demo database.
10. Preserve baseline API behavior and Flyway ownership. No frontend, unrelated endpoints, new infrastructure, generic framework, fixture changes or deliberately introduced defect is included.

### Definition of Done

All acceptance criteria are verified. The agent has inspected the diff, run git diff --check, made focused local commits and updated relevant project documentation. Record the actual verified commit, tests/build, migration and HTTP evidence in Toolkit and reread the result. Stop started processes. The implementation is ready for acceptance verification and review handoff; agent self-review is not human approval. PR publication belongs to WP-05 and is not a circular prerequisite for WP-03 completion.

### Expected evidence

Actual case/package IDs and branch/commit; changed files; Java/Gradle/OS; exact commands and test counts including skips; fresh/V1-upgrade/repeated migration outcomes with row preservation and index presence; representative successful/invalid HTTP responses; actual created ID and createdAt, identical from/to values, cancellation response and before/after counts; test database isolation and process shutdown confirmation.

## WP-04 — Verify statistics acceptance and regression behavior

### Description

Assess the WP-03 delivery against the agreed contract and produce a concise acceptance verification report. Inspect linked WP-03 evidence and rerun checks needed to substantiate the actual delivery commit. Reuse valid evidence from that same commit; do not recreate implementation tests merely for a second checklist. This is verification, not independent human code approval.

### Dependencies

WP-03's implementation commit and evidence are available.

### Acceptance criteria

1. Map every WP-03 acceptance criterion to evidence for the delivered commit, with pass/fail/blocked outcomes.
2. Confirm full/partial/empty periods, problem responses, unchanged GET-by-ID behavior and the same-period cancellation demonstration.
3. Confirm fresh database initialization, populated V1 upgrade, row preservation, index presence and repeated migration. Do not present demoReset as proof of an in-place upgrade.
4. Confirm full regression/build results, actual platform, test isolation and process shutdown.
5. Record defects against the implementation package. If fixes change the commit, repeat affected checks and update the evidence references; do not mark failed criteria accepted.

### Definition of Done

All required checks pass for the delivered commit and the acceptance verification report is saved and reread in Toolkit. Unresolved acceptance failures keep the package open or blocked using available Toolkit states. The report is ready for the customer/reviewer; it does not claim their approval.

### Expected evidence

Criterion-to-evidence table, exact commit, referenced/rerun commands, HTTP/migration results, issue dispositions and actual platform limitations.

## WP-05 — Author manual test cases for the case statistics change

### Description

Author a set of manual, black-box test cases in the Testcases list covering the full observable behavior of the case statistics change (the new period-based statistics and its validation, filtering, grouping, exclusion and error handling). The test cases are written in customer-facing language and assume the accompanying frontend; they are prepared as a quality-assurance artifact for the customer to run later and are not executed as part of this package.

### Dependencies

WP-03 (the delivered statistics endpoint) and the WP-01 contract as the source of expected behavior.

### Acceptance criteria

1. Test cases are created in the Testcases list and linked to the parent case, covering: full-period counts, single-day counts, an empty period, cancelled-case exclusion, creation-time (not modification-time) inclusion, and rejection of a nonsensical period.
2. Each test case states a goal, customer-facing steps, and an expected result, and assumes the frontend rather than a raw API client.
3. The test cases are ready for a customer to run without further authoring and do not require a specific tool such as Postman.
4. No test case is executed as part of this package; they are prepared-for-QA, not run.

### Definition of Done

The test cases are saved to the Testcases list and reread to confirm persistence, linked to the parent case, and scoped to the whole change. No test case is executed.

### Expected evidence

Actual Testcases item IDs, the linked parent case, and the list of authored test cases.