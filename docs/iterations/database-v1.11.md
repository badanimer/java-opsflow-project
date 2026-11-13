# Database Iteration v1.11

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.11 |
| Date | 2026-11-12, extended on 2026-11-13 when login was built, and again the same day when sign-up and password recovery were built |
| Baseline | v1.10, see [database-v1.10.md](database-v1.10.md) |
| Type | Two rules of the login flow, decided before Phase 2 (accounts and login) starts, the seed migration `V2`, which adds rows only, and the rules of the email codes with one new table, `email_code_request_counts` (migration `V3`). No existing table, column, constraint, or index changed |
| Table count | 17 (was 16): `email_code_request_counts` is new |
| Trigger | The owner's two decisions before T-05: how a login and a password reset at the same moment are handled, and what a disabled account gets. Then, when sign-up and recovery were built, the owner's decisions on Q-12 (the mail service) and on the code-request part of Q-07 |

## Summary

- **A login confirms the hash it checked.** The password check stays outside every transaction, because it is slow. A reset could therefore commit between the check and the moment the result is recorded, and a person could be logged in with a password that had just been replaced. The short transaction that records the result now locks the user row with `FOR UPDATE` and compares `password_hash` with the hash that was checked. If it differs, the login is refused and nothing is recorded.
- **The login steps are written down**, with the order of the decisions under the lock: a changed hash or a soft-deleted account, a lock set in the meantime, a wrong password, a disabled account, and a success.
- **A disabled account gets `ACCOUNT_DISABLED` and no token.** At login, only when the password is right. After login, on the next request. It no longer passes the pending page, which is now only for a user with no role.
- **The seed migration `V2` exists** (T-05): the roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`, and the SYSTEM identity, whose `password_hash` holds the marker `!SYSTEM-CANNOT-LOG-IN`, which is not a hash.
- **A token is accepted only when it was issued after `password_changed_at`**, to the millisecond. Found while building: "issued before" left a gap when a reset and a login fall in the same millisecond, and the standard issue time of a token is in whole seconds.
- **Requests for an email code are limited** (the code-request part of Q-07): 10 accepted requests of one client address in an hour, and 200 of the whole system in a day, counted in the new table `email_code_request_counts`. Before, only one address was limited (once in 60 seconds), so one client could request codes for many addresses and use the mail service for spam.
- **Sign-up says when an address already has an account, and recovery does not.** A request for a sign-up code for a registered address is refused with `EMAIL_TAKEN`. A request for a recovery code is answered the same way whether or not a mail is sent: a recovery code is mailed only to an account that can be recovered, and otherwise a row is stored with a marker that no code matches, so the limits and a wrong entry behave the same.
- **A sign-up logs the new user in.** The account is created with `last_login_at` set, and a token is returned, so the user goes straight to the pending page.
- **A code is stored as a keyed hash** (HMAC-SHA256 with the token signing key), because a plain hash of 6 digits can be reversed by trying the million codes.
- **The steps of sign-up and of the password reset are written down**, in the pattern of the login steps: the cheap checks, then the code, then the slow hashing outside any transaction, then one short transaction that uses the code up.
- **`password_changed_at` never falls behind a token.** A reset sets it to at least `last_login_at`, and later than its previous value, so a login and a reset in the same millisecond still end the old token.
- **A username** is 3 to 64 letters and digits, with no space and no other character.
- **The mail service is chosen** (Q-12): SMTP, with Gmail and an app password, from a Gmail account made for OpsFlow.
- **The codes of the token check are stated**: `UNAUTHENTICATED` for a user who does not exist or is soft-deleted and for a token older than `password_changed_at`, and `ACCOUNT_DISABLED` for a disabled account.

## Changes

| Area | Before (v1.10) | Now |
| --- | --- | --- |
| Recording a login result | A short transaction after the password check. Nothing said what happens when the row changed in between | The transaction locks the user row, reads the hash, the status, and the lock fields again, and records a result only when the hash is the one that was checked |
| A reset that commits between the check and the record | Not covered. A login with the old password could succeed and get a token issued after the reset | Refused with the general login error, no token, nothing recorded |
| Password reset | Sets the hash and `password_changed_at`, clears the lock fields | The same, stated as one transaction that locks the user row |
| A disabled account at login | The login succeeded, and the pending page said that the account is disabled | `ACCOUNT_DISABLED` (403) with no token when the password is right. The general login error when it is wrong, and the failure counts |
| A disabled account after login | Refused by the token check, with no code named here | `ACCOUNT_DISABLED` (403) on the next request |
| Pending page | Two messages: disabled, and no role | One message: no role |
| Seed data | Planned (T-05). `roles` was empty | `V2__seed_roles_and_system_identity.sql` inserts the four roles and the SYSTEM identity |
| Token and `password_changed_at` | A token issued before `password_changed_at` is refused | A token not issued after it is refused. The token carries its issue time in milliseconds, and a login in the same millisecond as the reset before it gets the next millisecond as its issue time |
| An unknown username at login | The general login error | The same, after a password check against a decoy hash, so the time of the answer does not show whether the username exists |
| Concurrency by Module, Authentication | Four races | Six: added the login against a password reset, and the login against disabling or deleting the account. The row of two wrong passwords now names the row lock |
| Tables | 16 | 17: `email_code_request_counts` (`id`, `scope`, `window_start`, `request_count`), with one row for a scope and a window, a count above zero, and an index on `window_start`. No foreign key, no email address, no code |
| Limits on requests for a code | Once in 60 seconds for one address and purpose. Nothing per client and nothing for the whole system (Q-07) | The same, and 10 accepted requests of one client address in an hour, and 200 of the whole system in a day (the Sydney day). All three are refused with `TOO_MANY_REQUESTS`. A refused request is not counted |
| A request for a code for an address that cannot use it | Recovery: the same response. Sign-up: the address had to be free, and the answer was not stated | Sign-up: `EMAIL_TAKEN` (409), and nothing is mailed, stored, or counted. Recovery: `204`, no mail, and a row whose `code_hash` is the marker `!NOT-SENT` |
| How a code is stored | "Stored hashed" | An HMAC-SHA256 of the purpose, the address, and the code, with the token signing key |
| Two requests for one address at the same moment | Both could pass the 60-second check and both send a code | Every request updates the count row of the whole system, so they run one at a time: one code, and the others are refused |
| Sign-up | Request a code, enter it, the account is created. The user then logged in | Four steps, with `CODE_INVALID`, `USERNAME_TAKEN`, and `EMAIL_TAKEN`. A taken username leaves the code unused. The account is created with `last_login_at` set, and the sign-up returns a login token, so the user is on the pending page at once |
| Recovery | The reset sets the hash and `password_changed_at` and clears the lock, in one transaction that locks the user row | The same, with the steps before it, the comparison of the hash under the lock, and the rule for `password_changed_at` |
| Username | Only its length (64) and that case does not matter | 3 to 64 letters and digits, with no space and no other character |
| Cleanup job | Deletes expired codes | Also deletes the request counts whose window started more than two days ago, at most 1000 rows of each table in one run |
| First administrator | The exact form of the command was left to the backend | `java -jar opsflow-backend.jar create-admin`. It refuses to run without a real terminal, and its two log rows carry the remark `console` |
| Application privileges | `DELETE` on `email_verification_codes` only | `INSERT`, `UPDATE`, and `DELETE` on `email_code_request_counts` as well |
| Concurrency by Module, Authentication (second extension) | Six races | Ten: the rows of a code submitted twice, of a new code requested while an older one is used, and of two sign-ups are rewritten, and added are requests at the same moment near a limit, a reset against another reset or the disabling of the account, and the cleanup job. The lock order of authentication is stated in the principles |
| Operating Parameters | No limit on code requests | The two limits, and the cleanup batch |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the password is checked | Outside the transaction, as before, with a confirmation under the row lock afterwards | Checking inside the transaction while the row is locked: BCrypt takes tens of milliseconds, and a connection and a row lock would be held all that time |
| What is compared under the lock | `password_hash`, with the hash that was checked | `password_changed_at`: it would work, but the hash is exactly what the check relied on |
| What a changed hash means | The check is void: the general login error, no token, and nothing recorded, whether the old password matched or not | Counting it as a failure: the reset has just cleared the count, and the person may have typed the new password. Checking the password again against the new hash: a second slow check and a loop, for a case that the person solves by logging in again |
| Where a disabled account learns it | On the login page, with no token | After a successful login, on the pending page (v1.4): a disabled account would hold a token that every endpoint refuses |
| When the login says `ACCOUNT_DISABLED` | Only when the password is right | For any attempt: anyone who knows a username could then learn that the account is disabled |
| A wrong password on a disabled account | Counted, like on any account | Not counted: the answer would then differ from that of a usable account |
| The session after an account is enabled again | The user logs in again, because the frontend dropped the token. The server does not revoke the old token; a token that somebody kept works until its 12 hours end, and a password reset ends it | A column such as `users.status_changed_at` compared with the issue time of the token, which needs a migration. It can be added if the owner wants the old token refused by the server |
| The edge of the token rule | Refuse a token issued at or before `password_changed_at`, and never issue one at or before it | "Issued before": a reset that commits in the same millisecond as the login before it would leave that token valid. Whole seconds only: a login within a second after a reset would be refused |
| The password marker of the SYSTEM identity | A fixed text that is not a hash and has no algorithm prefix | A real hash of a random password, which could in principle be matched |
| Where the limits on code requests are counted | In MySQL, in a table of counts | Redis, which fits counting with an expiry but would be a new service to run and to test for a few dozen codes a day. It can replace the table when Redis is brought in for idempotency (Q-07) |
| The shape of the count | One row for a scope and a fixed window, with `INSERT ... ON DUPLICATE KEY UPDATE` | One row for each request and a count over the last hour: a count-then-insert can be passed by two requests at once unless something is locked, and the rows would hold client addresses of each request |
| The windows | The clock hour for a client address, and the Sydney day for the whole system | A sliding window: more exact at the edge of a window, and not worth a second row or a second read here |
| What is counted | Every accepted request, also one that sends no mail | Only the mails sent: the limit would then be reached later for unregistered addresses, and the answer would show which addresses are registered |
| When the limit of the day is reached | Nobody gets a code until midnight | No such limit: with many client addresses the mail account could be used for spam and be blocked. A human check is recorded as a later idea |
| A sign-up code for an address that has an account | `EMAIL_TAKEN`, and the page says that the address is registered. The owner's decision on 2026-11-13, after the first version hid it | The same `204` and no mail, as recovery does: a stranger cannot test which addresses have accounts, but a person who forgot an old account waits for a mail that never comes. The cost of the choice: such a test is possible, and it is not counted against the limits |
| A recovery code for an address that cannot use it | The same answer, no mail, and a row that no code matches | Telling the person that the address is not registered: Forgot password would then show which addresses have accounts, and the owner changed only sign-up |
| After a sign-up | The new user is logged in: the sign-up returns a token, and the user goes straight to the pending page. The owner's decision on 2026-11-13 | No token, and a login as the next step: one more form to fill in for a person who has just proved the address and typed the password |
| How that first token stays safe | Its issue time is taken in the transaction that creates the account and stored as `last_login_at`, so a later reset is never earlier than it | Issuing it with a later time of its own: a reset in between could then be earlier than the token, and the token would survive it |
| How a code is stored | HMAC-SHA256 with a key outside the database | BCrypt: it only slows down the million tries, and it would bring the rules for slow work into the code check. A plain SHA-256: reversed at once |
| The key of that HMAC | The token signing key, with a first line in the signed text that no token can have | A second secret in the environment: one more value to set and to lose |
| The order of the checks in sign-up and reset | The values, the code, and only then BCrypt | Hashing first: every request with a wrong code would cost a hash |
| A refusal inside the sign-up transaction | Thrown, so the transaction rolls back and the code stays unused | Using the code up first: a person whose username is taken would have to wait 60 seconds for a new code |
| A new password equal to the current one | `VALIDATION_FAILED` on the field, after a right code, and the code stays unused | `CODE_INVALID`: the person would not know what to change |
| A reset whose account or hash changed under the lock | `CODE_INVALID` and nothing written | Checking again against the new hash: the other reset has used the code, so there is nothing to continue with |
| The username rule | 3 to 64 letters and digits, with no space and no other character. The owner's decision on 2026-11-13 | Dots, underscores, and hyphens as well (the first version of this iteration). Any text up to 64 characters: spaces and look-alike characters make two names hard to tell apart |
| The mail service (Q-12) | SMTP, with Gmail and an app password, from a Gmail account made for OpsFlow | A mail API of one provider, which ties the code to it. A provider for transactional mail now: it needs a domain of one's own. The owner's personal mailbox: a limit or a block on it would touch private mail |
| A soft-deleted account | Answered like an unknown user: `UNAUTHENTICATED` at login and in the token check | `ACCOUNT_DISABLED`: a deleted account cannot be enabled again by contacting an administrator |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Users without roles, Login security and account recovery (Login steps, Disabled accounts, Recovery, Token checks), Concurrency by Module, and the Change Log |
| [api-design.md](../api/api-design.md) | Updated in [api-v1.3.md](api-v1.3.md): the meaning of `UNAUTHENTICATED` and `ACCOUNT_DISABLED`, the rules of the login endpoint, and then the three endpoints of the email codes, sign-up, and the reset with the codes `CODE_INVALID`, `USERNAME_TAKEN`, and `EMAIL_TAKEN` |
| [page-design.md](../frontend/page-design.md) | Updated in [frontend-v1.1.md](frontend-v1.1.md): the login page, the pending page, and what the sign-up page and Forgot password show for each answer |
| [backend-design.md](../backend/backend-design.md) | Updated in [backend-v1.5.md](backend-v1.5.md) |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the five new checks of the script in Blocks A, B, and C with the counts, the Application tests section with the new classes and races, and What is not tested yet |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: T-20, T-22, and Phase 2; then T-29, T-49, and Q-12 removed as done or decided, Q-07 narrowed to what is left, T-56 and T-57 done and removed, and T-58 added |
| [README.md](../../README.md) | Updated: Project Log |
| [Verification script](../../db/verification/run-db-verification.ps1) | Updated and run on 2026-11-13: it applies `V3` after `V1`, expects 17 tables, and has five new checks (`V3` applies; a count that is not positive is rejected; one row counts a window; a second row for the same window is rejected; the application account can count and can delete old counts). **161 passed, 0 failed** (156 before). `V2` is not applied by the script: it only adds rows, and the checks make their own. The fixtures were not changed: the order and inventory flows did not change, and the race on the count row is tested in the application tests |
| [Seed migration](../../backend/src/main/resources/db/migration/V2__seed_roles_and_system_identity.sql) | Added. It was applied to `opsflow` on 2026-11-13. No grant follows it, because it adds no table |
| Application tests | The login steps, the token check, the email codes, sign-up, the password reset, the cleanup job, and their races are tested against the MySQL container: 222 tests passed on 2026-11-13. See [backend-v1.5.md](backend-v1.5.md) and the test log |
| [Migration `V3`](../../backend/src/main/resources/db/migration/V3__email_code_request_counts.sql) | Added: the table `email_code_request_counts`. It was applied to `opsflow` on 2026-11-13, and its grant was then run as `root` |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: `INSERT`, `UPDATE`, and `DELETE` on `email_code_request_counts` |
| [future-ideas.md](../future-ideas.md) | Updated with the owner's approval: a human check before a code is mailed |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: the grant after `V3`, the table of privileges, the mail variables, and how to set up the mail account |
| [Initial migration](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) | Not changed: a new table goes into a new migration |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated on 2026-11-13 through the Figma connector: the table `email_code_request_counts` added to the right of `email_verification_codes`, with `id` (`bigint`, PK), `scope` (`varchar`, NOT NULL), `window_start` (`datetime`, NOT NULL), both in `UK(scope, window_start)`, and `request_count` (`int`, NOT NULL, above 0). It has no connector, because it has no foreign key. The note of `email_verification_codes.code_hash` now says that it holds a keyed hash or the marker `!NOT-SENT` |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated on 2026-11-13 through the Figma connector: the table `email_code_request_counts` added to the right of `email_verification_codes`, with its four fields and no connector |
| Business Process Flowchart | Not changed: it shows the order and inventory flow, not the login |

## Open Items

- Done on 2026-11-13 in [backend-v1.5.md](backend-v1.5.md): the login steps with the race test against a password reset, the answers for a disabled and a soft-deleted account, the email codes with their limits, sign-up, the password reset with the user row locked, and the cleanup job.
- Done on 2026-11-13: `V2` and `V3` are applied to the local database, and the grant for `email_code_request_counts` is run.
- Done on 2026-11-13: the mail account is set up, and a real mail arrived.
- The pending page for a user with no role only, and the handling of `ACCOUNT_DISABLED` in the frontend (T-22). The sign-up page and Forgot password (T-58).
- Still open in Q-07: a limit on logins per client address, and how the client address is read behind a reverse proxy.
