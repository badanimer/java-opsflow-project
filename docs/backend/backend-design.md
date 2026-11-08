# OpsFlow Backend

## Purpose

This document describes the backend as it is built now: the stack, the layout of the project, how it is configured, and how it is run. It is the current-state document of the backend area. The database it connects to is described in [database-design.md](../database/database-design.md), and the pages it serves in [page-design.md](../frontend/page-design.md).

## Change Log

This document always describes the current backend. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-09 | 1.3 | The shared foundations in `common`: the error handler, paging, the retry component, UTC time helpers, the base path, the idempotency key check, the length limits, and the operating parameters in configuration | [backend-v1.3.md](../iterations/backend-v1.3.md) |
| 2026-11-05 | 1.2 | The application tests are logged in the test log, in a section of their own | [backend-v1.2.md](../iterations/backend-v1.2.md) |
| 2026-11-04 | 1.1 | The persistence layer is `JdbcTemplate`, transactions use `@Transactional`, and the code is organized by feature | [backend-v1.1.md](../iterations/backend-v1.1.md) |
| 2026-11-03 | 1.0 | The Spring Boot skeleton: Maven project in `backend/`, Flyway and the MySQL driver, configuration from environment variables, and the migrations moved into the project | [backend-v1.0.md](../iterations/backend-v1.0.md) |

## Stack

| Item | Choice |
| --- | --- |
| Language and runtime | Java 17 |
| Framework | Spring Boot 4.0.9 (Spring Initializr no longer offers 3.5.x) |
| Build | Maven, through the wrapper `backend/mvnw` |
| Web | `spring-boot-starter-webmvc` |
| Validation | `spring-boot-starter-validation` |
| Database access | Plain JDBC through `JdbcTemplate` (`spring-boot-starter-jdbc`), with the SQL written in the code. No JPA and no MyBatis |
| Transactions | `@Transactional` on service methods. See Transactions |
| Migrations | Flyway (`spring-boot-starter-flyway` and `flyway-mysql`) |
| Driver | `mysql-connector-j` |

Not added yet: security, Redis, RabbitMQ, and mail.

## Layout

| Path | Content |
| --- | --- |
| `backend/pom.xml` | The build and the dependencies |
| `backend/src/main/java/com/opsflow` | The code. The root package is `com.opsflow`. Inside it there is one package for each module (`order`, `inventory`, `ticket`, `user`, and so on) and a `common` package for error handling, configuration, and time helpers |
| `backend/src/main/resources/application.yml` | The configuration, with no secrets |
| `backend/src/main/resources/db/migration` | The Flyway migrations, applied in version order. Never edit one that has been applied: add a new one |

