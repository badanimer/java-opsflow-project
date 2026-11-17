# Database Iteration v1.18

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.18 |
| Date | 2026-11-18 |
| Baseline | [database-v1.17.md](database-v1.17.md) |
| Type | The rules of the login lockout. No table, column, constraint, privilege, or migration changed |
| Trigger | Q-07. The design said that anyone who knows a username can lock that account. The owner chose on 2026-11-18 to count by client address, with a ceiling for the account |

## Summary

- **The steps lock one address out of one account.** The failures of an account from one client address are counted in Redis: 5 lock that address out for a minute, 10 for 5 minutes, 15 for 15 minutes, 20 until midnight. A stranger who types wrong passwords for mary locks only their own address, and mary logs in from hers.
- **`users.failed_login_count` is the failures of the day from every address**, with a ceiling of 100 that locks the account itself until midnight, for every address. It stops guessing from many addresses. A success no longer clears it; a password reset does.
- **One address is refused for the rest of a 15-minute window after 50 failures on any username**, with `TOO_MANY_REQUESTS`, before the username is read.
- **Without Redis** the login goes on with the ceiling only.

## Changes

| Section | Change |
| --- | --- |
| Login security and account recovery | The three counts, the scenario of the stranger and the owner, what a success and a reset clear, Redis down, and the client address behind a proxy. The note that the lock was per account only, and that the limit per address belonged with Q-07, is replaced |
| Login steps | Step 1 refuses an address over its limit first and an address locked out of the account; the table of step 3 says what is counted in the row and what in Redis after the commit, and that a success clears only the count of its address |
| Email codes, the reset | After the commit, the counts and locks of every address are cleared |
| User and account logs | A `LOGIN_LOCKED` row names the address with `lockedAddress` when one address is locked out, and the remark says which lock it is |
| Concurrency by Module, Authentication | Two wrong passwords at once: the Redis script counts each once and only one reaches a step, and a late short lock never shortens a longer one. New row: two failures at the ceiling at once |
| Operating Parameters | The lock steps of one address, the 100 failures of an account in a day, and the 50 failures of an address in 15 minutes |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How a stranger is kept from locking an account | Steps by account and address, and a ceiling of 100 a day for the account (the owner's choice, 2026-11-18) | A limit per address in front of the old lock: an address that tries slowly still locks the owner out until midnight |
| What clears the count of the day | A password reset only | A successful login too: guessing from many addresses would start again after each login of the owner |
| The ceiling | 100 failures in a day | Lower: the owner and a stranger together reach it sooner, and it locks everybody out |
| The limit of an address | 50 failures in 15 minutes, on any username | Lower: an office behind one address where a few people mistype would be refused |
| Redis down | The login goes on with the ceiling only | Refusing every login |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design](../database/database-design.md) | Updated: the sections above and the Change Log |
| Migrations, ERDs, privileges, Business Process Flowchart | Not changed: no table, column, or grant changed, and the diagrams do not show the login |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the order and inventory flows did not change |
| Backend | Built in [backend-v1.12.md](backend-v1.12.md) |
| API | [api-v1.10.md](api-v1.10.md) |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
