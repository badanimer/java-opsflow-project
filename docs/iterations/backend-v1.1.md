# Backend Iteration v1.1

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.1 |
| Date | 2026-11-04 |
| Baseline | [backend-v1.0.md](backend-v1.0.md) |
| Type | Decisions: the persistence layer, transactions, and the package layout |
| Trigger | Before the first module is built, the owner settled how the code reads and writes the database (T-51) |

## Summary

- The persistence layer is plain JDBC through `JdbcTemplate`.
- Transactions are declared with `@Transactional`.
- The code is organized by feature, not by layer.
- Tests have three layers, and the integration tests run against a MySQL 8.4 container (Testcontainers).
- Passwords use BCrypt and the token is a signed JWT.
- Docker was found to work: the earlier note that the machine is a virtual machine and cannot run Docker was wrong, and `ENVIRONMENT_SETUP.md` was corrected. This made Testcontainers possible.
- The test infrastructure was built (T-53) and its first 7 tests pass (`mvnw test`: 7 run, 0 failed): the base class for integration tests, the race runner, and checks of the setup itself.
- No application code changed. The decisions are recorded in the backend design, and the build already holds the JDBC starter.

## Changes

| Area | Change |
| --- | --- |
| Backend design | The Stack row for database access, a new Transactions section, and a new package layout in Layout |
| Backend design | Also a Tests section and an Authentication section |
| Test code | New: `MySqlIntegrationTest`, `RaceRunner`, and `TestInfrastructureTest`; the Testcontainers MySQL dependency in `pom.xml`; the existing context test now runs against the container instead of the real `opsflow` database |
| Environment | `ENVIRONMENT_SETUP.md` corrected: this is a physical machine and Docker works |
| To-do list | T-51 removed; T-52 and T-53 added; Q-09 removed (decided); Q-32 narrowed; T-20 extended |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Persistence layer | `JdbcTemplate`. The flows need `SELECT ... FOR SHARE`, `FOR UPDATE`, conditional updates, and an explicit rollback before a retry, so the SQL that runs must be the SQL that is written, and the lock order must be readable in the code | JPA, whose caching and automatic writes hide the locking; MyBatis, which adds a mapping layer that this project does not need |
| Transactions | `@Transactional` on the methods of service beans | `TransactionTemplate` everywhere |
| Package layout | By feature: one package for each module | By layer |
| Integration tests | A real MySQL 8.4 in a Testcontainers container, with the migrations and the privilege script applied | An in-memory database, whose locking differs from MySQL's; a shared `opsflow_test` database on the local server, which needs cleaning between tests |
| MySQL, Redis, and RabbitMQ | Stay as native Windows services; Docker is used only for the temporary MySQL of the tests (Q-09) | Docker Compose for the services, which adds a layer for no present need |
| Password hashing | BCrypt. The password rule (8 to 24 characters) is far below its 72-byte limit, and no extra library is needed | Argon2, which adds a dependency for little gain here |
| Token | A signed JWT, since the design already invalidates old tokens through `password_changed_at` | A random string stored in a new table, which would change the database design |
| Where the frontend keeps the token | Left open until the frontend starts | |

### How `@Transactional` serves the retry rules

The annotation is applied through a proxy, so two shapes follow from it:

- A retry loop never lives in the method that carries `@Transactional`. A small component outside it calls the transactional method and retries on a deadlock or a lock wait, so each attempt is a new transaction and the earlier one is already rolled back. The attempts and the delays come from application configuration (T-11).
- A write that must happen after a rollback, such as the `order_attempts` rows of a refused order, is made in a method of another bean with `Propagation.REQUIRES_NEW`. A call from one method of a class to another method of the same class does not pass through the proxy, and the annotation would do nothing.

### Package layout

One package for each module under `com.opsflow`, such as `order`, `inventory`, `ticket`, and `user`, and a `common` package for the error handling, the configuration, and the time helpers. Inside a package the classes keep their layer in the name (`OrderController`, `OrderService`, `OrderRepository`). A module does not call the repository of another module: it calls its service. The write path of an order touches the stock and the product tables, and all of its SQL stays in the order module, so that the lock order can be read in one place.

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Stack, Layout, a Transactions section, and the Change Log |
| [README.md](../../README.md) | Updated: Project Log |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: the note on virtualization, the Docker rows, and section 2.6 |
| [project-todo.md](../project-todo.md) | Updated: T-51 removed; T-52 added; T-53 added and done; Q-09 removed (decided); Q-32 narrowed; T-20 and T-05 extended |
| Database design, migrations, verification | Not changed |

## Open Items

- Write the conventions of the API into code (T-52). This includes the retry component.
- Decide continuous integration and the deployment plan (Q-32).
