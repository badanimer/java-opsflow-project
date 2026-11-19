# OpsFlow Backend

## Purpose

This document describes the backend as it is built now: the stack, the layout of the project, how it is configured, and how it is run. It is the current-state document of the backend area. The database it connects to is described in [database-design.md](../database/database-design.md), and the pages it serves in [page-design.md](../frontend/page-design.md).

## Change Log

This document always describes the current backend. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-19 | 1.15 | The five long-running services of the deployment restart with Docker, so starting Docker Desktop brings OpsFlow back after the machine restarts. The documentation prepared for a public repository: a new README, the Project Log in `docs/iterations/README.md`, the environment document moved to `docs/environment-setup.md` and made general, and an MIT license | [backend-v1.15.md](../iterations/backend-v1.15.md) |
| 2026-11-18 | 1.14 | Who sees which order records (Q-34): the permission `ORDER_VIEW_ALL` (administrator, manager, viewer) and the person filter of `/order-records`, with the caller's own records for everyone else. The last races of Concurrency by Module against the real code (T-17, T-34): two orders of the same items in opposite order, a reassignment against a resolution and a cancellation, the archive job against a manager, a disabling against the person's own release and resolution, and a ticket opened as its product is delisted. The way of working between development and the deployment, and the backup and restore of the deployment, checked by running them. 7 tests | [backend-v1.14.md](../iterations/backend-v1.14.md) |
| 2026-11-18 | 1.13 | The local deployment (Q-32): Docker Compose with MySQL, the migrations, the rights of the application account, Redis, RabbitMQ, the backend, and nginx, with secrets made at random; the image of the backend; the client address from nginx only; Actuator on a management port of its own with the counters of retries and `BUSY` answers; and 4 tests | [backend-v1.13.md](../iterations/backend-v1.13.md) |
| 2026-11-18 | 1.12 | Redis and RabbitMQ are brought in (Q-07). `LoginLimits` keeps the login counts of each client address in Redis, so the steps of the lockout lock one address out of one account, and an address is refused after 50 failures in 15 minutes; the user row keeps the ceiling of 100 failures a day. `Mailer` puts each mail in a durable RabbitMQ queue, a listener sends it with three tries and keeps a refused mail as a dead letter, and without the broker the mail is sent at once. The variables `OPSFLOW_REDIS_*` and `OPSFLOW_RABBITMQ_*`, the parameters of the new counts and of the queue, Redis and RabbitMQ containers in the tests, and 11 tests | [backend-v1.12.md](../iterations/backend-v1.12.md) |
| 2026-11-16 | 1.11 | Phase 6 in the backend: the `ticket` module, which opens a ticket and makes its eight changes through the status gate with their locks, the limits of 99, the history, and the log rows; who sees which ticket, in one place; the list of the five screens with the search; the archive job; the cascades that return the tickets of a disabled or deleted user to the pool and cancel the tickets of a deleted item or a delisted product; the user picker in `user`; the permissions `TICKET_ASSIGN_PROTECTED` and `TICKET_HANDLE_ANY`; the parameters `opsflow.ticket.*`; the return of the tickets of a user who loses every role that may take tickets, and its warning `GET /users/{id}/role-change`; the two endpoints of the audit log page in `log`; the times of the audit rows and the stock movements from the clock of the application; the requests of the click-through of 2026-11-17 (the tickets of one item, a fixed remark for every action, the status buttons and the deleted users, the rows about one user); and 42 tests | [backend-v1.11.md](../iterations/backend-v1.11.md) |
| 2026-11-16 | 1.10 | Phase 5 in the backend: the `order` module, which places an order in one transaction with its idempotency key, its number, and the record of every attempt, a refused one included; and the three manual stock changes in `warehouse`. The `log` module: the two logs, the Deleted items module, and the product picker, with the shared time filter `DayRanges`. The ranges of a stock change and an order line in `Quantities`, the parameters `opsflow.order.*` and `opsflow.time-filter.zone`, and 48 tests | [backend-v1.10.md](../iterations/backend-v1.10.md) |
| 2026-11-15 | 1.9 | The `warehouse` module of Phase 4: warehouse items, their images, the product page, the catalog, listing, and delisting, with the five write paths and their locks, the image store and `OPSFLOW_IMAGE_DIR`, the shared quantity rule `Quantities`, and 37 tests | [backend-v1.9.md](../iterations/backend-v1.9.md) |
| 2026-11-15 | 1.8 | One signed-in device per account: a login writes a new session id that its token carries, `TokenAuthenticator` refuses an earlier login with `SESSION_REPLACED`, and `SessionEvents` keeps an event stream (`GET /auth/session-events`) open for each signed-in page and tells it at once when a newer login commits | [backend-v1.8.md](../iterations/backend-v1.8.md) |
| 2026-11-15 | 1.7 | The rules between the packages are checked by a test: `PackageDependencyRulesTest` fails the build when `common` depends on a module, when a controller reads the database itself, or when a module uses the repository of another module. The `user` module, the second module, with the user page (`GET /users`, the first list, paged by key) and the four changes one user makes to another: replacing the roles, disabling, enabling, and soft-deleting, each in one transaction that locks the actor and the target and reads the authority of the actor again, with their race tests | [backend-v1.7.md](../iterations/backend-v1.7.md) |
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
| Redis | `spring-boot-starter-data-redis` (Lettuce): the login counts of each client address. See Authentication. Added on 2026-11-18 (Q-07) |
| RabbitMQ | `spring-boot-starter-amqp`: the mail queue. See Mail. Added on 2026-11-18 (Q-07) |
| Health and counters | `spring-boot-starter-actuator` on a management port of its own: the health check and two counters of the retry. See Deployment. Added on 2026-11-18 (Q-32) |

Neither Redis nor RabbitMQ holds anything that cannot be lost: when one cannot be reached, the login goes on with the ceiling in the user row only, and a mail is sent at once without the queue.

## Layout

| Path | Content |
| --- | --- |
| `backend/pom.xml` | The build and the dependencies |
| `backend/src/main/java/com/opsflow` | The code. The root package is `com.opsflow`. Inside it there is one package for each module (`order`, `inventory`, `ticket`, `user`, and so on) and a `common` package for error handling, configuration, and time helpers |
| `backend/src/main/resources/application.yml` | The configuration, with no secrets |
| `backend/src/main/resources/db/migration` | The Flyway migrations, applied in version order. Never edit one that has been applied: add a new one |
| `.github/workflows/backend-tests.yml` | The continuous integration job. See Continuous integration |
| `backend/Dockerfile`, `deploy/` | The image of the backend and the local deployment with Docker Compose. See Deployment |

