# Feniks case API

A small Java 21 / Spring Boot 3.5.16 case-management API with Spring JDBC, H2 2.3.232 and Flyway 11.7.2. Gradle 8.14.5 is included through the verified Wrapper. There is no frontend or external database service.

## Build and run

Run commands from the repository root. Install/select a JDK 21, not just a JRE. No global Gradle is needed; the first build downloads dependencies. On macOS:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew --version
./gradlew test build --no-daemon
./gradlew demoReset --no-daemon
./gradlew flywayMigrate --no-daemon
./gradlew flywayInfo --no-daemon
./gradlew bootRun --no-daemon
```

On PowerShell, first set the current shell's `JAVA_HOME` to your installed JDK 21, and put its `bin` directory first on PATH. Do not commit that local path.

```powershell
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat test build --no-daemon
.\gradlew.bat demoReset --no-daemon
.\gradlew.bat flywayMigrate --no-daemon
.\gradlew.bat flywayInfo --no-daemon
.\gradlew.bat bootRun --no-daemon
```

**Stop the app before reset or separate Flyway commands.** Wait for each command to exit: stop app → optional reset → migrate → info → start app. Ctrl+C stops `bootRun`; wait for shutdown and connection closure. A database-in-use error means another process still owns the file: stop that process normally and retry. Never delete a live database or disable its locking. Windows commands are provided but have not yet been executed on Windows.

The API listens on `127.0.0.1:8080`. HTTP 404 at `/` is expected. The built JAR can also run from the root using `java -jar build/libs/feniks-build-demo-0.0.1-SNAPSHOT.jar` with Java 21.

## HTTP API

All success bodies contain `id`, `title`, `status`, `createdAt`, `updatedAt`.

| Request | Success |
| --- | --- |
| `POST /api/cases` with `{"title":"Demo case"}` | 201 and relative Location; status OPEN |
| `GET /api/cases/{id}` | 200 with persisted case |
| `PATCH /api/cases/{id}/status` with `{"status":"CANCELLED"}` | 200 with updated case |

Titles are trimmed before validation and must contain 1–200 characters. Creation accepts only a string title. Status changes accept only one status from OPEN, IN_PROGRESS, CLOSED, CANCELLED. Unknown properties, malformed JSON, invalid statuses and invalid/non-positive/out-of-range IDs return 400. Unknown positive IDs return 404. Errors use `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`; messages are in English.

Timestamps are UTC instants ending in Z, with microsecond precision. On creation both timestamps are identical. Any status may change to any other status, including reopening. A real change preserves `createdAt` and updates `updatedAt`; repeating the current status preserves both times. There is no status history.

macOS HTTP example (use the actual Location or ID returned by POST):

```sh
curl -i -H 'Content-Type: application/json' --data '{"title":"Demo case"}' http://127.0.0.1:8080/api/cases
# After reset the first new ID is 201. Replace it if your POST returned a different ID.
curl -i http://127.0.0.1:8080/api/cases/201
curl -i -X PATCH -H 'Content-Type: application/json' --data '{"status":"CANCELLED"}' http://127.0.0.1:8080/api/cases/201/status
curl -i http://127.0.0.1:8080/api/cases/201
```

PowerShell:

```powershell
$base = 'http://127.0.0.1:8080/api/cases'
$created = Invoke-RestMethod -Method Post -Uri $base -ContentType 'application/json' -Body (@{ title = 'Demo case' } | ConvertTo-Json)
Invoke-RestMethod -Uri "$base/$($created.id)"
Invoke-RestMethod -Method Patch -Uri "$base/$($created.id)/status" -ContentType 'application/json' -Body (@{ status = 'CANCELLED' } | ConvertTo-Json)
Invoke-RestMethod -Uri "$base/$($created.id)"
```

## Data and reset

`application.properties` is the common source for the app and commands: `jdbc:h2:file:./data/feniks-demo`, migrations under `classpath:db/migration`. Keep the project-root working directory and consistent Spring environment overrides. No `AUTO_SERVER`, web database console, ORM schema generation, or SQL initializer is enabled. Flyway owns the schema; regular startup migrates but never seeds or resets it. `flywayInfo` does not apply pending migrations (opening a new H2 URL can still create an empty database file).

`demoReset` deliberately removes all existing data in the dedicated demo schema, applies the migrations in the current checkout, inserts exactly 200 fictional cases, and sets the next generated ID to 201. It temporarily enables Flyway clean only inside the guarded reset command, with an H2 connection holding the file lock. Normal startup keeps clean disabled. The command rejects alternate destinations, URL options and symbolic links; it never deletes directories or kills another process. A failed reset must be corrected and rerun before relying on the fixtures.

Fixture IDs are 1–200; titles are `Demo case 001` through `Demo case 200`. For zero-based index i, creation time is January 1, 2026 at UTC midnight plus floor(i/10) days plus (i mod 10) hours; modification time initially matches. Each day's ten records contain four OPEN, three IN_PROGRESS, two CLOSED and one CANCELLED (80/60/40/20 overall). Fixture generation lives in `DemoDatabaseReset`, outside migrations and startup.

Do not copy the database between machines. Recreate it using reset. Build output, local environment files and H2 storage are ignored by Git. Ordinary API verification can use a temporary `SPRING_DATASOURCE_URL`; reset intentionally refuses any destination outside this checkout's dedicated data path.

## Development

`CaseController` maps HTTP to `CaseService`, which uses the concrete `CaseRepository`. JDBC statements are parameterized. Request/response records stay separate from `CaseRecord`; use the injected Clock for time. `ApiExceptionHandler` supplies problem responses. `FlywayCommand` uses the same Boot context without a webserver and closes it when finished.

Tests run through the real service/repository and isolated H2. Gradle enables the test profile; IDE tests select it explicitly. Each independent context gets a unique in-memory database. File and reset tests use JUnit temporary roots only. The symlink-specific test runs on macOS/Linux; test its guard separately on Windows where symlink privileges differ. Run `./gradlew test build --no-daemon` (or the Windows wrapper) and `git diff --check` before committing. The test report is `build/reports/tests/test/index.html`.

See [tool setup](docs/tooling.md) for OpenCode, corporate MCP setup and GitHub CLI requirements.
