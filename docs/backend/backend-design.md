# OpsFlow Backend

## Purpose

This document describes the backend as it is built now: the stack, the layout of the project, how it is configured, and how it is run. It is the current-state document of the backend area. The database it connects to is described in [database-design.md](../database/database-design.md), and the pages it serves in [page-design.md](../frontend/page-design.md).

## Change Log

This document always describes the current backend. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-03 | 1.0 | The Spring Boot skeleton: Maven project in `backend/`, Flyway and the MySQL driver, configuration from environment variables, and the migrations moved into the project | [backend-v1.0.md](../iterations/backend-v1.0.md) |

## Stack

| Item | Choice |
| --- | --- |
| Language and runtime | Java 17 |
| Framework | Spring Boot 4.0.9 (Spring Initializr no longer offers 3.5.x) |
| Build | Maven, through the wrapper `backend/mvnw` |
| Web | `spring-boot-starter-webmvc` |
| Validation | `spring-boot-starter-validation` |
| Database access | `spring-boot-starter-jdbc` only. The choice of a persistence layer (plain JDBC, JPA, or MyBatis) is open: see T-51 in [project-todo.md](../project-todo.md) |
| Migrations | Flyway (`spring-boot-starter-flyway` and `flyway-mysql`) |
| Driver | `mysql-connector-j` |

Not added yet: security, JPA or MyBatis, Redis, RabbitMQ, mail, and tests beyond the generated one.

## Layout

| Path | Content |
| --- | --- |
| `backend/pom.xml` | The build and the dependencies |
| `backend/src/main/java/com/opsflow` | The code. The root package is `com.opsflow` |
| `backend/src/main/resources/application.yml` | The configuration, with no secrets |
| `backend/src/main/resources/db/migration` | The Flyway migrations, applied in version order. Never edit one that has been applied: add a new one |

## Configuration

The application connects to MySQL as `opsflow_app`, and Flyway connects as `opsflow_migrator` (see [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md)). The four values come from environment variables and have no default, so a missing one stops the application from starting instead of falling back to a value in the repository:

| Variable | Used for |
| --- | --- |
| `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` | The application connection |
| `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` | Flyway |

The JDBC URL sets `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, so every session runs in UTC (see Time zone in the database design). A local file `application-local.yml` is ignored by git.

## Running

From `backend/`, with the four variables set in the shell:

```
mvn spring-boot:run
```

The first start makes Flyway apply `V1__initial_schema.sql` to `opsflow`. After it, run [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) once as `root` (T-02).
