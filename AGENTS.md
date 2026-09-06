# Project instructions

Read README.md for the API contract, development commands and database lifecycle.

- Use Java 21 for the Gradle launcher and toolchain. Use the committed Wrapper: `./gradlew` on macOS/Linux, `.\gradlew.bat` on PowerShell. Run from the repository root.
- Keep the Java controller → service → concrete JDBC repository structure in `com.example.feniksdemo`. Keep request/response DTOs separate from stored records. Do not introduce ORM or generic persistence frameworks.
- Use parameterized SQL, generated IDs, and Flyway versioned migrations. Do not edit already applied migrations; add a new migration for schema changes. No schema.sql or automatic ORM DDL.
- Use the injected UTC Clock, normalize stored instants to microseconds, and preserve creation timestamps. No-op status changes preserve modification timestamps.
- Validate input at the HTTP boundary. Keep errors as concise English problem JSON without SQL or stack traces. Keep JSON fields server-owned where documented.
- Tests must use the test profile and isolated in-memory H2; file tests use temporary directories. Use real service/JDBC integration tests and controlled clocks for behavior changes.
- Run `./gradlew test build --no-daemon` and `git diff --check` (use the Windows launcher as appropriate). Inspect failures and the diff before making focused commits. Report what was actually verified.
- Stop the app before `demoReset`, `flywayMigrate` or `flywayInfo`. Execute those commands sequentially, wait for exit, then start with `bootRun`. Tests use independent databases and may run while the app runs.
- `demoReset` is destructive to the dedicated local demo data. Use it only when resetting that data is intended. Never remove a live database, disable locking, or broaden the reset path guard.
- Keep credentials, machine-specific paths, database files and generated output out of Git. Preserve existing corporate/user configuration.
- Keep changes within the assigned work package. Read its acceptance criteria before implementation; record test/HTTP evidence and any unresolved prerequisites. Do not claim external actions succeeded without verifying them.
