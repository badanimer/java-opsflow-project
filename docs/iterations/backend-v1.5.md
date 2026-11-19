# Backend Iteration v1.5

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.5 |
| Date | 2026-11-13, extended the same day when sign-up and password recovery were built, and again with the console command that creates an administrator |
| Baseline | [backend-v1.4.md](backend-v1.4.md) |
| Type | Implementation: the first module, `auth`, and the backend part of Phase 2 of the [roadmap](../implementation-roadmap.md) |
| Trigger | The owner asked to build login, after deciding the login steps and the handling of a disabled account ([database-v1.11.md](database-v1.11.md)). Then the owner chose the mail service and the limits on code requests and asked for sign-up and recovery to be built |

## Summary

- Login is built: `POST /auth/login` checks the password outside any transaction, and a short transaction locks the user row, confirms that the checked hash is still the current one, and records the result (T-20).
- Every request is checked against the database: the user, the roles, the status, and the time the password last changed (T-20).
- The lockout after wrong passwords is built, with its `LOGIN_LOCKED` log row (T-28).
- The seed migration `V2` adds the four roles and the SYSTEM identity (T-05).
- Security refuses any request that no rule allows.
- Email codes are built (T-29): `POST /auth/email-codes` stores a code as a keyed hash and mails it after the commit. A sign-up code for an address that has an account is refused with `EMAIL_TAKEN`. A recovery request gets the same answer whether or not a mail is sent.
- Requests for a code are limited per client address and for the whole system, counted in MySQL in the new table of the migration `V3` (the code-request part of Q-07).
- Sign-up and the password reset are built (T-29): `POST /auth/sign-up` and `POST /auth/password-reset`, with the password rule and the username rule. A sign-up logs the new user in and returns the token. The reset locks the user row, which is the other side of the login race.
- Mail is built: `Mailer` sends by SMTP, in the background, and never inside a transaction. The mail service is Gmail with an app password (Q-12).
- The first scheduled job is built: the cleanup of expired codes and old request counts (T-49).
- The console command `create-admin` is built (T-24), the first task of Phase 3: it creates an administrator on the server, with the password typed hidden at a terminal.
- The suite passes: 222 tests. Login added 49 to the 85 before it, sign-up and recovery added 78, and the console command added 10.

## Changes