The code is organized by feature, not by layer. Inside a module the classes keep their layer in the name (`OrderController`, `OrderService`, `OrderRepository`). A module does not call the repository of another module: it calls the service. The write path of an order touches the stock and the product tables, and all of its SQL stays in the order module, so that the lock order of [Concurrency by Module](../database/database-design.md#concurrency-by-module) can be read in one place.

## Shared Code

The `common` package holds what every module uses. A module calls it and never copies it.

| Package | Class | Purpose |
| --- | --- | --- |
| `common.error` | `ErrorCode`, `ApiException`, `ApiExceptionHandler` | `ErrorCode` is the table of result codes with a status and a title each. A service refuses a request by throwing `ApiException` with a code, a detail for people, and extra fields such as `orderNo`. `ApiExceptionHandler` is the one place that turns an exception into the Problem Details body of [api-design.md](../api/api-design.md#error-response): it also answers the errors Spring MVC raises itself (unreadable body, missing parameter, unknown path, wrong method) and a failed validation, with an `errors` list. An unexpected exception is logged with its stack trace and answered with `INTERNAL_ERROR` and no detail, so no SQL, secret, or stack trace leaves the server |
| `common.paging` | `Cursor`, `CursorCodec`, `PageQuery`, `PageQueryFactory`, `PageResponse` | Paging by key. `PageQueryFactory.create` checks `limit`, `cursor`, `direction`, and `order` and refuses a bad value with `VALIDATION_FAILED`. A repository reads `PageQuery.fetchSize()` rows (one more than the page) in the direction of `PageQuery.ascendingRead()`, and `PageResponse.of` cuts the page, restores the order of a backward read, and makes the two cursors. The sort key and the `id` of the edge row make the cursor |
| `common.retry` | `TransactionRetry` | Calls a `@Transactional` method and retries it on a deadlock or a lock wait timeout. See Transactions |
| `common.time` | `UtcTime` | Converts between a `DATETIME(3)` value (a UTC wall time) and an `Instant`. See Time |
| `common.web` | `WebConfig`, `IdempotencyKey` | `WebConfig` serves every `@RestController` under `/api/v1` and provides the one `Clock` bean, in UTC. `IdempotencyKey.parse` reads the `Idempotency-Key` header |
| `common.validation` | `Limits` | The length of every text field a request can set, equal to its column (T-13). `@Size(max = Limits.ITEM_NAME)` |
| `common.config` | `OpsflowProperties` | The operating parameters, bound from `opsflow.*`. See Configuration |

A controller declares its path without the prefix (`@RequestMapping("/orders")`) and takes the paging parameters as plain request parameters that it passes to `PageQueryFactory`.

### Time

Java handles every time in UTC. Code takes "now" from the `Clock` bean and never calls `LocalDateTime.now()`, `ZoneId.systemDefault()`, `new Date()`, and the like, which read the time zone of the machine; `UtcSourceRuleTest` fails the build when one appears in `src/main/java`. A `DATETIME(3)` column is read with `rs.getObject(column, LocalDateTime.class)` and converted with `UtcTime.toInstant`, and written with `UtcTime.toLocalDateTime`. The API sends an `Instant`, which Jackson writes in ISO 8601 with a trailing `Z`. A calendar day in Sydney time is converted to a UTC range at the edge of the query (see the logs in the database design).

### Length limits

A request type declares the length of a text field with a constant of `Limits`, so that over-long input is refused with `VALIDATION_FAILED` and never truncated. `LimitsMatchSchemaTest` reads the length of each column from the database that Flyway built and compares it with the constant, so a migration that changes a length fails the build until the constant follows. A new text field adds a constant and a line in that test. The `TEXT` columns (`tickets.description` and `tickets.resolution`) have no constant yet: their limit is set when the ticket module is built.

## Transactions

Transactions are declared with `@Transactional` on service methods. The annotation works through a proxy, which has two consequences for the retry rules of the order flow:

- A retry loop never sits in the method that carries `@Transactional`. `TransactionRetry` sits outside it, calls that method, and retries on a deadlock or a lock wait (MySQL errors 1213 and 1205), so every attempt is a new transaction and the earlier one has been rolled back by the proxy. It refuses to run inside a transaction. Any other exception, a refusal of the API included, goes to the caller at once. When the last attempt fails too it throws `ApiException` with `BUSY`, with the last failure as its cause, and a caller that must record that outcome (the order module writes its `BUSY` row) catches it. The wait before a retry is the configured delay plus a random extra of up to half of it. The number of attempts and the delays come from application configuration (T-11).
- A write that must happen after a rollback, such as the `order_attempts` rows of a refused order, is made in a method of another bean with `Propagation.REQUIRES_NEW`. A call from one method of a class to another method of the same class does not pass through the proxy, so the annotation would do nothing.

The HTTP conventions of the controllers (paths, error responses, result codes, paging) are in [api-design.md](../api/api-design.md).

## Tests

Tests have three layers:

| Layer | What it covers | How |
| --- | --- | --- |
| Unit | Logic that does not touch the database: the cursor codec, quantity checks, the ticket status rules, the Permission Matrix | JUnit 5 |
| Controller | Paths, status codes, and the error body against the API document, with the services replaced | `MockMvc` |
| Integration | The real SQL, locks, transactions, and retries, including the races of [Concurrency by Module](../database/database-design.md#concurrency-by-module) (T-17) | Testcontainers with a MySQL 8.4 container, started by the test run and removed after it |

Integration tests do not use an in-memory database, because its locking differs from MySQL's. The container gets the schema from the Flyway migrations, run as the migration account, and then the privileges of [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql), so the tests connect with the same limited rights as the application. A concurrency test lets several threads commit, so it cannot roll back at the end: every test starts from empty tables instead. Docker Desktop must be running (see [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#26-docker-desktop)).

The test code is in `backend/src/test/java/com/opsflow/support`:

| Class | Purpose |
| --- | --- |
| `MySqlIntegrationTest` | The base class of integration tests. It starts the container once for the run, makes the two accounts with random passwords, applies the migrations and the privilege script (with the account host changed from `localhost` to `%`), and connects the application as `opsflow_app`. `jdbc` is the application account, `root()` is the root account of the container, and the tables are emptied before each test |
| `RaceRunner` | Runs one task on several threads released at the same moment and returns the value or the exception of each |
| `TestInfrastructureTest` | Checks the setup itself: 16 tables, UTC sessions, the limited rights, the clean start, and the race runner |

The tests of the shared code are in `backend/src/test/java/com/opsflow/common`, next to the package they test:

| Class | Layer | Purpose |
| --- | --- | --- |
| `CursorCodecTest` | Unit | The cursor survives the round trip with any key, and a string that is not a cursor is refused |
| `PageQueryFactoryTest` | Unit | The defaults, the limit of the page size, and the refusal of a bad `limit`, `direction`, `order`, or `cursor` |
| `PagingWalkTest` | Unit | Walks a list forward and back with the query a repository would run: every row once, no cursor at the two ends, and rows inserted or deleted between two reads neither repeat nor skip a row |
| `TransactionRetryTest` | Unit | The retry rules with a fake pause: the configured attempts, `BUSY` at the end, no retry of any other failure or of a refusal of the API, the waits, and the refusal to run inside a transaction |
| `TransactionRetryMySqlTest` | Integration | The retry against a real deadlock between two transactions and a real lock wait timeout |
| `UtcTimeTest` | Unit | The conversions between `DATETIME` values and instants, whatever the time zone of the machine |
| `UtcSourceRuleTest` | Unit | No code in `src/main/java` calls a method that reads the default time zone |
| `IdempotencyKeyTest` | Unit | The header is optional, and an empty, too long, or non-ASCII key is refused |
| `ProbeController`, `ApiConventionsTest` | Controller | A controller that exists only in the tests, and the checks through it: the base path, the error body and the code of each kind of error, no detail on an unexpected error, validation errors with their fields, the paging parameters, the idempotency header, and the format of times |
| `LimitsMatchSchemaTest` | Integration | Each constant of `Limits` equals the length of its columns in the migrated database (T-13) |

Run the tests from `backend/` with `mvnw test`. The first run downloads the MySQL image. Every application test is recorded in the Application tests section of the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md), next to the script check that it repeats.

The script in [`db/verification`](../../db/verification/) keeps checking the database on its own. The integration tests repeat those checks against the application code.

## Authentication (decided, not built)

- Passwords are hashed with BCrypt through Spring Security's `DelegatingPasswordEncoder`, so the stored value carries the `{bcrypt}` prefix and another algorithm can be added later without breaking old passwords.
- The token is a signed JWT, checked with Spring Security's OAuth2 resource server support. It carries only the user id and the issue time, and the signing key comes from an environment variable and never enters the repository. Every request still loads the user and compares the issue time with `password_changed_at` (T-20).
- The client sends the token in the `Authorization` header, as in [api-design.md](../api/api-design.md#general-rules). Where the frontend keeps it is decided when the frontend starts.

## Configuration

The application connects to MySQL as `opsflow_app`, and Flyway connects as `opsflow_migrator` (see [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md)). The four values come from environment variables and have no default, so a missing one stops the application from starting instead of falling back to a value in the repository:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` | The application connection |
| `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` | Flyway |

The operating parameters are under `opsflow.*` in `application.yml` and are bound and checked at startup by `OpsflowProperties` (T-11). They hold no secret. The reasons for the values are in Operating Parameters in the database design.

| Property | Value | Used for |
| --- | --- | --- |
| `opsflow.retry.attempts` | 3 | The tries in total of a transaction that lost a deadlock or a lock wait, the first one included |
| `opsflow.retry.delays` | 50ms, 150ms | The wait before the second and the third try. The last one is reused if there are more retries |
| `opsflow.paging.page-size` | 10 | The default and the most of the `limit` of a list |

A module adds its own parameters to this table and to `OpsflowProperties` when it is built.

The JDBC URL sets `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, so every session runs in UTC (see Time zone in the database design). A local file `application-local.yml` is ignored by git.

## Running

From `backend/`, with the four variables set in the shell:

```
mvn spring-boot:run
```

The first start makes Flyway apply `V1__initial_schema.sql` to `opsflow`. After it, run [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) once as `root` (T-02).
