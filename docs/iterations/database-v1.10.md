# Database Iteration v1.10

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.10 |
| Date | 2026-11-03 |
| Baseline | v1.9, see [database-v1.9.md](database-v1.9.md) |
| Type | One retention rule. No table, column, constraint, or index changed |
| Table count | 16 (unchanged) |
| Trigger | The owner's request that an email code is kept for 10 minutes only |

## Summary

- **An email code is deleted as soon as it expires.** v1.9 kept an expired code for 24 hours, so a person could ask support "I never received the code". The owner does not want that: a code lives for its 10 minutes of validity and is then deleted.
- **The cleanup job runs once a minute, not once an hour.** An hourly job would keep a code for up to 70 minutes. With a one-minute interval a code is kept for 10 minutes plus at most one minute until the next run. The interval matches the archive job.
- **The statement** is `DELETE FROM email_verification_codes WHERE expires_at < NOW()` (in UTC). It still deletes by `expires_at` only and never touches a valid code.
- **No index on `expires_at`.** The owner decided not to add one. The table holds only the codes of the last 10 minutes, so a scan every minute costs nothing.

## Changes

| Area | Before (v1.9) | Now |
| --- | --- | --- |
| Keeping of an expired code | 24 hours after `expires_at` | None: deleted at the next run after `expires_at` |
| Job interval | Once an hour | Once a minute |
| Application configuration | The interval and the 24 hours | The interval only |
| Privilege | `DELETE` on `email_verification_codes` | Unchanged |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How long a code is kept | Its 10 minutes of validity, plus at most one minute | 24 hours after expiry (v1.9), which served a support question the owner does not need |
| Job interval | One minute | One hour, which would keep a code for up to 70 minutes |
| Index on `email_verification_codes(expires_at)` | Not added | Adding it to `V1__initial_schema.sql`, which the small table does not need |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Email codes (Cleanup), Operating Parameters, and the Change Log |
| [project-todo.md](../project-todo.md) | Updated: T-49 |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: a comment only |
| [Verification script](../../db/verification/run-db-verification.ps1) | Updated: the check of the cleanup now inserts a code that expired one minute ago, runs `expires_at < NOW(3)`, and is named `app can delete email codes that have expired, and a valid code stays`. **Run on 2026-11-03, MySQL 8.4: 156 passed, 0 failed**, including the renamed check. The count stays 156. Started by the assistant, with the root password typed by the owner in the masked dialog |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the entry of this check. The Latest run section shows the run of 2026-11-03 (156 passed) |
| [Initial migration](../../backend/src/main/resources/db/migration/V1__initial_schema.sql), [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1), Simplified ERD, Business Process Flowchart | Not changed: no table, column, or flow changed |

## Open Items

- Implement the email code cleanup (T-49), with the interval in application configuration.
