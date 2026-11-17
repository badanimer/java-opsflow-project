# Backend Iteration v1.12

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.12 |
| Date | 2026-11-18 |
| Baseline | [backend-v1.11.md](backend-v1.11.md) |
| Type | Redis and RabbitMQ are brought into the backend (Q-07), with the login rules of [database-v1.18.md](database-v1.18.md) |
| Trigger | Phase 8. The owner decided Q-07 on 2026-11-18: Redis for the login counts of each client address, and RabbitMQ for the mail. Work on the branch `phase-8-backend` |

## Summary

- **The login counts by client address, in Redis.** `LoginLimits` keeps the failures of one account from one address and the lock of that address out of the account, so a stranger who types wrong passwords for mary locks out only their own address. It also counts the failures of one address on any username, and refuses that address for the rest of a 15-minute window after 50. The user row keeps the failures of the day from every address, with a ceiling of 100 that locks the account itself until midnight.
- **The mail goes through RabbitMQ.** `Mailer` still returns at once; its worker puts the mail in the durable queue `opsflow.mail`, and a listener sends it with three tries. A mail the server still refuses, or one that waited past its 10 minutes, is kept for a day in `opsflow.mail.dead`. A mail handed over before a restart is sent after it.
- **Neither service is needed to work.** Without Redis the login goes on with the ceiling only; without RabbitMQ the mail is sent at once, as before.
- **The tests start a Redis and a RabbitMQ container** beside MySQL, so every mail of every integration test goes through the real queue.
- 11 new tests. The last full run: 448 passed, 0 failed. The run before it had one failure, described under Changes.

## Changes

| Area | Change |
| --- | --- |
| `auth` | New `LoginLimits`: the three counts in Redis, each change one Lua script, the days, windows, and lock ends from the `Clock` bean, and no exception when Redis cannot be reached. `LoginLockPolicy` gives the steps of an address, the ceiling of the account, the day, and the window. `LoginService` refuses an address over its limit first, refuses a locked address, and counts after the commit; `LoginRecorder` counts the failures of the day and logs both kinds of lock; `AuthRepository.recordSuccess` no longer clears the count of the day; `AccountService.resetPassword` clears the counts of every address |
| `common.mail` | New `MailQueue` and `RabbitMailQueue`: the durable queue with a dead-letter queue, persistent messages that expire after `opsflow.mail.queue-ttl`, the confirm of the broker, and one listener. `Mailer` puts the mail in the queue from its worker, sends it at once when the queue cannot be reached, and gives a mail from the queue three tries |
| Configuration | `spring.data.redis.*` from `OPSFLOW_REDIS_*`, `spring.rabbitmq.*` from `OPSFLOW_RABBITMQ_*`, `opsflow.login.account-failures-per-day`, `address-failures`, and `address-window`, and `opsflow.mail.queue-ttl` |
| Dependencies | `spring-boot-starter-data-redis`, `spring-boot-starter-amqp`, and for the tests `testcontainers-rabbitmq` |
| Test support | `MySqlIntegrationTest` starts a Redis 8.0 and a RabbitMQ 4.1 container, connects the application to both, and empties Redis before each test. `Mailbox` can refuse every mail. `AuthIntegrationSupport` logs in from a chosen address and reads the counts in Redis |
| Tests | `LoginLimitsTest` and `MailQueueTest` are new; `LoginTest`, `LoginRaceTest`, `LoginLockPolicyTest`, `PasswordResetTest`, and `MailerTest` follow the new rules. The first full run had one failure, `UtcSourceRuleTest`: the throttle of the Redis warning read `System.currentTimeMillis()`, which the rule forbids in `src/main`; it now reads `System.nanoTime()`, which measures elapsed time only |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| What stops a stranger from locking an account | The steps count one account from one address, in Redis, and the account itself is locked only at a ceiling of 100 a day (the owner's choice, 2026-11-18) | A limit per address only, in front of the old lock of the account: one address that tries slowly still reaches 20 failures in a day and locks the owner out until midnight |
| Where the counts of the addresses live | Redis: many small counters that expire, written on every failure, which cost nothing when lost | The user row or a new table: a row per account and address, and a cleanup job for it |
| Whether a success clears the count of the day | No: only a reset clears it, so the ceiling holds against guessing from many addresses between two logins of the owner | Yes, as before: an attacker with many addresses would get 99 more tries after each login of the owner |
| Redis down | The login goes on without the counts of the addresses, with a warning at most once a minute | Refusing every login: everybody locked out to stop a stranger |
| The clock of the counts | The `Clock` bean, with the day or the window kept in the key's hash; the expiry in Redis only clears a key | The expiry of Redis as the rule: the tests could not move it, and it would follow a second clock |
| Idempotency keys | Stay in MySQL, in the transaction of the order | Redis: a key could be stored while the order rolls back |
| How a mail reaches the queue | The request hands it to the local worker as before, and the worker publishes it | Publishing in the request: the answer would take longer when a mail is sent, and would show whether an address is registered |
| RabbitMQ down | The worker sends the mail at once, as before the queue | Dropping the mail, or waiting for the broker |
| The retries of a mail | Three tries in the listener, 2 and 4 seconds apart, then the dead letters | Retrying for ever: a mail server that refuses one address would hold every later mail |
| How long a mail waits | 10 minutes, the life of its code, then the dead letters, kept a day | No expiry: a code mailed an hour late is of no use, and stays on the disk of the broker |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated: Change Log, Stack, Shared Code, Tests, Continuous integration, Authentication, Mail, and Configuration |
| [Database design](../database/database-design.md) | Updated in [database-v1.18.md](database-v1.18.md): the login rules. No table changed |
| [API design](../api/api-design.md) | Updated in [api-v1.10.md](api-v1.10.md): the meanings of `LOGIN_LOCKED` and `TOO_MANY_REQUESTS` at login |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: the Redis and RabbitMQ variables, named `OPSFLOW_*` like the others (T-08), and what the backend uses each service for |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the login and mail tests, the two login races, and the latest run |
| [README.md](../../README.md) | Updated: Project Log |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the flows of the script did not change |

## Open Items

| Item | Tracked in |
| --- | --- |
| Two orders that lock the same items in opposite order (D6) against the real code | T-17 |