The code is organized by feature, not by layer. Inside a module the classes keep their layer in the name (`OrderController`, `OrderService`, `OrderRepository`). A module does not call the repository of another module: it calls the service. `PackageDependencyRulesTest` checks this, and that a controller does not read the database itself and that `common` depends on no module. The second module is `user` (the changes one user makes to another; see Users). The modules so far: `warehouse` (items, products, and the manual stock changes), `order` (placing an order), `log` (the two logs, which only read), `auth` (login, the token, the security rules, the email codes, sign-up, the password reset, and the console command that creates an administrator). The write path of an order touches the stock and the product tables, and all of its SQL stays in the order module, so that the lock order of [Concurrency by Module](../database/database-design.md#concurrency-by-module) can be read in one place.

## Shared Code

The `common` package holds what every module uses. A module calls it and never copies it.

| Package | Class | Purpose |
| --- | --- | --- |
| `common.error` | `ErrorCode`, `ApiException`, `ApiExceptionHandler` | `ErrorCode` is the table of result codes with a status and a title each. A service refuses a request by throwing `ApiException` with a code, a detail for people, and extra fields such as `orderNo`. `ApiExceptionHandler` is the one place that turns an exception into the Problem Details body of [api-design.md](../api/api-design.md#error-response): it also answers the errors Spring MVC raises itself (unreadable body, missing parameter, unknown path, wrong method) and a failed validation, with an `errors` list. An unexpected exception is logged with its stack trace and answered with `INTERNAL_ERROR` and no detail, so no SQL, secret, or stack trace leaves the server. Every body carries the `requestId` of the request |
| `common.paging` | `Cursor`, `CursorCodec`, `PageQuery`, `PageQueryFactory`, `PageResponse` | Paging by key. `PageQueryFactory.create` checks `limit`, `cursor`, `direction`, and `order` and refuses a bad value with `VALIDATION_FAILED`; `createInOrder` does the same for a list whose fixed order is not the default `desc`, such as the user page. A repository reads `PageQuery.fetchSize()` rows (one more than the page) in the direction of `PageQuery.ascendingRead()`, and `PageResponse.of` cuts the page, restores the order of a backward read, and makes the two cursors. The sort key and the `id` of the edge row make the cursor |
| `common.retry` | `TransactionRetry` | Calls a `@Transactional` method and retries it on a deadlock or a lock wait timeout. See Transactions |
| `common.time` | `UtcTime`, `DayRanges` | `UtcTime` converts between a `DATETIME(3)` value (a UTC wall time) and an `Instant`. `DayRanges` turns the days of a time filter into a UTC range. See Time |
| `common.web` | `WebConfig`, `IdempotencyKey`, `RequestIdFilter` | `WebConfig` serves every `@RestController` under `/api/v1` and provides the one `Clock` bean, in UTC. `IdempotencyKey.parse` reads the `Idempotency-Key` header. `RequestIdFilter` gives every request an id. See Request id and logs |
| `common.validation` | `Limits` | The length of every text field a request can set, equal to its column (T-13). `@Size(max = Limits.ITEM_NAME)` |
| `common.config` | `OpsflowProperties`, `JobsConfig` | The operating parameters, bound from `opsflow.*`. See Configuration. `JobsConfig` turns the scheduled jobs on unless `opsflow.jobs.enabled` is false. See Scheduled Jobs |
| `common.mail` | `Mailer`, `MailQueue`, `RabbitMailQueue`, `MailTransport`, `MailConfig`, `OutgoingMail` | `Mailer.send` is the only way a mail leaves the application. `RabbitMailQueue` keeps the mail in RabbitMQ until it is sent. See Mail |
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

The application has two jobs, each run once a minute: the cleanup of expired email codes and the archive of resolved tickets. A job is a method with `@Scheduled` in its module, and `JobsConfig` turns the schedule on. Three rules apply to them and to any later job:

- **One instance.** The application is designed to run as one instance, so a job runs in one place. This is an assumption, not something the code enforces. Before a second instance is added, the jobs need a lock that only one instance can hold.
- **Safe to run twice.** A job that runs twice, or at the same time as a person doing the same thing, must change nothing wrongly. The archive goes through the status gate for each ticket, and the cleanup deletes only rows that have expired, so both already are.
- **A limit on each run.** A run handles at most a fixed number of rows and leaves the rest to the next run, so one run never holds locks or a connection for long after a backlog has built up. The number is an operating parameter, set when each job is built.

`EmailCodeCleanupJob` deletes the email codes whose `expires_at` has passed and the request counts whose window started more than two days ago, at most `opsflow.email-code.cleanup-batch` rows of each table in one run. It finds the rows with a plain read and deletes each one by its id in a statement of its own, so it holds no lock between two rows and never locks a range that a request is writing into.

`TicketArchiveJob` archives every `RESOLVED` ticket resolved at least `opsflow.ticket.auto-archive-after` ago, and all of them when `opsflow.ticket.auto-archive-at-resolved` are waiting, at most `opsflow.ticket.archive-job-batch` in one run. It reads the ids with a plain read and archives each ticket in its own transaction through `TicketWriter.archive`, as the SYSTEM identity, so a ticket that a person archived meanwhile is skipped. All the rows of one run share one `batchId`.

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
| A module does not use the repository of another module, a controller does not read the database itself, and `common` depends on no module | `PackageDependencyRulesTest` reads the compiled classes of `src/main` with ArchUnit. A module is the package directly under `com.opsflow`, and a repository is a class named `...Repository` or annotated `@Repository`. Each rule is also run over a made-up application in the test package `rulefixture` that breaks it once, so a rule that could not fail is noticed | Built |
| Only the allowed ticket status transitions happen | The transitions are one table in `TicketStatus`, and `TicketStatusTest` tries every pair of the five statuses and expects a refusal for each pair that is not in the table | Built |
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
| Integration | The real SQL, locks, transactions, and retries, including the races of [Concurrency by Module](../database/database-design.md#concurrency-by-module) (T-17) | Testcontainers with a MySQL 8.4 container, a Redis 8.0 container, and a RabbitMQ 4.1 container, started by the test run and removed after it |

Integration tests do not use an in-memory database, because its locking differs from MySQL's. The container gets the schema from the Flyway migrations, run as the migration account, and then the privileges of [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql), so the tests connect with the same limited rights as the application. A concurrency test lets several threads commit, so it cannot roll back at the end: every test starts from empty tables instead. Docker Desktop must be running (see [environment-setup.md](../environment-setup.md#docker-desktop)).

The test code is in `backend/src/test/java/com/opsflow/support`:

| Class | Purpose |
| --- | --- |
| `MySqlIntegrationTest` | The base class of integration tests. It starts the container once for the run, makes the two accounts with random passwords, applies the migrations and the privilege script (with the account host changed from `localhost` to `%`), and connects the application as `opsflow_app`. `jdbc` is the application account, `root()` is the root account of the container, `clock` is the clock of the application, and `sentMail()` returns the mails the application has sent in the test, after waiting for the mailer. Before each test every table is emptied with `DELETE` and its next id set back to 1, the rows of the seed migration `V2` are put back by running that file again, so they get the ids of the migration, the mailbox is emptied, and Redis is emptied. The application is connected to the Redis and the RabbitMQ containers, so every mail goes through the real queue to the `Mailbox`, and `sentMail()` waits until it has left the queue. Then every table is counted: a table with any row but the four roles and the SYSTEM identity stops the test with the name of the table, so a test never starts on the rows of another. `DELETE` replaced `TRUNCATE` on 2026-11-17: `TRUNCATE` rebuilds each table and took about 2.4 seconds before every test, most of the time of the whole run. It also gives the application a token signing key made for the run, and turns the scheduled jobs off |
| `MutableClock` | The clock of the application in the integration tests. It shows the real time until a test sets it, and then moves only when the test moves it, so a test lets a minute or a night pass without waiting |
| `Mailbox` | The mail server of the integration tests. It keeps every mail and sends none, so the tests need no mail account and no network. The application still sends through its real `Mailer` and the real queue. `refuseEveryMail()` makes it refuse every mail until the next test, as a server that is down |
| `RaceRunner` | Runs one task on several threads released at the same moment and returns the value or the exception of each |
| `TestInfrastructureTest` | Checks the setup itself: 17 tables, UTC sessions, the limited rights, the clean start with the seed rows, and the race runner |
| `SeedMigrationTest` | Flyway applied `V2` after `V1`, and the four roles and the SYSTEM identity are there (T-05) |

The tests of the shared code are in `backend/src/test/java/com/opsflow/common`, next to the package they test:

| Class | Layer | Purpose |
| --- | --- | --- |
| `CursorCodecTest` | Unit | The cursor survives the round trip with any key, and a string that is not a cursor is refused |
| `PageQueryFactoryTest` | Unit | The defaults, the limit of the page size, the refusal of a bad `limit`, `direction`, `order`, or `cursor`, and a list whose fixed order is ascending |
| `PagingWalkTest` | Unit | Walks a list forward and back with the query a repository would run: every row once, no cursor at the two ends, and rows inserted or deleted between two reads neither repeat nor skip a row |
| `TransactionRetryTest` | Unit | The retry rules with a fake pause: the configured attempts, `BUSY` at the end, no retry of any other failure or of a refusal of the API, the waits, the refusal to run inside a transaction, and the two counters |
| `ActuatorTest` | Integration | In a real server with two ports: the health check sees the database, Redis, and RabbitMQ and the two counters answer on the management port without a login, and the port of the API refuses both |
| `TransactionRetryMySqlTest` | Integration | The retry against a real deadlock between two transactions and a real lock wait timeout |
| `UtcTimeTest` | Unit | The conversions between `DATETIME` values and instants, whatever the time zone of the machine |
| `UtcSourceRuleTest` | Unit | No code in `src/main/java` calls a method that reads the default time zone |
| `PackageDependencyRulesTest` (in `com.opsflow`), with the classes of `rulefixture` | Unit | The three rules between the packages hold for the application, the rules read the classes of `src/main` and not those of the tests, and each rule finds the one class of the made-up application that breaks it. See Rules Checked By Tests |
| `IdempotencyKeyTest` | Unit | The header is optional, and an empty, too long, or non-ASCII key is refused |
| `ProbeController`, `ApiConventionsTest` | Controller | A controller that exists only in the tests, and the checks through it: the base path, the error body and the code of each kind of error, no detail on an unexpected error, validation errors with their fields, the request id (in the header, in every error body, on the log line, new for each request, never taken from the client, and gone from the thread afterwards), the paging parameters, the idempotency header, and the format of times |
| `LimitsMatchSchemaTest` | Integration | Each constant of `Limits` equals the length of its columns in the migrated database (T-13) |
| `MailerTest` | Unit | With a queue made by the test: the mails leave in order, the caller does not wait for the mail server, a mail from the queue gets three tries and is then given up, without the queue a mail is sent at once, sending inside a transaction is refused, and neither the text of a mail nor the mail password is part of what could be printed |
| `MailQueueTest` | Integration | Against the RabbitMQ container: a mail goes through the queue to the mail server and leaves nothing behind, and a mail the server refuses three times ends as a dead letter |
| `AuditLogTest` | Integration | Every audit action has a category, the categories are the ones the database accepts, a row is written with the category of its action, and writing outside a transaction is refused |
| `RoleTest` | Integration | The constants of `Role` are the `code` values of the seeded `roles` rows, a role is found by its code, and `CurrentUser` answers which roles a user holds |
| `PermissionTest` | Unit | Every permission is granted by a role, the roles keep the principles of the Permission Matrix (a manager can do what an operator can, an administrator what a manager can, a viewer only reads), the permissions of the roles of a user add up, and a user with no role or with an unknown role code can do nothing |

The tests of login, sign-up, and password recovery are in `backend/src/test/java/com/opsflow/auth`:

| Class | Layer | Purpose |
| --- | --- | --- |
| `LoginLockPolicyTest` | Unit | The lock steps of an address at 5, 10, and 15 failures, its lock until midnight from 20, the account locked at the ceiling of 100, the count read as zero after midnight in Sydney, the day and the windows of the Redis counts, and the days of 23 and 25 hours |
| `LoginLimitsTest` | Unit | When Redis cannot be reached, no address is refused and no count throws |
| `TokenServiceTest` | Unit | The token carries only the user id, its issue time, and the id of its login, lasts 12 hours, and is refused when signed with another key or changed; a signing key that is too short or not Base64 stops the start and is not quoted in the message |
| `PasswordHasherTest` | Unit | A password matches its own hash only, a stored value that is not a hash matches nothing, and hashing or checking inside a transaction is refused |
| `EmailCodeHasherTest` | Unit | A code has 6 digits; its stored value is not the code, matches only that code, purpose, and address, and depends on the key; the marker of a code that was not mailed matches nothing |
| `AccountRulesTest` | Unit | The password rule and the username rule, value by value |
| `AuthIntegrationSupport` | | The base of the integration classes below: HTTP calls through the whole application with `MockMvc`, the security filters included, the code read from the mail that the application sent, and accounts made directly in the database where a test is not about sign-up |
| `LoginTest` | Integration | The login through HTTP: the token, the general error for a wrong password, an unknown user, a soft-deleted account, and the SYSTEM identity, the lock of an address and its log row, a stranger who locks only their own address, the steps up to midnight, the reset at midnight, a success that clears its address only, the ceiling of the day that locks the account, an address refused after 50 failures in its window, and a disabled account |
| `TokenCheckTest` | Integration | The check on every request: no token, a foreign token, the expiry, an account disabled or soft-deleted after login, a token older than the password, the roles read at each request, and the refusal of a request that no rule allows |
| `SessionTest` | Integration | One signed-in device: a second login, or a login after a sign-up, ends the earlier token with `SESSION_REPLACED`; a login of another account ends nothing; a disabled account is told that it is disabled; the open stream of the earlier login gets `session-replaced` at once and ends, the stream of the newest login stays open, a stream cannot be opened with an ended token, a stream opened just after a login it missed is told at once, and of two logins at the same moment exactly one stays valid |
| `LoginRaceTest` | Integration | The races of login: a password reset, a disabling, a soft delete, or a lock that commits between the password check and the transaction that records the result, five wrong passwords at the same moment from one address, and two failures at the ceiling at the same moment from two addresses. The test wraps the real `PasswordHasher` and commits the other change right after the check, so the order is the same on every run. The reset in these tests is the real transaction of the application |
| `EmailCodeTest` | Integration | Requesting a code: the mail and the stored keyed hash, `EMAIL_TAKEN` for a sign-up code for an address that has an account, the same answer and no mail for a recovery code that nobody can use, the 60-second rule, the limit of 10 per client address in an hour and of 200 for the whole system in a day (the Sydney day), a refused request that is not counted, and requests at the same moment that cannot pass a limit or get two codes |
| `SignUpTest` | Integration | Sign-up: the active account with no role, its log row, and the token that logs the new user in and ends with a reset in the same millisecond, a wrong, dead, expired, older, or foreign code, the rules checked before the code, a taken username that leaves the code usable, and the races of one code sent four times and of one username wanted twice |
| `PasswordResetTest` | Integration | The reset: the new password, the old tokens ended, the locks of every address cleared, the log row, a new password equal to the current one, the same answer for an address with no account, a disabled and a soft-deleted account, a token issued in the same millisecond as a reset, and a login and a reset on real threads at the same moment |
| `AccountRaceTest` | Integration | A newer code is requested while the password of a sign-up or a reset is being hashed: the older code no longer works and nothing is written. The test wraps the real `PasswordHasher`, so the order is the same on every run |
| `CreateAdminCommandTest` | Integration | The console command, in an application started without the web server and with a terminal played by the test: the administrator with its role and its two log rows, who can log in; a second administrator; differing passwords, a value that breaks a rule of sign-up, a taken username or address, a missing `ADMIN` role, and input that ends early, each of which creates nothing; the password is never printed; and two commands at the same moment |
| `EmailCodeCleanupJobTest` | Integration | The cleanup job, called directly: an expired code is deleted and a valid one is kept, the counts of windows long over are deleted, a run deletes at most its batch, a second run changes nothing, and no job is scheduled in the tests |
| `PermissionProbeController`, `EndpointPermissionTest` | Integration | A controller that exists only in the tests, with one endpoint for each kind of declaration and one that declares nothing, and the checks through it: no token, a user with no role (`ACCOUNT_NO_ROLE`), roles that do not grant the permission (`FORBIDDEN`), each role that grants it, a user with two roles, an endpoint that declares nothing, a path with another method, a role granted or taken away between two requests, and a disabled account |
| `EveryEndpointDeclaresWhoMayCallItTest` | Integration | Every endpoint of the running application declares exactly once who may call it. See Rules Checked By Tests |

The tests of the user module are in `backend/src/test/java/com/opsflow/user`:

| Class | Layer | Purpose |
| --- | --- | --- |
| `UserIntegrationSupport` | Helper | Accounts with roles made directly in the database, a login through the real endpoint, the calls of the four user endpoints, and the role and account log rows of a user |
| `UserListTest` | Integration | The user page: whom an administrator and a manager see, the search without regard to case and with the characters of a pattern taken as plain, the role buttons and No role, neither filter widening the list, the refusals, and the paging by key forward and back, with a user added between two page reads |
| `UserRolesTest` | Integration | Replacing the roles: the roles and the log rows it writes, the limits of a manager, nobody changes their own roles, each refusal with its result code, and an actor whose authority is gone when the transaction reads it |
| `UserRolesRaceTest` | Integration | The races of a replacement: two administrators who remove each other, two changes to one user, the same grant twice, a manager demoted while acting, and a change that waits for the locked row of its actor |
| `UserStatusTest` | Integration | Disabling, enabling, and soft-deleting: what each writes and logs, the roles that stay, the answer the user gets on the next request, the limits of a manager, nobody changes their own account, each refusal with its result code, and an actor whose authority is gone when the transaction reads it |
| `UserStatusRaceTest` | Integration | The races of a status change: two administrators who disable each other, two disablings of one user, a grant racing a disabling, a disabling racing a deletion, an actor disabled while acting, and a password reset of the auth module racing a deletion |

Run the tests from `backend/` with `mvnw test`. The first run downloads the MySQL image.

**Which tests to run.** While a change is made, run the tests of the module it touches, which take seconds or a minute: `mvnw test -Dtest='com.opsflow.ticket.**'` for the tickets, or one class, `mvnw test -Dtest=TicketTest`, or one test, `mvnw test -Dtest='TicketTest#aReleaseReturnsTheTicketToThePoolWithAReason'`. A change of `common`, of the schema, or of the test support touches every module: run them all. **Before every commit, run the whole suite**, and after the push wait until the job of continuous integration is green before the branch is merged into main. Nothing else blocks a merge, because main has no branch protection (the owner's choice, 2026-11-17). Every application test is recorded in the Application tests section of the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md), next to the script check that it repeats.

The script in [`db/verification`](../../db/verification/) keeps checking the database on its own. The integration tests repeat those checks against the application code.

### Continuous integration

GitHub Actions runs `mvnw test` on a clean Linux machine on every push to any branch. The job is [`.github/workflows/backend-tests.yml`](../../.github/workflows/backend-tests.yml), and its result is shown next to the commit on GitHub. The frontend has a job of its own, [`frontend-tests.yml`](../../.github/workflows/frontend-tests.yml), described in the [page design](../frontend/page-design.md#running-the-frontend-locally).

- It installs Java 17 and nothing else. The machine has Docker, so the integration tests start their MySQL, Redis, and RabbitMQ containers as they do locally.
- It needs no secret and no environment variable: the tests make their own accounts with random passwords and their own token signing key, and no test reads `OPSFLOW_DB_*`, `OPSFLOW_MIGRATION_*`, `OPSFLOW_TOKEN_SIGNING_KEY`, `OPSFLOW_MAIL_*`, `OPSFLOW_REDIS_*`, or `OPSFLOW_RABBITMQ_*`, and no test sends a mail. Keep it so, and check a change of the test set-up by running the tests with those variables unset.
- A new test class is run without any change to the job, as long as its name ends in `Test` or `Tests`. Maven skips a class with another name without a warning.
- It does not run [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1), which needs the local MySQL service and the root password. That script is still run by hand when the schema, the privileges, or the flows change.

## Authentication

Login, the check of every request, the email codes, sign-up, and the password reset are built in the `auth` module. The rules are in [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery), and the endpoints in [api-design.md](../api/api-design.md#authentication).

| Class | Purpose |
| --- | --- |
| `AuthController` | `POST /auth/login`, `GET /auth/me`, `GET /auth/session-events`, `POST /auth/email-codes`, `POST /auth/sign-up`, and `POST /auth/password-reset` |
| `LoginService` | The login steps. It has no transaction: it reads the user, checks the password, and then calls `LoginRecorder` through `TransactionRetry` |
| `LoginRecorder` | The short transaction of step 3. It locks the user row, confirms that the hash that was checked is still the current one, and records the result. It returns the outcome and never throws a refusal, because an exception would roll back the count of a failed attempt |
| `LoginLockPolicy` | The lockout rules as pure calculation: the failures in a row that still count, and until when a failure locks the account |
| `PasswordHasher` | Hashes and checks passwords, and refuses to run inside a transaction |
| `TokenService` | Issues the token and checks its signature and expiry |
| `TokenAuthenticator` | The check of the account on every request: loads the user and the roles and refuses an account that may not use the token, and a token of a login that a newer login ended (`SESSION_REPLACED`) |
| `SessionIds` | Makes the id of a login: 16 random bytes in hexadecimal, written to `users.session_id` and carried by the token |
| `SessionEvents` | The open event streams of the signed-in pages, in memory, per user and login. After a login commits it sends `session-replaced` on the streams of the other logins of the account and closes them; a comment every 25 seconds keeps a quiet stream open and finds the ones whose page has gone |
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
- **Every request** loads the user and the roles from the database with plain reads (`TokenAuthenticator`). A token is accepted only if it was issued after `password_changed_at` and carries the current `session_id`. `LoginRecorder` never issues a token at or before that time: a login in the same millisecond as the reset before it gets the next millisecond.
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
- **The login counts of a client address** are kept in Redis by `LoginLimits` (Q-07): the failures of an account from an address, the lock of that address out of the account, and the failures of an address on any username. They are many small counters that expire, written on every failed login, and a lost one costs nothing: Redis is the right place, and the user row keeps only the ceiling of the day. Each change is one Lua script, so it is atomic. The days, the windows, and the ends of the locks come from the `Clock` bean, as every other time, so the tests move them with the clock; the expiry of a key only clears it away. When Redis cannot be reached, `LoginLimits` answers as if the address had no count and logs a warning at most once a minute. The rules are in [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery).
- **One signed-in device.** `LoginRecorder` and `AccountWriter.create` write a new `session_id` in the transaction that holds the user row, and the token carries it as the claim `sid`. `LoginService` calls `SessionEvents.loggedIn` after the commit, so a stream is never told about a login that rolled back. A stream is opened by `GET /auth/session-events` with the ordinary token check, and after it is registered the session id is read once more, so a login that committed between the check and the registration still reaches it. The streams are kept in memory: enough for one instance, like the scheduled jobs. A second instance needs the logins of one instance to reach the streams of the other, for example through Redis publish and subscribe. A stream ends after 15 minutes and the page opens a new one, which checks the token again. The async dispatch that ends a stream is allowed in `SecurityConfig`, because the request was already decided when the stream opened. A proxy in front of the application must not buffer `text/event-stream` (for nginx, `proxy_buffering off` on that path).
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
- A wider reach that is itself a row of the matrix is a permission of its own: `TICKET_VIEW` opens the ticket screens, and `TICKET_VIEW_ALL` shows everyone's tickets on them. A reach that depends on the data, such as an operator who may resolve only a ticket assigned to them, stays a rule of the service; when a module is built, such a rule gets a permission where it would otherwise ask for a role. The ticket module added two such permissions: `TICKET_ASSIGN_PROTECTED` (administrator), to give a ticket to an administrator, and `TICKET_HANDLE_ANY` (administrator, manager), to release, resolve, or cancel a ticket that is not one's own. The order records added `ORDER_VIEW_ALL` (administrator, manager, viewer), to read everyone's order records and filter them by who ordered, as `TICKET_VIEW_ALL` does for tickets (Q-34).

So each kind of change has one place:

| Change | What it needs |
| --- | --- |
| A new endpoint | One line: the annotation with the permission of its row in the matrix |
| A new action | A constant in `Permission`, added to the roles that grant it in `Role` |
| A role may do more or less | The list of that role in `Role` |
| A new role | A migration that inserts the `roles` row, and a constant in `Role` with its permissions and with whether it is protected (`Role.isProtected`). No endpoint and no page changes |

`RoleTest` fails when the enum and the table differ, and `PermissionTest` fails for a permission that no role grants, and for a role list that breaks a principle of the matrix.

## Users

The `user` module holds the user page and the changes one user makes to another (see [User page](../database/database-design.md#user-page) and [Authority over roles and accounts](../database/database-design.md#authority-over-roles-and-accounts)). The page is `GET /users`, and the changes are four: replacing the roles of a user (`PUT /users/{id}/roles`), disabling (`POST /users/{id}/disable`), enabling (`POST /users/{id}/enable`), and soft-deleting (`DELETE /users/{id}`); see [api-design.md](../api/api-design.md#users).

| Class | Purpose |
| --- | --- |
| `UserController` | The five endpoints. The list needs the permission `USER_VIEW`, replacing the roles `ROLE_ASSIGN`, and the other three `USER_MANAGE`; a permission only opens the endpoint |
| `UserService` | No transaction. For the list it decides from the permissions of the caller whom they may see, and checks the search and the role buttons. For a change it turns the role codes into roles, refuses a code that is no role, and runs the transaction through `TransactionRetry` |
| `UserChangeWriter` | The transaction, in the eight steps of [Steps of a change to a user](../database/database-design.md#steps-of-a-change-to-a-user). The first three steps are one private method that the later changes use too |
| `UserRepository` | The SQL: the query of the user page, the lock on a user row, the roles of a user, the lock on a role row, the grant, the revocation, the status, the soft deletion, and the count of usable holders of a role |

How it works:

- **The user page is the first list**, and the model for the later ones. The query is built from the filter: the users the caller may not see are left out by a `NOT EXISTS` on their protected roles and by their id, the search is a `LIKE` with `%`, `_`, and the escape character escaped, and the role buttons are an `EXISTS` on the chosen roles, or a `NOT EXISTS` on any role for No role. The page is read by key on `(username, id)`, with `PageQuery.fetchSize()` rows, and `PageResponse.of` makes the cursors from the username and the id. The roles of each row come from a subquery with `GROUP_CONCAT`, so a page is one query. It reads no lock and needs no index, because the table of users is small (see [User page](../database/database-design.md#user-page)).
- **The authority is decided in the transaction, not at the endpoint.** The roles a request arrives with are not used. The transaction locks the user rows of the actor and of the target in ascending id and reads the status and the roles of both again. An actor who was demoted or disabled a moment ago is therefore refused, and of two administrators who take the role from each other at the same moment exactly one succeeds.
- **The service names no role to decide what is allowed.** `ROLE_ASSIGN` allows a change of roles, and `ROLE_ASSIGN_PROTECTED` is needed when the target holds a protected role or the change adds one. `USER_MANAGE` allows a change of status, and `USER_MANAGE_PROTECTED` is needed when the target holds a protected role. `Role.isProtected` says which roles are protected (`ADMIN` and `MANAGER`). The one role the class names is `ADMIN`, as the role that must always have a usable holder.
- **What the actor saw is part of the request.** The roles under the lock are compared with `expectedRoles`, and a difference is refused with `USER_CHANGED`. Two replacements of one user therefore never mix.
- **A status change starts from one status.** Disabling expects an active account and enabling a disabled one, read under the lock. A request that finds the other status is refused with `USER_CHANGED` and the current status, so of two disablings at the same moment one is refused and one log row is written. A soft deletion accepts both, sets `deleted` and `DISABLED`, and keeps the row, the roles, the username, and the email. Each of the three writes its row of [User and account logs](../database/database-design.md#user-and-account-logs).
- **Not built yet:** disabling and soft-deleting do not return the tickets of the user to the pool. That step follows the ticket rules and comes with the ticket module (T-21); the methods say so.
- **Only the difference is written.** A role that stays keeps its row. Each role removed writes `ROLE_REVOKED` and each role added `ROLE_GRANTED`, through `AuditLogWriter`, with `replace roles` as the remark and the `roleId`, the `roleCode`, and the `username` as the data.
- **Administrator protection.** When the target holds `ADMIN`, a replacement of roles, a disabling, and a deletion of an active account lock the `ADMIN` row in `roles` before they change anything and count the usable administrators afterwards. Today the count cannot be zero, because only an administrator who is not the target can take `ADMIN` away; the check is the second line of defence of the design and stays for a later role that may assign protected roles.
- **Every refusal is thrown**, so the transaction rolls back and neither a role nor a log row is written. The result of each refusal is in the table under Steps of a change to a user.
- **Lock order**: `users` (ascending id), then `roles`. Two changes that name each other's actor lock the same rows in the same order, so they wait and do not deadlock.
- The application account needs `DELETE` on `user_roles` for a revocation. The privilege script grants it (see [environment-setup.md](../environment-setup.md)).

## Warehouse Items and Products

Built on 2026-11-15 for Phase 4 (T-39, T-41, T-42; [backend-v1.9.md](../iterations/backend-v1.9.md)). The rules are in [Product and Inventory](../database/database-design.md#product-and-inventory), and the endpoints in [Warehouse Items, Images, and Products](../api/api-design.md#warehouse-items-images-and-products).

**One module, `warehouse`.** Items, their images, and products live in one package, because their writes cross: deleting an item delists its product in the same transaction, and listing reads the stock of the item under a lock. One module keeps those transactions in one place, and the package rules of `PackageDependencyRulesTest` stay as they are. The orders of Phase 5 read the same tables with their own SQL, in their own module.

| Class | Purpose |
| --- | --- |
| `ItemController` | `GET /items`, `POST /items`, `PUT /items/{id}`, `DELETE /items/{id}`. The starting quantity is declared as `Object`, so it reaches `Quantities` as it was sent |
| `ProductController` | `GET /products`, `GET /products/listable`, `POST /products`, `POST /products/{id}/delist` |
| `ImageController` | `POST /item-images` (`ITEM_MANAGE`) and `GET /images/items/{file}` (`@Public`). The upload declares no `consumes`, so a body that is not multipart reaches it and is answered `UNSUPPORTED_MEDIA_TYPE` by `ApiExceptionHandler`; with `consumes` no method would match, and the permission layer would refuse it as `FORBIDDEN` |
| `ItemService`, `ProductService` | Check the values (the name, the price, the starting quantity through `Quantities`, the image path), then call the writer through `TransactionRetry`. No transaction of their own |
| `ItemWriter` | The transactions of adding, editing, and deleting an item |
| `ProductWriter` | The transactions of listing and delisting |
| `WarehouseRepository` | The SQL on `inventory`, `products`, `inventory_transactions`, `inventory_item_events`, and `product_listing_events`, and the three lists |
| `SkuGenerator` | 8 characters from `A-Z` and `0-9` from `SecureRandom`. `ItemWriter` turns a duplicate key on `uk_inventory_sku` into `SkuTaken`, and `ItemService` generates another, up to `opsflow.warehouse.sku-attempts` |
| `ImageStore` | Checks the first bytes of an upload, stores it under a random name, and resolves a stored path to a file. It does no database work |
| `Quantities` (in `common/validation`) | The rule of Valid quantities, shared with the stock changes and the orders of Phase 5 (T-41). A fraction, text, a boolean, or a number out of range is `INVALID_QUANTITY` |

**The write paths.** Each runs in one transaction under the deadlock and lock-wait retry, with the lock order of Concurrency by Module: `products`, then `inventory`.

| Path | Reads and locks | What stops a concurrent request | Writes |
| --- | --- | --- | --- |
| Add an item | Nothing is locked; the SKU is generated before the transaction | `uk_inventory_name_active` refuses a second item with the name (`ITEM_NAME_TAKEN`); `uk_inventory_sku` refuses a repeated SKU, and the service generates another, up to 5 attempts in all | The item, a `RESTOCK` with the reason `initial stock` when the starting quantity is above 0, an `ITEM_CREATED` event, and the `ITEM_CREATED` log row |
| Edit an item | The product row of the item `FOR UPDATE`, if any, then the `inventory` row `FOR UPDATE` | A listing takes the product row first too, so the edit sees `LISTED` and refuses with `ITEM_LISTED`; a deletion that committed first leaves the item deleted (`NOT_FOUND`); the name key refuses a taken name | The name and the image path, and the `ITEM_UPDATED` log row with both before and after |
| Delete an item | The product row `FOR UPDATE`, then the `inventory` row `FOR UPDATE`; the time is taken after the locks | The second of two deletions waits and finds the item deleted (`NOT_FOUND`); an order holding the product row shared finishes first | The product delisted when `LISTED`, with its `DELISTED` event and log row; `deleted_at` and `deleted_by`; the `DELETED` event with the stock it showed; the `ITEM_DELETED` log row. The tickets of the item are cancelled here in Phase 6 (T-21) |
| List an item | The product row `FOR UPDATE`, if any, then the `inventory` row `FOR SHARE`; the time is taken after the locks | A second listing waits and finds `LISTED` (`ALREADY_LISTED`), or, the first time, is refused by `uk_products_inventory`; a deletion waits or is waited for; a write-off of Phase 5 cannot change the stock that `NO_STOCK` was checked against | The product created, or set `LISTED` again with the new name and price; the `LISTED` event; the `PRODUCT_LISTED` log row |
| Delist a product | The product row `FOR UPDATE`; the time is taken after the lock | A second delisting finds `UNLISTED` (`ALREADY_DELISTED`); orders that hold the row shared finish first, so none is dated after the delisting | `UNLISTED` with `delisted_at` and `delisted_by`; the `DELISTED` event; the `PRODUCT_DELISTED` log row. The tickets of the product are cancelled here in Phase 6 (T-21) |

**Images.** `opsflow.warehouse.image-dir` names the folder, outside the web root, from the environment variable `OPSFLOW_IMAGE_DIR`, by default `./data/images` under the folder the application starts in. A file is stored at `items/<random UUID>.<jpg|png|webp>`, the extension taken from the type found in its first bytes. The upload is read whole into memory (2 MB at most) and checked before anything is written. Spring's multipart limit is set a little above 2 MB, so a larger upload is refused before it is read, and that refusal is answered `IMAGE_TOO_LARGE`. `GET /images/items/{file}` accepts only a name of the form the store makes, so a path such as `../` never reaches the file system. An `imagePath` sent with an item must have the same form and its file must exist. Files are never deleted.

**The lists** are paged by key in one fixed order, by name and then `id` (`PageQueryFactory.createInOrder`), with the search of a part of the name as on the user page (`LIKE` with `!` as the escape character). The item list and the catalog leave out deleted items; the product page shows `LISTED` products only.

**Tests.** In `backend/src/test/java/com/opsflow/warehouse`, all integration tests through the whole application against the MySQL container:

| Class | Purpose |
| --- | --- |
| `WarehouseIntegrationSupport` | The base of the classes below: accounts with roles, one token for each (a second login would end the first), the HTTP calls of the endpoints, image bytes of each type, and reads of the rows |
| `ItemTest` (12) | Adding with a generated SKU, the starting stock as a `RESTOCK`, the event, and the log row; no transaction for a quantity of 0; refused quantities (a fraction, text, a negative, one above 999,999, a boolean); the name rule, a name in use without regard to case, and the name of a deleted item free again; the stock page by name with the search, the paging, and `listed`; editing and its log, an edit that changes nothing, `ITEM_LISTED`, `NOT_FOUND`, and a taken name; an image path that the upload did not return; deleting with its product delisted, the stock left, the events, and the log rows; and the permissions |
| `ProductTest` (8) | The first listing with its event and log row; listing again with the same id and the new name and price; `NO_STOCK`; `ALREADY_LISTED`, `ALREADY_DELISTED`, and `NOT_FOUND`; the price rule; the product page with only products on sale and no stock; the catalog; and the permissions |
| `ImageTest` (6) | The three types stored under random names whatever the file is called; any other content refused; the 2 MB limit; the image served without a token with its type and a long cache; any other name not found; the permission, and a body that is not multipart |
| `WarehouseRaceTest` (8) | Two additions with one name; two deletions; two listings; a listing and a deletion; a listing and a delisting; an edit and a listing; a repeated SKU replaced, and adding failing when every attempt collides; the time of a deletion not earlier than the listing |

`QuantitiesTest` (in `common/validation`, 7, unit) checks the rule of Valid quantities value by value, for every use.

## Orders and Stock Changes

Built on 2026-11-16 for Phase 5, the first part (T-10, T-12, T-30, T-41; [backend-v1.10.md](../iterations/backend-v1.10.md)). The rules are in [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) and [Manual stock changes](../database/database-design.md#manual-stock-changes), and the endpoints in [Orders and Stock Changes](../api/api-design.md#orders-and-stock-changes).

**Two modules.** An order is placed in the `order` module, with all of its SQL in `OrderRepository`, so that its lock order can be read in one place, as Layout says. The manual stock changes belong to the stock page and live in `warehouse`, next to the other changes of an item. Both lock the same `inventory` rows and follow the lock order of Concurrency by Module.

| Class | Purpose |
| --- | --- |
| `OrderController` | `POST /orders` (`ORDER_PLACE`). The quantity of a line is declared as `Object`, so it reaches `Quantities` as it was sent. A new order is `201`, the original order of a key used before `200` |
| `OrderService` | No transaction. Checks the shape of the request (`VALIDATION_FAILED`, not an attempt), returns the original order of a key used before, makes the order number, checks each quantity (`INVALID_QUANTITY`, recorded), then runs `OrderWriter` through `TransactionRetry`. A refusal of the transaction and `BUSY` are recorded by `OrderAttemptRecorder` and answered with their `orderNo` |
| `OrderWriter` | The transaction of an order, in the four steps of Create order |
| `OrderAttemptRecorder` | Writes the `order_attempts` rows of a refused attempt in a new transaction (`REQUIRES_NEW`), after the order transaction has rolled back, with the current name of each product. Best effort: a failure is logged and the refusal is still answered |
| `OrderRepository` | The SQL of an order: the products shared, the SKU with a plain read, the order and its items, the stock locked and deducted, the `SALE` rows, the attempts, and the order of a key |
| `OrderNumbers` | `ORD-` and a random UUID without hyphens, in lower case. On a clash with `uk_orders_order_no`, `OrderService` makes another, up to `opsflow.order.number-attempts` |
| `ItemController` (in `warehouse`) | Also `POST /items/{id}/stock-in` (`STOCK_IN`), `/write-off` (`STOCK_WRITE_OFF`), and `/correction` (`STOCK_CORRECT`) |
| `StockService`, `StockWriter` (in `warehouse`) | The checks of a stock change (the quantity through `Quantities`, the reason, a correction that changes nothing), and its transaction |

**The write paths.** Each runs in one transaction under the deadlock and lock-wait retry.

| Path | Reads and locks | What stops a concurrent request | Writes |
| --- | --- | --- | --- |
| Place an order | Each product `FOR SHARE`, in ascending id; the SKU with a plain read; each `inventory` row `FOR UPDATE`, in ascending id, all locked before any is checked. The order takes its time after the product locks, and its movements and attempt after the stock locks | A delisting, a listing, an edit, or a deletion needs a product row exclusively, so it waits for the order or the order sees its result (`PRODUCT_DELISTED`, `PRODUCT_CHANGED`). Another order, a stock change, or a listing on the same item waits for the stock lock, so the stock never goes below zero and each snapshot follows the last. `uk_orders_user_idempotency` makes a second request with the key wait for the first and then fail, and the service returns the order of the first | The order, its items, the deduction (`available_quantity` down, `sold_quantity` up), one `SALE` per item, and one `COMPLETED` attempt per line. No `operation_logs` row: `order_attempts` is the record of an order |
| Record a refused attempt | The name of each product, with a plain read; no lock | Nothing to stop: it only inserts, after the order has rolled back. `uk_order_attempts_order_no_product` is the last guard against a number used twice | One attempt per line with the result and nothing deducted |
| Stock in, write-off, correction | The `inventory` row `FOR UPDATE` | An order, a listing that reads the stock, a deletion, and another change wait for the same row. A correction compares the stock under the lock with the stock the person saw (`STOCK_CHANGED`), so a stale screen cannot undo a sale | The new `available_quantity` (never `sold_quantity`), the movement with the stock before and after and the reason, and the `STOCK_RESTOCK`, `STOCK_WRITE_OFF`, or `STOCK_ADJUSTED` log row |

**How a refusal leaves the transaction.** `OrderWriter` throws `ApiException` for `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, and `OUT_OF_STOCK`, so the proxy rolls the order back, and `TransactionRetry` passes it on without a retry. Two internal exceptions carry the cases the service handles itself: `KeyUsed` (a key that committed a moment ago: the answer is that order) and `NumberTaken` (a new number and another try).

**Tests.** In `backend/src/test/java/com/opsflow/order` and `.../warehouse`, against the MySQL container:

| Class | Purpose |
| --- | --- |
| `OrderIntegrationSupport` | The base of the order classes: accounts with roles and their tokens, items and products made through the warehouse endpoints, the call of `POST /orders` with or without a key, and reads of the stock, the movements, and the attempts |
| `OrderTest` (12) | An order with its items, the deduction, the `SALE`, and the attempt; a repeated key; a key that is the user's own and not used up by a refusal; an order of two products, all or nothing; `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, `OUT_OF_STOCK`, and `INVALID_QUANTITY`, each recorded with its number; a malformed request and an unknown product, which are no attempt; the permissions; and `BUSY` recorded after the last retry |
| `OrderRaceTest` (8) | Eight orders on one stock; one key sent four times at once; an order against a delisting, a deletion, a write-off, and a correction from a screen the order made stale; two orders of the same three items in opposite order, 12 rounds, with no deadlock and no retry (D6, T-17) |
| `StockTest` (9) | A stock-in, a write-off, and a correction in both directions, each with its movement, snapshots, and log row; `OUT_OF_STOCK`, `STOCK_CHANGED`, a correction that changes nothing, the reason, `INVALID_QUANTITY`, the `INT` limit, a deleted item, and the permissions |

`QuantitiesTest` checks the ranges of a stock change, a correction, and an order line, and what an attempt records for a quantity that is not valid.

## Tickets

Built on 2026-11-16 for Phase 6 (T-12, T-14, T-21, T-33, T-35, T-36, T-39, T-40, T-44, T-47, T-67; [backend-v1.11.md](../iterations/backend-v1.11.md)). The rules are in [Tickets](../database/database-design.md#tickets) and the ticket rows of [Concurrency by Module](../database/database-design.md#concurrency-by-module), and the endpoints in [Tickets](../api/api-design.md#tickets).

| Class | Purpose |
| --- | --- |
| `TicketController` | `GET /tickets` and `GET /tickets/{id}` (`TICKET_VIEW`), `POST /tickets` (`TICKET_CREATE`), and the changes: `take` (`TICKET_TAKE`), `assign` and `reassign` (`TICKET_ASSIGN`), `release` (`TICKET_RELEASE`), `resolve` (`TICKET_RESOLVE`), `cancel` (`TICKET_CANCEL`), and the single and batch `archive` (`TICKET_ARCHIVE`). Every change answers with the ticket as it is after it |
| `TicketService` | No transaction. Checks the shape of a request (the type, the texts and their limits, 0 to 20 items each once, the order number written as the numbers are made, the follow-up number in upper case), makes the ticket number and makes another on a clash, runs each change through `TransactionRetry`, archives a batch one ticket at a time, and reads the lists and the detail with what the caller may see |
| `TicketWriter` | The transactions: opening a ticket and the eight changes, each through the status gate, with the history row and the log row of Ticket logs |
| `TicketAccess` | Who sees which ticket, asked by the list, the detail, and every change, and whether a set of roles grants a permission |
| `TicketStatus` | The five statuses and the one table of the allowed transitions |
| `TicketCascades` | The steps that a change of another module starts in its own transaction (`MANDATORY`): `returnToPool` from `UserChangeWriter.disable` and `softDelete`, and from `replaceRoles` when the user could take tickets before and cannot after, and `cancelLinkedTo` from `ItemWriter.delete` and `ProductWriter.delist`. The SYSTEM identity acts, and the log rows have no operator |
| `TicketArchiveJob` | The archive job (see Scheduled Jobs) |
| `TicketNumbers` | 8 characters from `A-Z` and `0-9` from `SecureRandom`; a typed number in upper case |
| `TicketRepository` | The SQL of the module. The lists are read by key on the source (downward, so `SYSTEM` comes first in either direction), the time of the screen, and the id; the cursor key is the source, a bar, and the UTC wall time |
| `UserOptionController`, `UserService.options` (in `user`) | `GET /user-options`, the user picker. The two uses need different permissions, so the endpoint is `@AnyAccount` and the service checks the permission of the use |

**The write paths.** Each runs in one transaction under the deadlock and lock-wait retry. Lock order: `users` (ascending id), then `tickets`, then `inventory`.

| Path | Reads and locks | What stops a concurrent request | Writes |
| --- | --- | --- | --- |
| Open a ticket | The creator's user row `FOR UPDATE` and the count of their `OPEN` tickets; the followed ticket and the order attempt with plain reads; the new ticket row; each chosen item `FOR SHARE`, in ascending id, all locked before any is judged | Two tickets of one creator wait at the user row, so the limit holds. A deletion of an item needs it exclusively, so it waits for the ticket and cancels it, or the ticket waits and sees the item deleted (`ITEM_CHANGED`). `uk_tickets_ticket_no` refuses a number used before | The ticket, its links, and `TICKET_CREATED` |
| Take | The taker's user row `FOR UPDATE`, their roles, and the count of their tickets in progress; the ticket row `FOR UPDATE` | A disabling or a change of roles waits for the user row, or the take sees it. Two takes of one ticket: the second waits for the ticket row and finds it no longer `OPEN` (`TICKET_CHANGED`). Two takes by one person at 98 wait at the user row | `IN_PROGRESS` and the assignee, a history row, and `TICKET_ASSIGNED` (`taken`) |
| Assign, reassign | The rows of the actor and the new assignee `FOR UPDATE`, in ascending id, and the roles of both; the count of the assignee; the ticket row `FOR UPDATE`, and for a reassignment the assignee compared with the one the actor saw | As for a take, for both people; a reassignment that meets a release, a resolve, or another reassignment finds another status or assignee under the ticket lock (`TICKET_CHANGED`) | The assignee (and `IN_PROGRESS` for an assignment), a history row, and `TICKET_ASSIGNED` (`assigned`) or `TICKET_REASSIGNED` |
| Release, resolve, cancel | The ticket row `FOR UPDATE`; whether the caller may see it, and whether it is theirs | The status gate and the compared assignee: the second of two changes finds another status (`TICKET_CHANGED`) | The status and its fields, a history row for a release, and the log row |
| Archive | The ticket row `FOR UPDATE` | The status gate, one ticket per transaction, so a batch, the job, and a person never archive a ticket twice | `CLOSED`, `closed_by`, `closed_at`, and `TICKET_CLOSED`, with `batchId` in a batch or a run of the job |
| Return to the pool (a user disabled or deleted, or left with no role that may take tickets) | After the user row of `UserChangeWriter`: every `IN_PROGRESS` ticket of the user `FOR UPDATE`, in ascending id | A take or an assignment for the user waits for the user row and then fails the check. A release or a resolve by the user at the same moment: one of the two finds the ticket changed | Each ticket `OPEN` with no assignee, a history row by the SYSTEM identity, and `TICKET_RELEASED` with no operator |
| Cancel the tickets of an item (deleted) or a product (delisted) | After the product and item rows of the warehouse change: every `OPEN` or `IN_PROGRESS` ticket linked to the item, `FOR UPDATE OF` the tickets and `FOR SHARE OF` the links, in ascending id | A ticket opened on the item waits for the item, as above. A ticket resolved or cancelled a moment earlier is not read | Each ticket `CANCELLED` by the SYSTEM identity with `ITEM_DELETED:` or `PRODUCT_DELISTED:` and the id, `TICKET_CANCELLED`, and the ids and numbers in `cancelledTicketIds` and `cancelledTicketNos` of the item or product row |

**How a refusal is chosen.** A ticket the caller may not see is `NOT_FOUND` before anything else, the same as a ticket that does not exist. A take is the exception: the pool is open to everyone, so a ticket that just left it is `TICKET_CHANGED`, with `ticketStatus` only for a caller who may see it now. A rule of the ticket that the caller's permission does not cover is `NOT_ALLOWED`: an operator, who lacks `TICKET_HANDLE_ANY`, releasing, resolving, or cancelling a ticket that is not theirs. The roles of the actor that decide an assignment are read again under the lock of the actor's user row.

**Tests.** In `backend/src/test/java/com/opsflow/ticket`:

| Class | Purpose |
| --- | --- |
| `TicketStatusTest` (2, unit) | Every pair of the five statuses against the table of Status transitions; only `CLOSED` and `CANCELLED` are final |
| `TicketIntegrationSupport` | The base of the integration classes: accounts with roles and their tokens, the HTTP calls, tickets opened through the service, items and tickets in any status made directly, and reads of the rows, the history, and the log rows |
| `TicketTest` (27) | Opening a ticket with its number, links, and log; each check of the fields; an order number of a refused attempt, typed in another case; a follow-up of each status, and a link the caller cannot see; a deleted item that refuses the whole ticket; the limit of 99 open; a take and a lost take; the limit of 99 in progress for a take, an assignment, and a reassignment; each kind of assignee that is not eligible, a manager who cannot give a ticket to an administrator, and the reason; a reassignment from a stale screen, to the same person, and what the previous assignee loses; a release; a resolve by the assignee and by a manager; a cancel of one's own and of another's, and a final ticket that never moves; the single and the batch archive with one `batchId`; the job after 7 days and at 99; what an operator sees on each screen and in the detail; `SYSTEM` first in both directions with the paging; the search and a number used alone; a disabled and a deleted user; a deleted item and a delisted product, with the cause on the page; the user picker; a user made a viewer who gives back the tickets in progress, with the warning before it, and a manager made an operator who keeps them; the tickets of one item, and no log or history row without a reason; the deleted users on request, the tickets in progress of a row, the tickets a disabling returned named in its row, and the rows about one user; and the times taken from the clock of the application |
| `TicketRaceTest` (13) | Two people take one ticket; a user who loses the role to take tickets while taking one; a person at 98 takes two; a person at 98 opens two; a reassignment against a release; a user disabled while taking a ticket; a ticket opened on an item as it is deleted; two batch archives of the same tickets; and, for T-34, a reassignment against a resolution and against a cancellation, the archive job against a manager's archive, a user disabled while releasing and resolving their own tickets, and a ticket opened on an item as its product is delisted |

## Logs

Built on 2026-11-16 for Phase 5 (T-46; [backend-v1.10.md](../iterations/backend-v1.10.md)). The rules are in [Logs](../database/database-design.md#logs), and the endpoints in [Logs](../api/api-design.md#logs).

**One module, `log`.** The two logs read tables that belong to `warehouse` (the events and the movements) and to `order` (the attempts). A module may not use the repository of another, so the reads are a module of their own, which writes nothing and locks nothing.

| Class | Purpose |
| --- | --- |
| `LogController` | The six endpoints: `/listing-records`, `/order-records` (`PRODUCT_VIEW`; everyone's records and the person filter `userId` with `ORDER_VIEW_ALL`, otherwise the caller's own, decided in `LogService`), `/item-records`, `/stock-movements` (`STOCK_VIEW`), `/deleted-items` (`DELETED_ITEMS_VIEW`), and `/product-options` (`PRODUCT_VIEW`). A price is written as a string, as everywhere |
| `LogController` (audit) | `/audit-records` and `/audit-order-records` (`AUDIT_VIEW`), the audit log page. The data of a row is returned as the JSON it was written with |
| `LogService` | Checks the filters: a positive id, the time filter through `DayRanges`, the search, and the order number, which is used alone and written as the numbers are made (`ORD-` upper, the rest lower) |
| `LogRepository` | The SQL. A log is read by key on `created_at` and then the id, in the direction chosen, with the product or item index when one is chosen and the time index otherwise; the cursor key is the UTC wall time as ISO text. The usernames come from a join on `users`. The deleted items are read by `deleted_at` and the id, and the picker by name and the id |
| `DayRanges` (in `common/time`) | The time filter of every list: a start day, or a start day and an end day, in the zone of `opsflow.time-filter.zone`, turned into a UTC range from the start of the first day to the start of the day after the last. Each refusal is `VALIDATION_FAILED` on its field |

**Tests.** `AuditTest` (5, integration): the rows of an area newest first with who did it and the data, the action of the area only, a user, System, the area required, the Sydney days, the Orders area with the result and the user, and only an administrator and a manager reading. `LogTest` (10, integration, in `backend/src/test/java/com/opsflow/log`): each kind of record with what it shows, an operator who reads only their own order records and is refused a person filter while a viewer and a manager filter by who ordered, newest and oldest first, one product or item chosen, a deleted item that keeps its records, the order number typed in capitals and refused with another filter, the time filter on Sydney days, the paging forward and back, the Deleted items list and its search by name or SKU, the picker without the products of deleted items, and every role reading. `DayRangesTest` (6, unit): the two forms, the zone in summer and winter and on the night the clocks change, and each refusal.

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
- **In the background.** `send` hands the mail to one worker thread and returns at once. The answer to a request therefore takes the same time whether or not a mail is sent, which matters because the answer must not show whether an address is registered, and a slow mail server or a slow broker does not hold a request. At most 200 wait for the worker; a mail beyond that is dropped with a log line.
- **Through RabbitMQ** (since 2026-11-18, Q-07). The worker puts the mail in the durable queue `opsflow.mail`, as a persistent message, and waits for the broker to confirm it. A listener with one consumer takes one mail at a time, so the mails leave in the order they were handed over, and gives it to the mail server. A mail handed over before a restart is therefore still sent after it. The scenario: a person asks for a code, and the application is restarted a second later; before the queue the mail was lost with the worker thread, and the person waited 60 seconds to ask again.
- **Three tries.** The listener tries the mail server three times, 2 and 4 seconds apart. A mail still refused goes to the dead letters, `opsflow.mail.dead`, where it is kept for a day so that a person can see that mail failed, and the next mail is taken.
- **A mail expires** in the queue after `opsflow.mail.queue-ttl` (10 minutes), the life of the code it holds, and goes to the dead letters then too. The code of a waiting mail is on the disk of the broker for at most that long.
- **Without the broker** the worker sends the mail itself, at once, as before the queue, and logs a warning. Mail never depends on RabbitMQ to work.
- **A failure is logged and not reported**: the request has been answered already. The log line holds the subject and the type of the error, never the address, the text, or the code. The person requests a new code after 60 seconds.
- **Without a mail server** (`OPSFLOW_MAIL_HOST` or `OPSFLOW_MAIL_PASSWORD` not set) the application starts, sends nothing, and logs a warning for each mail. The code is never written to the log, so a machine without a mail account cannot complete a sign-up. The tests replace the mail server with `Mailbox`.
- **The connection** uses the port 587, requires STARTTLS, so neither the password nor a mail travels unencrypted, and gives up after 10 seconds.
- **The password** of the mail account comes from the environment. `OpsflowProperties.Mail` leaves it out of its text, so it cannot reach a log through the configuration object.

## Configuration

The application connects to MySQL as `opsflow_app`, and Flyway connects as `opsflow_migrator` (see [environment-setup.md](../environment-setup.md)). These values come from environment variables and have no default, so a missing one stops the application from starting instead of falling back to a value in the repository:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` | The application connection |
| `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` | Flyway |
| `OPSFLOW_IMAGE_DIR` | The folder of the item images, outside the web root. Optional: by default `./data/images` under the folder the application starts in, which `backend/.gitignore` keeps out of git |
| `OPSFLOW_TOKEN_SIGNING_KEY` | The key that signs the login tokens: Base64 of at least 32 random bytes. The application refuses to start with a shorter key or one that is not Base64, and never prints it. Whoever has the key can make a token for any user, and changing it logs every user out. It is also the key of the stored email codes, so changing it makes the codes that are waiting useless |

The mail variables are optional, so the application starts without them and sends no mail:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_MAIL_HOST` | The SMTP server, `smtp.gmail.com` for the chosen account. Without it, or without the password, no mail is sent |
| `OPSFLOW_MAIL_PORT` | The SMTP port. The default is 587 |
| `OPSFLOW_MAIL_USERNAME` | The login of the mail account, which is its address |
| `OPSFLOW_MAIL_PASSWORD` | The password for SMTP. For Gmail it is an app password, not the password of the Google account. A secret |
| `OPSFLOW_MAIL_FROM` | The sender address, when it differs from the username. Gmail always sends as the account |

The Redis and RabbitMQ variables are optional too. Without them the application uses the local services of [environment-setup.md](../environment-setup.md), and when a service cannot be reached it goes on without it (see Stack):

| Variable | Used for |
| --- | --- |
| `OPSFLOW_REDIS_HOST`, `OPSFLOW_REDIS_PORT` | The Redis server. The defaults are `127.0.0.1` and 6379 |
| `OPSFLOW_REDIS_PASSWORD` | The password of Redis, when it has one. A secret. Empty by default, as the local service has none |
| `OPSFLOW_RABBITMQ_HOST`, `OPSFLOW_RABBITMQ_PORT` | The RabbitMQ broker. The defaults are `127.0.0.1` and 5672 |
| `OPSFLOW_MANAGEMENT_PORT` | The port of Actuator, 8081 by default. Not the port of the API, which a reverse proxy passes on |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Set to `native` only behind nginx, in the deployment, so the client address comes from `X-Forwarded-For`. Not set on the development machine |
| `OPSFLOW_RABBITMQ_USERNAME`, `OPSFLOW_RABBITMQ_PASSWORD` | The account of the broker. Without them the default local account of RabbitMQ is used, which RabbitMQ lets in from the same machine only. On another machine set both; the password is a secret |

The operating parameters are under `opsflow.*` in `application.yml` and are bound and checked at startup by `OpsflowProperties` (T-11). They hold no secret. The reasons for the values are in Operating Parameters in the database design.

| Property | Value | Used for |
| --- | --- | --- |
| `opsflow.retry.attempts` | 3 | The tries in total of a transaction that lost a deadlock or a lock wait, the first one included |
| `opsflow.retry.delays` | 50ms, 150ms | The wait before the second and the third try. The last one is reused if there are more retries |
| `opsflow.paging.page-size` | 10 | The default and the most of the `limit` of a list |
| `opsflow.paging.product-page-size` | 16 | The default `limit` of the product page (`PageQueryFactory.createProductPage`) |
| `opsflow.paging.product-page-max` | 24 | The most of the `limit` of the product page, where the person chooses 8, 16, or 24 |
| `opsflow.login.lock-steps` | 5: 1m, 10: 5m, 15: 15m | The failures of one account from one address in a day that lock that address out of it, and for how long |
| `opsflow.login.lock-until-midnight-at` | 20 | The failures of one account from one address from which that address is locked out until midnight |
| `opsflow.login.reset-zone` | `Australia/Sydney` | The time zone of the midnight at which the counts of failures start again |
| `opsflow.login.account-failures-per-day` | 100 | The failures of one account from every address in a day that lock the account until midnight |
| `opsflow.login.address-failures` | 50 | The failures of one client address on any username in one window, after which it is refused until the window ends |
| `opsflow.login.address-window` | 15m | The window of `address-failures` |
| `opsflow.mail.queue-ttl` | 10m | How long a mail waits in the queue before it expires there |
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
| `opsflow.quantity.max-stock-input` | 999999 | The most of a starting quantity, a stock-in, a write-off, or a correction |
| `opsflow.quantity.max-order-line` | 9999 | The most of one order line |
| `opsflow.warehouse.image-dir` | `${OPSFLOW_IMAGE_DIR:./data/images}` | The folder of the item images. The tests use a new temporary folder |
| `opsflow.warehouse.image-max-bytes` | 2097152 | The largest image accepted, 2 MB |
| `opsflow.warehouse.sku-attempts` | 5 | The generated SKUs tried before adding an item fails |
| `opsflow.order.max-lines` | 20 | The most product lines of one order |
| `opsflow.order.number-attempts` | 5 | The generated order numbers tried before an order fails on a clash |
| `opsflow.time-filter.zone` | `Australia/Sydney` | The zone of the days a person picks in a time filter |
| `opsflow.ticket.number-attempts` | 5 | The generated ticket numbers tried before opening a ticket fails on a clash |
| `opsflow.ticket.max-open-created` | 99 | The most `OPEN` tickets one person may have created |
| `opsflow.ticket.max-in-progress` | 99 | The most `IN_PROGRESS` tickets one person may hold |
| `opsflow.ticket.max-items` | 20 | The most items one ticket may link |
| `opsflow.ticket.max-archive-batch` | 100 | The most tickets one batch archive may name |
| `opsflow.ticket.auto-archive-after` | 7d | How long after `resolved_at` the job archives a resolved ticket |
| `opsflow.ticket.auto-archive-at-resolved` | 99 | The number of `RESOLVED` tickets at which the job archives all of them |
| `opsflow.ticket.archive-interval` | 1m | How often the archive job runs |
| `opsflow.ticket.archive-job-batch` | 500 | The most tickets one run of the job archives |
| `spring.servlet.multipart.max-file-size`, `max-request-size` | 3MB | A little above the image limit, so a larger upload is refused before it is read; that refusal is answered `IMAGE_TOO_LARGE` |

A module adds its own parameters to this table and to `OpsflowProperties` when it is built.

`logging.pattern.correlation` prints the request id on every log line (see Request id and logs).

The JDBC URL sets `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, so every session runs in UTC (see Time zone in the database design). A local file `application-local.yml` is ignored by git.

## Running

From `backend/`, with the five required variables set in the shell, and the mail variables if mail is to be sent:

```
mvn spring-boot:run
```

The first start makes Flyway apply `V1__initial_schema.sql` to `opsflow`. After it, run [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) once as `root` (T-02). The next start after a new migration applies it: `V2__seed_roles_and_system_identity.sql` adds the four roles and the SYSTEM identity, and needs no new grant because it adds no table. `V3__email_code_request_counts.sql` adds a table, so after it the grant for `email_code_request_counts` in the privilege script is run once as `root`. Until then a request for an email code fails with a permission error, which is the safe way to fail. On the local machine all three migrations are applied and the grant is run (2026-11-13).

The health check and the counters answer on the management port, 8081 by default: `http://localhost:8081/actuator/health` and `http://localhost:8081/actuator/metrics/opsflow.retry.busy`.

## Deployment

Decided on 2026-11-18 (Q-32): the whole system runs on one machine with Docker Compose, from [`deploy/compose.yaml`](../../deploy/compose.yaml), as one instance. It is the way to show OpsFlow working end to end without the services of the development machine; a server on the internet would run the same file with a domain and HTTPS in front.

```
cd deploy
.\new-env.ps1                    # once: makes .env with random secrets, shows none of them
docker compose up -d --build     # then open http://localhost:8000
docker compose run --rm backend create-admin    # once: the first administrator, at a hidden prompt
```

| Service | Image | What it does |
| --- | --- | --- |
| `mysql` | `mysql:8.4` | The database, in the volume `mysql-data`. When the volume is new, `deploy/mysql/01-accounts.sh` makes the migration account and the application account with the passwords of `.env` |
| `migrate` | `flyway/flyway:11.14.1`, the version of the backend | Applies the migrations of `backend/src/main/resources/db/migration` as the migration account, and ends. Its MariaDB driver asks the server for its public key to send the password, which is safe on the private network of the containers |
| `privileges` | `mysql:8.4` | Runs [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) for the application account after every migration, and ends. It gives the database-wide rights again first, because the script starts by revoking them, so it runs on every start and a new table gets its grant |
| `redis`, `rabbitmq` | `redis:8.0`, `rabbitmq:4.1` | The login counts and the mail queue. RabbitMQ has its own account with the password of `.env`, and keeps its queues in the volume `rabbitmq-data` |
| `backend` | built from [`backend/Dockerfile`](../../backend/Dockerfile) | The application, as a user that is not root, with Flyway off and no migration password. It starts after the rights are set and Redis and RabbitMQ are healthy, and is healthy when `/actuator/health/readiness` answers. The item images are in the volume `images` |
| `web` | built from [`frontend/Dockerfile`](../../frontend/Dockerfile) | nginx with the built pages and [`nginx.conf`](../../frontend/nginx.conf), which passes `/api` to the backend and does not buffer the event stream. The only port on the machine: `127.0.0.1:8000` |

- **Secrets.** `.env` holds the root password of MySQL, the two account passwords, the RabbitMQ password, and the token signing key, all made at random by `new-env.ps1`, which prints none of them; git ignores `.env`. [`.env.example`](../../deploy/.env.example) shows the names. The mail variables are taken from the shell when they are set there. The deployment has its own secrets, so a token of the development backend does not open it.
- **The client address.** nginx sets `X-Forwarded-For` to the address of the connection and never passes on one the client sent. The backend runs with `SERVER_FORWARD_HEADERS_STRATEGY=native`, so Tomcat takes the address from that header, and only when the request comes from a private address, such as the nginx container. On 2026-11-18 a login sent through nginx with a forged `X-Forwarded-For: 1.2.3.4` was counted for `172.18.0.1`, the address of the machine, and not for the forged one or for nginx. The development backend does not trust the header: nothing stands in front of it.
- **Health and counters.** Actuator answers on the management port 8081, which is not published and which nginx does not reach: `docker compose exec backend curl -s localhost:8081/actuator/health`. The security rules let a request through without a token on that port only; on the port of the API the same paths are refused. `opsflow.retry.retries` counts the transactions tried again after a lock conflict, and `opsflow.retry.busy` the `BUSY` answers.
- **One instance.** The scheduled jobs and the event streams of one signed-in device are kept by one instance. A second one needs a lock for the jobs and the logins sent between the instances through Redis; that waits until it is wanted.
- **After the machine restarts.** `mysql`, `redis`, `rabbitmq`, `backend`, and `web` have `restart: unless-stopped`, so they start again when Docker Desktop starts, and `http://localhost:8000` answers about half a minute later. Docker Desktop is not set to start with Windows: OpsFlow runs when the owner starts Docker Desktop (the owner's choice, 2026-11-19). A service stopped with `docker compose stop` or `down` stays stopped until `docker compose up -d`. `migrate` and `privileges` run only with `up`, which is when a new migration can arrive.
- **Starting over.** `docker compose down` stops the system and keeps the data; `docker compose down -v` deletes the volumes too, and then `.env` can be made again.

### Development and the deployment

The two run side by side and share only the source in the repository. Each has its own database, Redis, RabbitMQ, secrets, accounts, and data, so a user made in one does not exist in the other.

| | Development | Deployment |
| --- | --- | --- |
| What runs | The backend from `mvn spring-boot:run` and the dev server of Vite, against the MySQL, Redis, and RabbitMQ of the machine (see [environment-setup.md](../environment-setup.md)) | Every part in a container, from `deploy/compose.yaml` |
| Address | `http://localhost:5173` | `http://localhost:8000` |
| A change shows | At once: the backend restarts, the page reloads | After `docker compose up -d --build`, which builds the two images again from the source and replaces their containers |
| Used for | Every change, with the tests | Checking the whole system before a merge, and after a change to a migration, the configuration, a service, `compose.yaml`, or a Dockerfile |

- **The way of working.** Change and test in development; run `mvnw test` and `npm test`; then rebuild the deployment and click through it; then commit. A change of only Java or TypeScript code that the tests cover need not wait for the deployment.
- **The data survives a rebuild.** The rows are in the volume `mysql-data`, the queues in `rabbitmq-data`, and the item images in `images`, not in a container, so `--build` replaces the code and keeps them. A new migration runs on the next start through the service `migrate`, and the privilege script gives a new table its grant. Only `docker compose down -v` deletes the data.

### Backup and restore

Checked on 2026-11-18 against the running deployment: the dump held the 17 tables and the history of Flyway, and loaded into an empty MySQL 8.4 with every table and the users there. From `deploy/`, with the system running:

```
mkdir backup
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF opsflow > /tmp/opsflow.sql'
docker compose cp mysql:/tmp/opsflow.sql backup/opsflow.sql
docker compose exec mysql rm /tmp/opsflow.sql
docker compose cp backend:/data/images backup/images
```

- **Why through a file in the container.** In Windows PowerShell 5.1, `>` writes the output of a program as UTF-16, which MySQL cannot read back. The dump is written inside the container and copied out unchanged.
- **What it holds.** The schema `opsflow` with its rows and `flyway_schema_history`, read in one consistent snapshot while the system keeps working. Not the MySQL accounts, which `deploy/mysql/01-accounts.sh` makes from `.env`, and not `.env` itself: keep a copy of `.env` with the backup in a safe place, or the signing key and passwords are made again. The queues and the Redis counts are not kept: a mail waiting in the queue expires in 10 minutes anyway, and the login counts start again.
- **Restore** onto a new volume: `docker compose up -d`, which makes the accounts and an empty schema; then `docker compose cp backup/opsflow.sql mysql:/tmp/opsflow.sql` and `docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot opsflow < /tmp/opsflow.sql'`, which replaces the tables; then `docker compose cp backup/images/. backend:/data/images`, `docker compose exec -u root backend chown -R opsflow /data/images`, and `docker compose restart backend`.
- **The folder `backup/`** holds the data of real users. Git ignores `deploy/backup/`; copy it to a safe place outside the machine, because a backup on the same disk is lost with the disk.
