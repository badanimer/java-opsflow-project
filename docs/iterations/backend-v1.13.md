# Backend Iteration v1.13

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.13 |
| Date | 2026-11-18 |
| Baseline | [backend-v1.12.md](backend-v1.12.md) |
| Type | The local deployment, the health check, and the counters of the retry |
| Trigger | Q-32. The owner decided on 2026-11-18 on a local Docker Compose, with one instance, a health check, and the counters. Work on the branch `phase-8-backend` |

## Summary

- **One command starts the whole system**: `deploy/compose.yaml` with MySQL, the migrations, the rights of the application account, Redis, RabbitMQ, the backend, and nginx, in that order, each waiting for the one before it. Only nginx has a port, on `127.0.0.1:8000`.
- **Secrets made at random**: `deploy/new-env.ps1` writes `.env` with the passwords and the signing key, prints none, and refuses to replace one that exists.
- **The client address comes from nginx only**, so the login counts of Q-07 work behind the proxy and a client cannot choose the address that is counted.
- **Actuator on a management port of its own**, 8081, which nginx does not reach: the health check of the database, Redis, and RabbitMQ, and two counters, the retries after a lock conflict and the `BUSY` answers.
- **Checked by running it** on 2026-11-18: every service healthy, the pages and a link to a page served, a forged `X-Forwarded-For` ignored, the health check up, the API port refusing Actuator, the two mail queues there, the rights of the application account narrowed, and a second start running cleanly.
- 4 new tests. The last full run: 452 passed, 0 failed.

## Changes

| Area | Change |
| --- | --- |
| `deploy/` | New: `compose.yaml`, `new-env.ps1`, `.env.example`, `mysql/01-accounts.sh` (the two accounts when the volume is new), and `privileges.sh` (the privilege script on every start, after the database-wide rights are given again, because the script revokes them first) |
| `backend/` | New `Dockerfile` (built with Maven, run on the JRE as a user that is not root) and `.dockerignore` |
| `common.retry` | `TransactionRetry` counts each retry (`opsflow.retry.retries`) and each `BUSY` answer (`opsflow.retry.busy`) |
| `auth` | `SecurityConfig` lets a request through without a token on the management port only |
| Configuration | `spring-boot-starter-actuator`; `management.server.port` from `OPSFLOW_MANAGEMENT_PORT` (8081), with the endpoints `health` and `metrics` and the probes. The deployment sets `SERVER_FORWARD_HEADERS_STRATEGY=native` and turns Flyway off in the backend |
| Tests | New `ActuatorTest` (3): the health check and the counters on the management port, and the refusal on the port of the API. `TransactionRetryTest` counts the retries and the `BUSY` answers |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where it runs | Docker Compose on this machine (the owner's choice, 2026-11-18) | A server on the internet now: it needs a domain, HTTPS, and backups, and costs money each month. The same file runs there later |
| Who runs the migrations | A Flyway container as the migration account, before the backend | The backend, as in development: it would hold the migration password, which can change the schema |
| The rights of the application account | The privilege script on every start, after the migrations | Once by hand, as on the development machine: a new table would wait for a person to remember the grant |
| The port of Actuator | A port of its own that nginx does not reach, open without a token there | The port of the API behind a permission: a health check of Docker has no token, and a mistake would open the counters to the internet |
| The client address | Taken from `X-Forwarded-For` only in the deployment, only from a private address, and set by nginx | Trusting the header everywhere: on the development machine anyone could send it |
| How many instances | One | Several: they need a lock for the scheduled jobs and the logins sent between them through Redis |
| How Flyway's MariaDB driver logs in | It may ask the server for its public key, on the private network of the containers | TLS, which this driver did not take up with `sslMode=trust` in the test of 2026-11-18 |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated: Change Log, Stack, Layout, Tests, Configuration, Running, and Deployment (new) |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.11.md](frontend-v1.11.md): the image of the frontend |
| [environment-setup.md](../environment-setup.md) | Updated: Docker runs the deployment too, and the port 8000 |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the new tests and the latest run |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: Q-32 narrowed to more than one instance and a server on the internet |
| [README.md](../../README.md) | Updated: Project Log, and how to run the system |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the flows did not change |

## Open Items

| Item | Tracked in |
| --- | --- |
| More than one instance, and a server on the internet with a domain and HTTPS | Q-32 |
| Two orders that lock the same items in opposite order (D6) against the real code | T-17 |
