# OpsFlow Backend

## Purpose

This document describes the backend as it is built now: the stack, the layout of the project, how it is configured, and how it is run. It is the current-state document of the backend area. The database it connects to is described in [database-design.md](../database/database-design.md), and the pages it serves in [page-design.md](../frontend/page-design.md).

## Change Log

This document always describes the current backend. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-15 | 1.6 | The permission check of the endpoints, the first part of the permission module: a controller method declares who may call it with `@Public`, `@AnyAccount`, or `@RequiresPermission`, `EndpointPermissions` decides from that and refuses a method that declares nothing, a user with no role gets `ACCOUNT_NO_ROLE`, the enum `Role` is the one place that says which role grants which `Permission`, so that a new role changes no endpoint, and a test fails the build for an endpoint with no declaration | [backend-v1.6.md](../iterations/backend-v1.6.md) |
| 2026-11-13 | 1.5 | Login is built: the `auth` module with BCrypt, the signed token, the check of the account on every request, the login lockout, and security that refuses by default; the seed migration `V2`; the one table of audit actions and the writer of `operation_logs`; and a signing key from the environment. Extended the same day with sign-up and password recovery: email codes with their limits, the mail component (SMTP, in the background, never inside a transaction), the first scheduled job (the cleanup), and the migration `V3`. Then the console command that creates an administrator, the first task of Phase 3 | [backend-v1.5.md](../iterations/backend-v1.5.md) |
| 2026-11-12 | 1.4 | A request id on every response, error body, and log line; the tests run in continuous integration on every push; and the rules for slow work in a transaction, for scheduled jobs, and for rules that a test must check | [backend-v1.4.md](../iterations/backend-v1.4.md) |
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
| Security | Spring Security with its OAuth2 resource server support (`spring-boot-starter-security-oauth2-resource-server`): BCrypt for passwords and a signed JWT as the login token. See Authentication |
| Mail | `spring-boot-starter-mail`: plain SMTP with STARTTLS, so the provider is configuration only. See Mail |
| Database access | Plain JDBC through `JdbcTemplate` (`spring-boot-starter-jdbc`), with the SQL written in the code. No JPA and no MyBatis |
| Transactions | `@Transactional` on service methods. See Transactions |
| Migrations | Flyway (`spring-boot-starter-flyway` and `flyway-mysql`) |
| Driver | `mysql-connector-j` |

Not added yet: Redis and RabbitMQ.

## Layout

| Path | Content |
| --- | --- |
| `backend/pom.xml` | The build and the dependencies |
| `backend/src/main/java/com/opsflow` | The code. The root package is `com.opsflow`. Inside it there is one package for each module (`order`, `inventory`, `ticket`, `user`, and so on) and a `common` package for error handling, configuration, and time helpers |
| `backend/src/main/resources/application.yml` | The configuration, with no secrets |
| `backend/src/main/resources/db/migration` | The Flyway migrations, applied in version order. Never edit one that has been applied: add a new one |
| `.github/workflows/backend-tests.yml` | The continuous integration job. See Continuous integration |