| Area | Change |
| --- | --- |
| Dependencies | `spring-boot-starter-security-oauth2-resource-server` and its test starter |
| Migration | `V2__seed_roles_and_system_identity.sql`: the roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`, and the SYSTEM identity. Rows only |
| `auth` module | `AuthController`, `LoginService`, `LoginRecorder`, `LoginLockPolicy`, `PasswordHasher`, `TokenService`, `TokenAuthenticator`, `SecurityConfig`, `SecurityErrorHandler`, `AuthRepository`. See [Authentication](../backend/backend-design.md#authentication) |
| `common.audit` | `AuditAction` (the one table of audit actions, each with its category), `AuditCategory`, and `AuditLogWriter`, which writes `operation_logs` and refuses to run outside a transaction |
| `common.security` | `CurrentUser`, the user of the request for the controllers |
| Configuration | `OpsflowProperties` gains `login` (the lock steps, the count that locks until midnight, the reset zone) and `token` (12 hours). The signing key comes from the new environment variable `OPSFLOW_TOKEN_SIGNING_KEY` |
| Errors of the security filters | `UNAUTHENTICATED`, `ACCOUNT_DISABLED`, and `FORBIDDEN` are written by the exception handler of the controllers, so they carry `code` and `requestId` like every other error. This closes the open item of [api-v1.2.md](api-v1.2.md) |
| Test support | `MySqlIntegrationTest` puts the seed rows back after it empties the tables, gives the application a signing key made for the run, and replaces the clock with `MutableClock`. `ApiConventionsTest` lets every request through, because it tests the conventions and not the security rules |
| Tests | New: `LoginLockPolicyTest` (5), `TokenServiceTest` (4), `PasswordHasherTest` (3), `LoginTest` (12), `TokenCheckTest` (11), `LoginRaceTest` (7), `AuditLogTest` (4), `SeedMigrationTest` (3). Changed: `TestInfrastructureTest` (two tests renamed, because a clean start now has the four seeded roles), `TransactionRetryMySqlTest` (reads only its own two roles) |
| Dependencies (second part) | `spring-boot-starter-mail` |
| Migration (second part) | `V3__email_code_request_counts.sql`: the table of the counts of code requests. The privilege script grants `INSERT`, `UPDATE`, and `DELETE` on it |
| `auth` module (second part) | New: `EmailCodeService`, `EmailCodeStore`, `EmailCodeHasher`, `EmailCodeRepository`, `EmailCodePurpose`, `EmailCodeCleanupJob`, `AccountService`, `AccountWriter`, `AccountRules`. Changed: `AuthController` (three endpoints), `AuthRepository` (the account by email, the insert of a user, the reset, and `last_login_at` in the account), `SecurityConfig` (the three endpoints are open to everyone) |
| `common.mail` | New: `Mailer`, `MailTransport`, `MailConfig`, `OutgoingMail`. See [Mail](../backend/backend-design.md#mail) |
| `common.config` | New: `JobsConfig`, which turns the scheduled jobs on unless `opsflow.jobs.enabled` is false. `OpsflowProperties` gains `emailCode`, `mail`, and `jobs` |
| `common.error` | `ErrorCode` gains `CODE_INVALID`, `USERNAME_TAKEN`, and `EMAIL_TAKEN` |
| Configuration (second part) | The properties `opsflow.email-code.*`, `opsflow.jobs.enabled`, and `opsflow.mail.*`, the last from the optional environment variables `OPSFLOW_MAIL_HOST`, `OPSFLOW_MAIL_PORT`, `OPSFLOW_MAIL_USERNAME`, `OPSFLOW_MAIL_PASSWORD`, and `OPSFLOW_MAIL_FROM` |
| Test support (second part) | New: `Mailbox`, the mail server of the tests. `MySqlIntegrationTest` uses it, offers `sentMail()`, empties it before each test, and turns the scheduled jobs off. `AuthIntegrationSupport` gains the calls for the three endpoints and reads the code from the mail; its `resetPassword` now runs the real transaction of the reset, so `LoginRaceTest` races the real one |
| Tests (second part) | New: `EmailCodeTest` (11), `SignUpTest` (12), `PasswordResetTest` (11), `AccountRaceTest` (2), `EmailCodeCleanupJobTest` (6), `EmailCodeHasherTest` (6), `AccountRulesTest` (25 runs), `MailerTest` (5). Changed: `TestInfrastructureTest` (17 tables, and the two tables with `DELETE`), `SeedMigrationTest` (`V2` follows `V1`, and later migrations may follow), `PageQueryFactoryTest` (the new groups of `OpsflowProperties`) |
| Verification script | It applies `V3` after `V1`, expects 17 tables, and has five new checks for the new table and its privileges |
| Console command | New: `CreateAdminCommand`, `AdminCreator`, `Prompt`, and `PasswordConfig`. `OpsflowBackendApplication.main` runs the command when the first argument is `create-admin`. `AuthRepository` gains the lock on a role row and the grant of a role. `SecurityConfig` and `SecurityErrorHandler` exist only in the web application, and the password encoder moved from `SecurityConfig` to `PasswordConfig`. New test: `CreateAdminCommandTest` (10) |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The signing algorithm | HMAC-SHA256 with one secret key from the environment | A key pair: nothing but this application reads the token, so there is nobody to give a public key to |
| The issue time in the token | The standard `iat` in seconds, and `iat_ms` in milliseconds for the comparison with `password_changed_at` | Seconds alone: a login in the same second as a reset could not be told from one before it, so it would either be refused wrongly or keep a token it should lose |
| The clock that reads the expiry | The `Clock` bean of the application | The system clock inside the token library: the token would then be issued on one clock and checked on another, and a test could not let 12 hours pass |
| How the result of the login is recorded | `LoginRecorder` returns an outcome, and `LoginService` throws the refusal after the commit | Throwing inside the transaction: the rollback would undo the count of the failed attempt |
| The count of failures | Calculated in Java under the row lock and written in one `UPDATE` | `failed_login_count = failed_login_count + 1` in SQL: the lazy reset at midnight needs the old value and the time of the last failure anyway, and the row is locked |
| An unknown username | A password check against a decoy hash before the general error | Answering at once, which is measurably faster than for a real user and so shows which usernames exist |
| A request that no rule allows | Refused, also when the path does not exist | Letting unknown paths through to get a `404`: an endpoint added without a rule would then be open |
| The first rules | `POST /auth/login` for everyone, `GET /auth/me` for any logged-in user, and nothing else | Waiting for the permission module: the token check needs an endpoint to be tested through, and the pending page needs the roles |
| The password of a login request | At most 24 characters, the limit of the password rule; a longer one is refused as a validation failure and not hashed | No limit, which lets a client make the server hash very long texts |
| The audit actions in the code | All 25 actions of the design, in one enum | Only `LOGIN_LOCKED` for now: the table is short, and a test can then compare the categories with the database |
| How the tests keep the seed rows | Empty every table, then run the seed migration file again | Skipping the seeded tables when emptying: a test that adds a role would leave it for the next test |
| How the race with a reset is tested | The real password check is wrapped, and the reset is committed right after it has answered | Two threads and a sleep: the order would depend on timing and the test could pass by luck |
| The cost of BCrypt in tests | The tests store hashes of the lowest cost, which the application reads from the hash. The application itself hashes at the default cost | A test profile with another encoder, which would make the test application differ from the real one |
| How mail leaves the application | Plain SMTP through `spring-boot-starter-mail`, built from `opsflow.mail.*` | The API of one mail provider: every provider offers SMTP, so the choice of a provider stays configuration. Spring Boot's own `spring.mail.*` set-up: an empty host would still make a sender that fails at the first mail |
| When a mail is sent | After the commit, on one worker thread of the mailer | In the request thread: the answer for a registered address would take a second longer than for an unregistered one, which shows which addresses are registered, and a slow mail server would hold requests |
| A mail that fails | Logged without address or text, and not reported | An error to the client: the answer would again differ between an address that gets mail and one that does not |
| No mail server configured | The application starts, sends nothing, and logs a warning. The code is never logged | Refusing to start: the tests and a developer without a mail account could not run it. Logging the code for convenience: a code in a log is a secret in a log |
| The mailer in the tests | The real `Mailer` with a `Mailbox` behind it, and `flush` to wait for the worker | Replacing the whole mailer: the rule that a mail is sent after the commit would not be tested |
| The transactions of a code request | One transaction that throws its refusal, so a refused request rolls back | Returning an outcome as the login does: here nothing has to stay after a refusal |
| The transaction of a wrong entry | A short transaction of its own, before the refusal is thrown | Counting inside the transaction of the sign-up: the rollback would undo the count |
| Scheduled jobs in the tests | Off by `opsflow.jobs.enabled`; a test calls the job | Letting the schedule run: a job would delete rows in the middle of another test, at a time no test controls |
| How the cleanup deletes | A plain read of the ids, then one delete for each id | One `DELETE ... WHERE expires_at < ?`: without an index on `expires_at` it locks every row and gap it scans, and can deadlock with a request that inserts a code |
| The helper `resetPassword` of the tests | It makes a code row and calls the real transaction of the reset | A second HTTP call: inside `LoginRaceTest` it would go through the password hasher that the test has wrapped |
| How the console command starts | The same application, with no web server and the jobs off, chosen by the first argument of `main` | A second main class or a second jar: two things to build and to keep in step. A Spring profile: a profile can be set by mistake in the environment of the server |
| Where the password of the administrator comes from | Typed twice at the terminal, hidden. Without a terminal the command refuses to run | An argument or an environment variable: it would stay in the shell history or in the configuration. Reading it from a pipe: the password would have been written into a command or a file first |
| The security classes without a web server | `SecurityConfig` and `SecurityErrorHandler` are parts of the web application only | Leaving them unconditional: the application then does not start without the web server, which the first run of `CreateAdminCommandTest` showed |
| The lock of the console command | `FOR UPDATE` on the `ADMIN` row in `roles`, before the user is inserted | No lock: Administrator protection (T-23) will count the holders of `ADMIN` under this lock, and a creation must wait for it like any other change of who holds the role |
| Where the command lives | In `auth`, next to sign-up, whose rules it uses | A user module that does not exist yet. It can move when that module is built |

## Verification

- `mvnw test` on 2026-11-13, with Docker Desktop running and the database, signing key, and mail environment variables unset: **222 passed, 0 failed**. The run after login alone had 134, and the run before the owner's three changes of the same day (the message for a registered address, the username rule, and the login by sign-up) had 208.
- The race test of the login was checked against a broken version: with the comparison of the hash removed from `LoginRecorder`, `aLoginThatCheckedTheOldPasswordIsRefusedWhenAResetCommitsFirst` fails (the login answers 200) and `aWrongPasswordIsNotCountedWhenAResetCommitsFirst` fails. The comparison was put back.
- Two rules of the reset and the codes were checked the same way. With `password_changed_at` set to the plain time of the reset, `aTokenIssuedInTheSameMillisecondAsTheResetIsRefused` fails. With `consumed_at IS NULL` removed from the conditional update, no test failed at first: the unique keys and the hash comparison still refused the second use. `AccountRaceTest` was written for the case they do not cover, a newer code requested while the password is hashed, and both of its tests fail without the condition. Both rules were put back.
- The log of the run shows three deadlocks on the insert into `email_code_request_counts`, in the two tests where several threads make the first request of a window at the same moment. The retry handled each, and no request was answered `BUSY`.
- Sending through Gmail works. On 2026-11-13, after the owner stored the app password at a masked prompt, the backend was started with the mail variables and a sign-up code was requested for the mail account itself. The request answered `204`, the mailer logged no failure, and the owner confirmed that the mail "Your OpsFlow verification code" arrived in the inbox with a 6-digit code. A whole sign-up with a mailed code was not done by hand; the tests cover it with the mailbox of the tests. No test sends a mail.
- The console command: its tests run it in an application started without the web server. The first run of those tests failed, because `SecurityErrorHandler` needs the web layer; it and `SecurityConfig` were then limited to the web application, and the whole suite passed again, so the security rules of the web application are unchanged. The jar was built, and `create-admin` with its input taken from a file refused to run with exit code 1 and the message that it needs a terminal. The owner then ran it in a terminal that had been opened before `OPSFLOW_TOKEN_SIGNING_KEY` was set: the application did not start, and the command printed the stack trace of the framework. It now reports a failed start in one sentence that names the missing variable, with a test. The owner ran it again in a terminal with the variables, and it created the first administrator of the local machine: one `ACTIVE` user that holds `ADMIN`, with a BCrypt hash, and a `USER_REGISTERED` and a `ROLE_GRANTED` row with no operator and the remark `console`, read back from `opsflow` afterwards.
- The application was started against the local database on 2026-11-13, after the signing key was made and stored without being shown. Flyway validated three migrations and applied `V2` and `V3` (`opsflow` went from version 1 to 3). Checked through HTTP: a request without a token and a wrong login answer `UNAUTHENTICATED` with a `requestId`; a request for an email code answered `INTERNAL_ERROR` before the grant, as expected, and after the owner typed the root password and the grant for `email_code_request_counts` was run, `204`, then `TOO_MANY_REQUESTS` within 60 seconds, and `EMAIL_TAKEN` for the address of the SYSTEM identity. No mail was sent in that check: the application was started without the mail variables. It was stopped afterwards.
- The verification script of the database was run on 2026-11-13 after `V3` and the new grant were added: **161 passed, 0 failed** (156 before). The owner typed the root password in the masked dialog.

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Stack, Layout, Shared Code, No slow work inside a transaction, Scheduled Jobs (renamed from "Scheduled Jobs (decided, not built)"), Rules Checked By Tests, Tests, Continuous integration, Authentication, the new Mail section, Configuration, Running, and the Change Log |
| [Database design document](../database/database-design.md) | Updated in [database-v1.11.md](database-v1.11.md): the seed migration, the token rule, the decoy check, and then the email codes, their limits, the new table, and the steps of sign-up and the reset |
| [API design document](../api/api-design.md) | Updated in [api-v1.3.md](api-v1.3.md): the Authentication section with five endpoints, and the result codes |
| [environment-setup.md](../environment-setup.md) | Updated: `OPSFLOW_TOKEN_SIGNING_KEY` and how to make it, the later migrations with the grant after `V3`, the mail variables, and how to set up the mail account |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the latest run, the test infrastructure, the shared code, the section on login, sign-up, and password recovery with the races, the new checks of the script, and What is not tested yet |
| [project-todo.md](../project-todo.md) | Updated: T-05, T-20, T-28, T-29, and T-49 are done and removed; Q-12 is decided and removed; Q-07 keeps what is left of it; T-22, T-24, T-27, T-36, and T-50 say what is already built; T-24 is done and removed. T-58 is new. T-59 (the first administrator of the local machine) was added and done in this iteration. T-56 (the signing key, the two migrations, and the grant on the local machine) and T-57 (the mail account and a first real mail) were added and done in this iteration. The links to Scheduled Jobs follow its new name |
| [implementation-roadmap.md](../implementation-roadmap.md) | Updated: Phase 2 |
| [README.md](../../README.md) | Updated: Project Log |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: the grant for `email_code_request_counts`. Login needs `SELECT` and `UPDATE` on `users` and `INSERT` on `operation_logs`, which the account had |
| [Verification script](../../db/verification/run-db-verification.ps1) | Updated: `V3`, the count of 17 tables, and five new checks |
| [future-ideas.md](../future-ideas.md) | Updated with the owner's approval: a human check before a code is mailed |
| `.github/workflows/backend-tests.yml` | Not changed: the tests need no new variable and send no mail |
| Physical ERD, Simplified ERD, Business Process Flowchart | See [database-v1.11.md](database-v1.11.md) for the two ERDs, which get the new table. The flowchart is not changed: no order or inventory flow changed |

## Open Items

- Done on 2026-11-13: the signing key is set on this machine, `V2` and `V3` are applied to `opsflow`, and the grant for `email_code_request_counts` is run.
- Done on 2026-11-13: the mail account is set up on this machine, and a real mail arrived (T-57).
- `ACCOUNT_NO_ROLE` on protected endpoints (T-22), and the rules of the Permission Matrix with the test over every endpoint (T-27).
- The other rules of the SYSTEM identity (T-36) and the other account log rows (T-50).
- The sign-up page and Forgot password in the frontend (T-58).
- Done on 2026-11-13: the console command was run at a real terminal and made the first administrator of the local machine (T-59).
- Still open in Q-07: a limit on logins per client address, and the client address behind a reverse proxy.
- Read the result of the continuous integration job after the push (T-55).