The code is organized by feature, not by layer. Inside a module the classes keep their layer in the name (`OrderController`, `OrderService`, `OrderRepository`). A module does not call the repository of another module: it calls the service. The modules so far: `auth` (login, the token, the security rules, the email codes, sign-up, the password reset, and the console command that creates an administrator). The write path of an order touches the stock and the product tables, and all of its SQL stays in the order module, so that the lock order of [Concurrency by Module](../database/database-design.md#concurrency-by-module) can be read in one place.

## Shared Code

The `common` package holds what every module uses. A module calls it and never copies it.

| Package | Class | Purpose |
| --- | --- | --- |
| `common.error` | `ErrorCode`, `ApiException`, `ApiExceptionHandler` | `ErrorCode` is the table of result codes with a status and a title each. A service refuses a request by throwing `ApiException` with a code, a detail for people, and extra fields such as `orderNo`. `ApiExceptionHandler` is the one place that turns an exception into the Problem Details body of [api-design.md](../api/api-design.md#error-response): it also answers the errors Spring MVC raises itself (unreadable body, missing parameter, unknown path, wrong method) and a failed validation, with an `errors` list. An unexpected exception is logged with its stack trace and answered with `INTERNAL_ERROR` and no detail, so no SQL, secret, or stack trace leaves the server. Every body carries the `requestId` of the request |
| `common.paging` | `Cursor`, `CursorCodec`, `PageQuery`, `PageQueryFactory`, `PageResponse` | Paging by key. `PageQueryFactory.create` checks `limit`, `cursor`, `direction`, and `order` and refuses a bad value with `VALIDATION_FAILED`. A repository reads `PageQuery.fetchSize()` rows (one more than the page) in the direction of `PageQuery.ascendingRead()`, and `PageResponse.of` cuts the page, restores the order of a backward read, and makes the two cursors. The sort key and the `id` of the edge row make the cursor |
| `common.retry` | `TransactionRetry` | Calls a `@Transactional` method and retries it on a deadlock or a lock wait timeout. See Transactions |
| `common.time` | `UtcTime` | Converts between a `DATETIME(3)` value (a UTC wall time) and an `Instant`. See Time |
| `common.web` | `WebConfig`, `IdempotencyKey`, `RequestIdFilter` | `WebConfig` serves every `@RestController` under `/api/v1` and provides the one `Clock` bean, in UTC. `IdempotencyKey.parse` reads the `Idempotency-Key` header. `RequestIdFilter` gives every request an id. See Request id and logs |
| `common.validation` | `Limits` | The length of every text field a request can set, equal to its column (T-13). `@Size(max = Limits.ITEM_NAME)` |
| `common.config` | `OpsflowProperties`, `JobsConfig` | The operating parameters, bound from `opsflow.*`. See Configuration. `JobsConfig` turns the scheduled jobs on unless `opsflow.jobs.enabled` is false. See Scheduled Jobs |
| `common.mail` | `Mailer`, `MailTransport`, `MailConfig`, `OutgoingMail` | `Mailer.send` is the only way a mail leaves the application. See Mail |
| `common.audit` | `AuditAction`, `AuditCategory`, `AuditLogWriter` | `AuditAction` is the one table of the actions written to `operation_logs`, each with its category (see [Categories](../database/database-design.md#categories)). `AuditLogWriter.write` is the only way a row is written, so no row lacks a category, and it refuses to run outside a transaction, because a log row belongs to the transaction of the change it describes. A module that logs a new action adds it to `AuditAction` |
| `common.security` | `CurrentUser`, `Permission`, `Role`, `Public`, `AnyAccount`, `RequiresPermission` | `CurrentUser` is the user who sent the request: the id, the username, and the role codes, as the database has them at this request. A controller takes it with `@AuthenticationPrincipal CurrentUser`, and a service asks it `can(Permission)`. `Permission` is the actions of the Permission Matrix, and `Role` is the four fixed roles with the permissions each one grants. The three annotations are how a controller method declares who may call it. See Permissions |

A controller declares its path without the prefix (`@RequestMapping("/orders")`) and takes the paging parameters as plain request parameters that it passes to `PageQueryFactory`.

### Request id and logs

When a user reports an error, the log lines of that one request must be found among the lines of every other request. `RequestIdFilter` makes that possible:

- It gives every request a new id of 32 hexadecimal characters and returns it in the response header `X-Request-Id`.
- It puts the id into the logging context of the thread, and `logging.pattern.correlation` in `application.yml` prints it in square brackets on every log line that the request writes. A line written outside a request shows empty brackets.
- `ApiExceptionHandler` adds it to every error body as `requestId`, so a screenshot of an error is enough to find the request.
- It runs before every other filter, the security filters included, so a request that security refuses has an id too.
- An id sent by the client is ignored, because a client could otherwise write its own text into the log.
- The id is removed from the thread when the request ends, because the thread then serves another request.

The id is not stored in the database. The audit rows in `operation_logs` record what was changed and are kept; the log lines record how a request ran and are not.

### Time

Java handles every time in UTC. Code takes "now" from the `Clock` bean and never calls `LocalDateTime.now()`, `ZoneId.systemDefault()`, `new Date()`, and the like, which read the time zone of the machine; `UtcSourceRuleTest` fails the build when one appears in `src/main/java`. A `DATETIME(3)` column is read with `rs.getObject(column, LocalDateTime.class)` and converted with `UtcTime.toInstant`, and written with `UtcTime.toLocalDateTime`. The API sends an `Instant`, which Jackson writes in ISO 8601 with a trailing `Z`. A calendar day in Sydney time is converted to a UTC range at the edge of the query (see the logs in the database design).

### Length limits

A request type declares the length of a text field with a constant of `Limits`, so that over-long input is refused with `VALIDATION_FAILED` and never truncated. `LimitsMatchSchemaTest` reads the length of each column from the database that Flyway built and compares it with the constant, so a migration that changes a length fails the build until the constant follows. A new text field adds a constant and a line in that test. The `TEXT` columns (`tickets.description` and `tickets.resolution`) have no constant yet: their limit is set when the ticket module is built.

## Transactions

Transactions are declared with `@Transactional` on service methods. The annotation works through a proxy, which has two consequences for the retry rules of the order flow:

- A retry loop never sits in the method that carries `@Transactional`. `TransactionRetry` sits outside it, calls that method, and retries on a deadlock or a lock wait (MySQL errors 1213 and 1205), so every attempt is a new transaction and the earlier one has been rolled back by the proxy. It refuses to run inside a transaction. Any other exception, a refusal of the API included, goes to the caller at once. When the last attempt fails too it throws `ApiException` with `BUSY`, with the last failure as its cause, and a caller that must record that outcome (the order module writes its `BUSY` row) catches it. The wait before a retry is the configured delay plus a random extra of up to half of it. The number of attempts and the delays come from application configuration (T-11).
- A write that must happen after a rollback, such as the `order_attempts` rows of a refused order, is made in a method of another bean with `Propagation.REQUIRES_NEW`. A call from one method of a class to another method of the same class does not pass through the proxy, so the annotation would do nothing.

### No slow work inside a transaction

A transaction holds a database connection, and often row locks, until it ends. The pool has 10 connections by default, so a transaction that waits for something slow keeps other requests waiting for a connection or for its locks. Three kinds of work therefore stay outside every transaction:

| Work | Why it is slow | Where it runs |
| --- | --- | --- |
| Hashing or checking a password with BCrypt | It is made to take tens of milliseconds | Before the transaction. Login reads the user, checks the password, and only then opens a short transaction that locks the user row, confirms that the hash it checked is still the current one, and records the success or the failure (T-20, T-28; see [Login steps](../database/database-design.md#login-security-and-account-recovery)). Sign-up and a password reset check the email code first and hash the new password before they open theirs, and the console command hashes the typed password before its transaction |
| Sending an email | It waits for a mail server, and it cannot be undone by a rollback | After the transaction has committed, so a code is never sent for a row that was rolled back, and on the thread of the mailer, so the request does not wait for the mail server either (see Mail). The database design states the same rule for any call outside the database |
| Writing or reading a file | It waits for the disk | An item image is written before the transaction that stores its path (T-42) |

The components that do this work refuse to run inside a transaction, as `TransactionRetry` already does, so that a mistake fails in the first test and not under load. This is built with each component. `PasswordHasher` refuses to hash or check a password while a transaction is active, and `Mailer` refuses to send a mail.

An email code is not slow work: its stored value is an HMAC, which takes microseconds, so `EmailCodeHasher` has no such rule.

A method with `Propagation.REQUIRES_NEW` needs a second connection while the caller keeps its own. It is therefore called only after the outer transaction has ended, as the order module does for a refused order. Called inside an open transaction, a busy moment could leave every thread holding one connection and waiting for a second, with none left in the pool.

The HTTP conventions of the controllers (paths, error responses, result codes, paging) are in [api-design.md](../api/api-design.md).

## Scheduled Jobs

The application has two jobs, each run once a minute: the cleanup of expired email codes, which is built, and the archive of resolved tickets, which comes with the ticket module (T-33). A job is a method with `@Scheduled` in its module, and `JobsConfig` turns the schedule on. Three rules apply to them and to any later job:

- **One instance.** The application is designed to run as one instance, so a job runs in one place. This is an assumption, not something the code enforces. Before a second instance is added, the jobs need a lock that only one instance can hold.
- **Safe to run twice.** A job that runs twice, or at the same time as a person doing the same thing, must change nothing wrongly. The archive goes through the status gate for each ticket, and the cleanup deletes only rows that have expired, so both already are.
- **A limit on each run.** A run handles at most a fixed number of rows and leaves the rest to the next run, so one run never holds locks or a connection for long after a backlog has built up. The number is an operating parameter, set when each job is built.

`EmailCodeCleanupJob` deletes the email codes whose `expires_at` has passed and the request counts whose window started more than two days ago, at most `opsflow.email-code.cleanup-batch` rows of each table in one run. It finds the rows with a plain read and deletes each one by its id in a statement of its own, so it holds no lock between two rows and never locks a range that a request is writing into.

With `opsflow.jobs.enabled` set to false no job runs by itself. The integration tests set it, so that no job changes rows in the middle of another test, and call a job directly when they test it.

## Rules Checked By Tests

A rule that is only written in a document is broken without anyone noticing. A rule that every later module must keep is therefore written as a test that fails the build, and the document explains the rule. The tests so far, and the ones that are due with their module:

| Rule | Test | State |
| --- | --- | --- |
| No code reads the time zone of the machine | `UtcSourceRuleTest` | Built |
| The length limit of a text field equals its column | `LimitsMatchSchemaTest` | Built |
| Every endpoint is refused unless it declares who may call it | An endpoint that declares nothing is refused for everyone (`EndpointPermissionTest.anEndpointThatDeclaresNothingIsClosedEvenForAnAdministrator`), and `EveryEndpointDeclaresWhoMayCallItTest` reads every endpoint of the running application and fails for one that does not declare exactly once | Built |
| The roles in the code are the roles in the database | `RoleTest.theRolesOfTheCodeAreTheRowsOfTheSeedMigration` | Built |
| Every permission is granted by a role, and the roles keep the principles of the Permission Matrix | `PermissionTest` | Built |
| A module does not use the repository of another module, a controller does not use `JdbcTemplate`, and `common` depends on no module | A test of the package dependencies | When the second module is built (T-54) |
| Only the allowed ticket status transitions happen | The transitions are one table in the code, and a test tries every pair of the five statuses and expects a refusal for each pair that is not in the table | With the ticket module (T-33) |
| Every action of the audit log has a category | `AuditLogTest`: every action of `AuditAction` has a category, and the categories are exactly the ones the database accepts | Built |
| A password is never hashed or checked inside a transaction | `PasswordHasherTest.hashingOrCheckingInsideATransactionIsRefused` | Built |
| A mail is never sent inside a transaction | `MailerTest.sendingInsideATransactionIsRefused` | Built |

When such a test fails, the code is wrong unless the design was changed on purpose. Only in that case is the test changed, together with the design document.

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
| `MySqlIntegrationTest` | The base class of integration tests. It starts the container once for the run, makes the two accounts with random passwords, applies the migrations and the privilege script (with the account host changed from `localhost` to `%`), and connects the application as `opsflow_app`. `jdbc` is the application account, `root()` is the root account of the container, `clock` is the clock of the application, and `sentMail()` returns the mails the application has sent in the test, after waiting for the mailer. Before each test the tables are emptied, the rows of the seed migration `V2` are put back by running that file again, and the mailbox is emptied, so every test starts with the four roles and the SYSTEM identity and nothing else. It also gives the application a token signing key made for the run, and turns the scheduled jobs off |
| `MutableClock` | The clock of the application in the integration tests. It shows the real time until a test sets it, and then moves only when the test moves it, so a test lets a minute or a night pass without waiting |
| `Mailbox` | The mail server of the integration tests. It keeps every mail and sends none, so the tests need no mail account and no network. The application still sends through its real `Mailer` |
| `RaceRunner` | Runs one task on several threads released at the same moment and returns the value or the exception of each |
| `TestInfrastructureTest` | Checks the setup itself: 17 tables, UTC sessions, the limited rights, the clean start with the seed rows, and the race runner |
| `SeedMigrationTest` | Flyway applied `V2` after `V1`, and the four roles and the SYSTEM identity are there (T-05) |

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
| `ProbeController`, `ApiConventionsTest` | Controller | A controller that exists only in the tests, and the checks through it: the base path, the error body and the code of each kind of error, no detail on an unexpected error, validation errors with their fields, the request id (in the header, in every error body, on the log line, new for each request, never taken from the client, and gone from the thread afterwards), the paging parameters, the idempotency header, and the format of times |
| `LimitsMatchSchemaTest` | Integration | Each constant of `Limits` equals the length of its columns in the migrated database (T-13) |
| `MailerTest` | Unit | The mails leave in order, the caller does not wait for the mail server, a failing mail server reaches neither the caller nor the next mail, sending inside a transaction is refused, and neither the text of a mail nor the mail password is part of what could be printed |
| `AuditLogTest` | Integration | Every audit action has a category, the categories are the ones the database accepts, a row is written with the category of its action, and writing outside a transaction is refused |
| `RoleTest` | Integration | The constants of `Role` are the `code` values of the seeded `roles` rows, a role is found by its code, and `CurrentUser` answers which roles a user holds |
| `PermissionTest` | Unit | Every permission is granted by a role, the roles keep the principles of the Permission Matrix (a manager can do what an operator can, an administrator what a manager can, a viewer only reads), the permissions of the roles of a user add up, and a user with no role or with an unknown role code can do nothing |

The tests of login, sign-up, and password recovery are in `backend/src/test/java/com/opsflow/auth`:

| Class | Layer | Purpose |
| --- | --- | --- |
| `LoginLockPolicyTest` | Unit | The lock steps at 5, 10, and 15 failures, the lock until midnight from 20, the count read as zero after midnight in Sydney, and the days of 23 and 25 hours |
| `TokenServiceTest` | Unit | The token carries only the user id and its issue time, lasts 12 hours, and is refused when signed with another key or changed; a signing key that is too short or not Base64 stops the start and is not quoted in the message |
| `PasswordHasherTest` | Unit | A password matches its own hash only, a stored value that is not a hash matches nothing, and hashing or checking inside a transaction is refused |
| `EmailCodeHasherTest` | Unit | A code has 6 digits; its stored value is not the code, matches only that code, purpose, and address, and depends on the key; the marker of a code that was not mailed matches nothing |
| `AccountRulesTest` | Unit | The password rule and the username rule, value by value |
| `AuthIntegrationSupport` | | The base of the integration classes below: HTTP calls through the whole application with `MockMvc`, the security filters included, the code read from the mail that the application sent, and accounts made directly in the database where a test is not about sign-up |
| `LoginTest` | Integration | The login through HTTP: the token, the general error for a wrong password, an unknown user, a soft-deleted account, and the SYSTEM identity, the lockout and its log row, the reset at midnight, and a disabled account |
| `TokenCheckTest` | Integration | The check on every request: no token, a foreign token, the expiry, an account disabled or soft-deleted after login, a token older than the password, the roles read at each request, and the refusal of a request that no rule allows |
| `LoginRaceTest` | Integration | The races of login: a password reset, a disabling, a soft delete, or a lock that commits between the password check and the transaction that records the result, and five wrong passwords at the same moment. The test wraps the real `PasswordHasher` and commits the other change right after the check, so the order is the same on every run. The reset in these tests is the real transaction of the application |
| `EmailCodeTest` | Integration | Requesting a code: the mail and the stored keyed hash, `EMAIL_TAKEN` for a sign-up code for an address that has an account, the same answer and no mail for a recovery code that nobody can use, the 60-second rule, the limit of 10 per client address in an hour and of 200 for the whole system in a day (the Sydney day), a refused request that is not counted, and requests at the same moment that cannot pass a limit or get two codes |
| `SignUpTest` | Integration | Sign-up: the active account with no role, its log row, and the token that logs the new user in and ends with a reset in the same millisecond, a wrong, dead, expired, older, or foreign code, the rules checked before the code, a taken username that leaves the code usable, and the races of one code sent four times and of one username wanted twice |
| `PasswordResetTest` | Integration | The reset: the new password, the old tokens ended, the lock cleared, the log row, a new password equal to the current one, the same answer for an address with no account, a disabled and a soft-deleted account, a token issued in the same millisecond as a reset, and a login and a reset on real threads at the same moment |
| `AccountRaceTest` | Integration | A newer code is requested while the password of a sign-up or a reset is being hashed: the older code no longer works and nothing is written. The test wraps the real `PasswordHasher`, so the order is the same on every run |
| `CreateAdminCommandTest` | Integration | The console command, in an application started without the web server and with a terminal played by the test: the administrator with its role and its two log rows, who can log in; a second administrator; differing passwords, a value that breaks a rule of sign-up, a taken username or address, a missing `ADMIN` role, and input that ends early, each of which creates nothing; the password is never printed; and two commands at the same moment |
| `EmailCodeCleanupJobTest` | Integration | The cleanup job, called directly: an expired code is deleted and a valid one is kept, the counts of windows long over are deleted, a run deletes at most its batch, a second run changes nothing, and no job is scheduled in the tests |
| `PermissionProbeController`, `EndpointPermissionTest` | Integration | A controller that exists only in the tests, with one endpoint for each kind of declaration and one that declares nothing, and the checks through it: no token, a user with no role (`ACCOUNT_NO_ROLE`), roles that do not grant the permission (`FORBIDDEN`), each role that grants it, a user with two roles, an endpoint that declares nothing, a path with another method, a role granted or taken away between two requests, and a disabled account |
| `EveryEndpointDeclaresWhoMayCallItTest` | Integration | Every endpoint of the running application declares exactly once who may call it. See Rules Checked By Tests |

Run the tests from `backend/` with `mvnw test`. The first run downloads the MySQL image. Every application test is recorded in the Application tests section of the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md), next to the script check that it repeats.

The script in [`db/verification`](../../db/verification/) keeps checking the database on its own. The integration tests repeat those checks against the application code.

### Continuous integration

GitHub Actions runs `mvnw test` on a clean Linux machine on every push to any branch. The job is [`.github/workflows/backend-tests.yml`](../../.github/workflows/backend-tests.yml), and its result is shown next to the commit on GitHub. The frontend has a job of its own, [`frontend-tests.yml`](../../.github/workflows/frontend-tests.yml), described in the [page design](../frontend/page-design.md#running-the-frontend-locally).

- It installs Java 17 and nothing else. The machine has Docker, so the integration tests start their MySQL container as they do locally.
- It needs no secret and no environment variable: the tests make their own accounts with random passwords and their own token signing key, and no test reads `OPSFLOW_DB_*`, `OPSFLOW_MIGRATION_*`, `OPSFLOW_TOKEN_SIGNING_KEY`, or `OPSFLOW_MAIL_*`, and no test sends a mail. Keep it so, and check a change of the test set-up by running the tests with those variables unset.
- A new test class is run without any change to the job, as long as its name ends in `Test` or `Tests`. Maven skips a class with another name without a warning.
- It does not run [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1), which needs the local MySQL service and the root password. That script is still run by hand when the schema, the privileges, or the flows change.

## Authentication

Login, the check of every request, the email codes, sign-up, and the password reset are built in the `auth` module. The rules are in [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery), and the endpoints in [api-design.md](../api/api-design.md#authentication).

| Class | Purpose |
| --- | --- |
| `AuthController` | `POST /auth/login`, `GET /auth/me`, `POST /auth/email-codes`, `POST /auth/sign-up`, and `POST /auth/password-reset` |
| `LoginService` | The login steps. It has no transaction: it reads the user, checks the password, and then calls `LoginRecorder` through `TransactionRetry` |
| `LoginRecorder` | The short transaction of step 3. It locks the user row, confirms that the hash that was checked is still the current one, and records the result. It returns the outcome and never throws a refusal, because an exception would roll back the count of a failed attempt |
| `LoginLockPolicy` | The lockout rules as pure calculation: the failures in a row that still count, and until when a failure locks the account |
| `PasswordHasher` | Hashes and checks passwords, and refuses to run inside a transaction |
| `TokenService` | Issues the token and checks its signature and expiry |
| `TokenAuthenticator` | The check of the account on every request: loads the user and the roles and refuses an account that may not use the token |
| `SecurityConfig` | The security filters: no session, the token, and `EndpointPermissions` for every request. It exists only in the web application |
| `EndpointPermissions` | Decides whether a request may reach its controller method, from what the method declares. Refuses by default. See Permissions |
| `SecurityErrorHandler` | Turns a refusal of the security filters into the error body of the API. It exists only in the web application |
| `AuthRepository` | The SQL of the module on `users` and `user_roles` |
| `EmailCodeService` | Requests a code and checks the one a person typed. It has no transaction: it decides whether a mail is sent, stores the code through `EmailCodeStore`, and sends the mail after the commit |
| `EmailCodeStore` | The transaction of a request: the two counts, the 60-second rule, the older codes marked used, and the new row. A refusal is thrown, so a refused request rolls back and is not counted. Also the short transaction that counts a wrong entry |
| `EmailCodeHasher` | Makes a code, and the keyed hash that is stored for it |
| `EmailCodeRepository` | The SQL on `email_verification_codes` and `email_code_request_counts` |
| `EmailCodeCleanupJob` | The scheduled cleanup. See Scheduled Jobs |
| `AccountService` | Sign-up and the password reset. It has no transaction: it checks the values, then the code, then does the BCrypt work, and then calls `AccountWriter` through `TransactionRetry` |
| `AccountWriter` | The two transactions that a code allows: creating the account, and setting the new password with the user row locked |
| `AccountRules` | The username rule and the password rule |
| `CreateAdminCommand` | The console command `create-admin`: it asks for the values at the terminal, checks them with the rules of sign-up, hashes the password, and calls `AdminCreator`. See Console command |
| `AdminCreator` | The transaction of that command: it locks the `ADMIN` row in `roles`, creates the user, grants `ADMIN`, and writes the two log rows |
| `Prompt` | The terminal of a console command. The real one hides a typed secret; the tests use one of their own |
| `PasswordConfig` | The password encoder. It is apart from `SecurityConfig`, because the console command hashes a password without the web application |

- **Passwords** are hashed with BCrypt through Spring Security's `DelegatingPasswordEncoder`, so the stored value carries the `{bcrypt}` prefix and another algorithm can be added later without breaking old passwords.
- **The token** is a JWT signed with HMAC-SHA256, checked with Spring Security's OAuth2 resource server support. It carries the user id (`sub`), the issue time, and the expiry, and nothing else about the user. The standard issue time `iat` is in whole seconds, and `users.password_changed_at` is in milliseconds, so the token also carries the issue time in milliseconds (`iat_ms`): with seconds alone, a login in the same second as a password reset could not be told from one before it.
- **Every request** loads the user and the roles from the database with plain reads (`TokenAuthenticator`). A token is accepted only if it was issued after `password_changed_at`. `LoginRecorder` never issues a token at or before that time: a login in the same millisecond as the reset before it gets the next millisecond.
- **The expiry** is read on the `Clock` bean, the clock that issued the token, and not on the system clock of the token library.
- **Unknown users cost the same time.** When there is no account to check, the login checks the password against a decoy hash made at startup, so the answer for an unknown username takes as long as for a real one.
- **Refuse by default.** Every request is decided by what its controller method declares (see Permissions). `POST /auth/login`, `POST /auth/email-codes`, `POST /auth/sign-up`, and `POST /auth/password-reset` are `@Public`, and `GET /auth/me` is `@AnyAccount`. A request whose method declares nothing, or that has no method, is refused, with `UNAUTHENTICATED` without a token and `FORBIDDEN` with one, also when the path does not exist.
- **Errors of the security filters** happen before any controller. `SecurityErrorHandler` hands them to the exception handler of the controllers, so they have the same Problem Details body with `code` and `requestId`: `UNAUTHENTICATED`, `ACCOUNT_DISABLED`, `ACCOUNT_NO_ROLE`, or `FORBIDDEN`. The body does not say why a token was refused.
- **No session and no cookie.** The server keeps no session, and the client sends the token in the `Authorization` header, as in [api-design.md](../api/api-design.md#general-rules). Protection against cross-site request forgery is therefore off: no cookie authenticates a request. The frontend keeps the token in `localStorage` (see [Frontend Stack And Design Files](../frontend/page-design.md#frontend-stack-and-design-files)).
- **An email code** is stored as an HMAC-SHA256 made with the token signing key, so there is no second secret. The signed text starts with a line of its own, which no login token can start with, so a stored value can never serve as the signature of a token.
- **A request for a sign-up code** for an address that has an account is refused with `EMAIL_TAKEN` before anything is stored. **A request for a recovery code** gets the same answer whether or not a mail is sent, and a row is stored either way (with the marker `!NOT-SENT` when no mail goes out), so the limits and a later wrong entry behave the same for a registered and an unregistered address.
- **A sign-up logs the new user in.** The transaction that creates the account takes the issue time of the first token and stores it as `last_login_at`, and `AccountService` issues the token after the commit. A later reset is never earlier than `last_login_at`, so it ends that token like any other.
- **The cheap checks come first.** Sign-up and the reset check the values, then the code, and only with a right code hash a password. A request with a wrong code costs no BCrypt.
- **The limits on code requests** are counted in MySQL and not in Redis: a few dozen codes a day do not justify another service, and the count row gives the lock that makes the limits safe against a race.
- **The roles** of the user become the authorities `ROLE_<code>` of the request, and `CurrentUser.roles()` for the code. `EndpointPermissions` reads them for every endpoint that needs a permission.

### Permissions

The Permission Matrix of [database-design.md](../database/database-design.md#permission-matrix) is enforced in two places. This is the first: whether a request may reach its controller method at all. The second is the service, which checks the rules that depend on the data, such as an operator who may resolve only their own ticket, inside the transaction of the change.

Every controller method carries exactly one of three annotations from `common.security`:

| Annotation | Who may call the endpoint |
| --- | --- |
| `@Public` | Everyone, logged in or not: login, sign-up, and Forgot password |
| `@AnyAccount` | Every logged-in user, also one with no role: `GET /auth/me`, which the pending page needs |
| `@RequiresPermission(Permission.AUDIT_VIEW)` | A logged-in user with a role that grants the permission. It is one row of the Permission Matrix, written on the method. The endpoint names no role |

`EndpointPermissions` finds the controller method of a request and decides:

- **The roles of a user add up.** A user passes when any one of their roles grants the permission. The roles are not a ladder: a viewer sees everyone's tickets and an operator does not, so each role lists its own permissions.
- **A user with no role** who calls an endpoint that needs a permission gets `ACCOUNT_NO_ROLE`, and the frontend opens the pending page (T-22). A user whose roles do not grant it gets `FORBIDDEN`.
- **A method that declares nothing is refused for everyone**, an administrator included, and so is a request that has no controller method. An endpoint that somebody forgot to declare is closed, and `EveryEndpointDeclaresWhoMayCallItTest` fails the build for it.
- **A role counts only for a usable account** (T-19). The roles are read from the database for every request, and a disabled or soft-deleted account is refused before its roles are looked at, so a role that is granted or taken away counts on the next request.

**Permissions and roles.** `Permission` is an enum with one constant for each action of the Permission Matrix that some roles may do and others may not, such as `ORDER_PLACE` or `TICKET_ASSIGN`. `Role` is an enum of the four fixed roles; the name of each constant is its `roles.code`, and each constant lists the permissions it grants. That list is the columns of the matrix, and it is the only place in the code that ties a role to a permission:

- An endpoint names a permission, never a role.
- A service asks `CurrentUser.can(Permission)` before an action, and not which role the user holds. `CurrentUser.holds(Role)` is for a rule about the role itself, such as who may change an administrator.
- `GET /auth/me` returns `CurrentUser.permissions()`, the permissions of all the roles of the user, and the frontend shows a control by its permission ([api-v1.4.md](../iterations/api-v1.4.md)). So the frontend names no role either.
- A wider reach that is itself a row of the matrix is a permission of its own: `TICKET_VIEW` opens the ticket screens, and `TICKET_VIEW_ALL` shows everyone's tickets on them. A reach that depends on the data, such as an operator who may resolve only a ticket assigned to them, stays a rule of the service; when a module is built, such a rule gets a permission where it would otherwise ask for a role.

So each kind of change has one place:

| Change | What it needs |
| --- | --- |
| A new endpoint | One line: the annotation with the permission of its row in the matrix |
| A new action | A constant in `Permission`, added to the roles that grant it in `Role` |
| A role may do more or less | The list of that role in `Role` |
| A new role | A migration that inserts the `roles` row, and a constant in `Role` with its permissions. No endpoint and no page changes |

`RoleTest` fails when the enum and the table differ, and `PermissionTest` fails for a permission that no role grants, and for a role list that breaks a principle of the matrix.

## Console command

`create-admin` makes an administrator on the server, so that no page and no endpoint exists for it (see [First administrator](../database/database-design.md#first-administrator)). It is run from `backend/` after `mvnw package`, in a terminal, with the five required variables set:

```
java -jar target/opsflow-backend-0.0.1-SNAPSHOT.jar create-admin
```

- **No web server.** `OpsflowBackendApplication.main` sees the argument and starts the application with no web server and with the scheduled jobs off, runs the command, and exits with 0 when the administrator was created and 1 otherwise. Flyway still runs at that start, so the roles exist. `SecurityConfig` and `SecurityErrorHandler` are marked as parts of the web application only, because they need the web layer; `CreateAdminCommandTest` starts the application the same way, so a class that cannot start without the web server fails there.
- **The password is typed and kept nowhere.** The command asks for the username, the email, and the password twice. The password is read with the typed characters hidden, is never an argument, an environment variable, or a file, and is never printed or logged. Without a real terminal (input from a pipe or a file, or a start from an IDE or from Maven) the command refuses to run and says so.
- **The rules of sign-up** apply to the username, the email, and the password. A value that breaks one, two passwords that differ, a username or an address that is taken, and a missing `ADMIN` role each end the command with a message that names the reason, and nothing is created.
- **A start that fails** is reported in one sentence and not as a stack trace. The usual reason is an environment variable that the terminal does not have, because the terminal was opened before the variable was set; the sentence names the variable and never a value.
- **It can run again** at any time, for another administrator.

## Mail

The application sends one kind of mail so far: the email code of sign-up and password recovery. All mail goes through `Mailer` in `common.mail`.

- **Plain SMTP with STARTTLS.** Every mail provider offers it, so changing the provider changes the configuration and no code. The owner chose Gmail SMTP with an app password, from a Gmail account made for OpsFlow (Q-12). The sender shown to the recipient is that account. A provider for transactional mail (Amazon SES, Resend, and the like) needs a domain of one's own and replaces it by configuration when OpsFlow gets real users.
- **Never inside a transaction.** `Mailer.send` refuses to run while a transaction is active. A caller commits first and sends afterwards.
- **In the background.** `send` hands the mail to one worker thread and returns at once. The answer to a request therefore takes the same time whether or not a mail is sent, which matters because the answer must not show whether an address is registered, and a slow mail server does not hold a request. The mails leave in the order they were handed over. At most 200 wait; a mail beyond that is dropped with a log line.
- **A failure is logged and not reported**: the request has been answered already. The log line holds the subject and the type of the error, never the address, the text, or the code. The person requests a new code after 60 seconds.
- **Without a mail server** (`OPSFLOW_MAIL_HOST` or `OPSFLOW_MAIL_PASSWORD` not set) the application starts, sends nothing, and logs a warning for each mail. The code is never written to the log, so a machine without a mail account cannot complete a sign-up. The tests replace the mail server with `Mailbox`.
- **The connection** uses the port 587, requires STARTTLS, so neither the password nor a mail travels unencrypted, and gives up after 10 seconds.
- **The password** of the mail account comes from the environment. `OpsflowProperties.Mail` leaves it out of its text, so it cannot reach a log through the configuration object.

## Configuration

The application connects to MySQL as `opsflow_app`, and Flyway connects as `opsflow_migrator` (see [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md)). These values come from environment variables and have no default, so a missing one stops the application from starting instead of falling back to a value in the repository:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` | The application connection |
| `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` | Flyway |
| `OPSFLOW_TOKEN_SIGNING_KEY` | The key that signs the login tokens: Base64 of at least 32 random bytes. The application refuses to start with a shorter key or one that is not Base64, and never prints it. Whoever has the key can make a token for any user, and changing it logs every user out. It is also the key of the stored email codes, so changing it makes the codes that are waiting useless |

The mail variables are optional, so the application starts without them and sends no mail:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_MAIL_HOST` | The SMTP server, `smtp.gmail.com` for the chosen account. Without it, or without the password, no mail is sent |
| `OPSFLOW_MAIL_PORT` | The SMTP port. The default is 587 |
| `OPSFLOW_MAIL_USERNAME` | The login of the mail account, which is its address |
| `OPSFLOW_MAIL_PASSWORD` | The password for SMTP. For Gmail it is an app password, not the password of the Google account. A secret |
| `OPSFLOW_MAIL_FROM` | The sender address, when it differs from the username. Gmail always sends as the account |

The operating parameters are under `opsflow.*` in `application.yml` and are bound and checked at startup by `OpsflowProperties` (T-11). They hold no secret. The reasons for the values are in Operating Parameters in the database design.

| Property | Value | Used for |
| --- | --- | --- |
| `opsflow.retry.attempts` | 3 | The tries in total of a transaction that lost a deadlock or a lock wait, the first one included |
| `opsflow.retry.delays` | 50ms, 150ms | The wait before the second and the third try. The last one is reused if there are more retries |
| `opsflow.paging.page-size` | 10 | The default and the most of the `limit` of a list |
| `opsflow.login.lock-steps` | 5: 1m, 10: 5m, 15: 15m | The failed logins in a row that lock an account, and for how long |
| `opsflow.login.lock-until-midnight-at` | 20 | The failed logins in a row from which the account is locked until midnight |
| `opsflow.login.reset-zone` | `Australia/Sydney` | The time zone of the midnight at which the count of failures starts again |
| `opsflow.token.ttl` | 12h | How long a login token is valid |
| `opsflow.email-code.ttl` | 10m | How long an email code is valid |
| `opsflow.email-code.max-wrong-entries` | 5 | The wrong entries after which a code is dead |
| `opsflow.email-code.resend-after` | 60s | The wait before a new code for the same email and purpose |
| `opsflow.email-code.requests-per-ip-per-hour` | 10 | The accepted code requests of one client address in one hour |
| `opsflow.email-code.requests-per-day` | 200 | The accepted code requests of the whole system in one day |
| `opsflow.email-code.day-zone` | `Australia/Sydney` | The time zone of the midnight at which that day starts |
| `opsflow.email-code.cleanup-interval` | 1m | How often the cleanup job runs |
| `opsflow.email-code.cleanup-batch` | 1000 | The most rows one run of the cleanup deletes from each table |
| `opsflow.jobs.enabled` | true | Whether the scheduled jobs run. The tests set it to false |

A module adds its own parameters to this table and to `OpsflowProperties` when it is built.

`logging.pattern.correlation` prints the request id on every log line (see Request id and logs).

The JDBC URL sets `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, so every session runs in UTC (see Time zone in the database design). A local file `application-local.yml` is ignored by git.

## Running

From `backend/`, with the five required variables set in the shell, and the mail variables if mail is to be sent:

```
mvn spring-boot:run
```

The first start makes Flyway apply `V1__initial_schema.sql` to `opsflow`. After it, run [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) once as `root` (T-02). The next start after a new migration applies it: `V2__seed_roles_and_system_identity.sql` adds the four roles and the SYSTEM identity, and needs no new grant because it adds no table. `V3__email_code_request_counts.sql` adds a table, so after it the grant for `email_code_request_counts` in the privilege script is run once as `root`. Until then a request for an email code fails with a permission error, which is the safe way to fail. On the local machine all three migrations are applied and the grant is run (2026-11-13).
