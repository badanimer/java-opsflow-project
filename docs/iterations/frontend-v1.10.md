# Frontend Iteration v1.10

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.10 |
| Date | 2026-11-18 |
| Baseline | [frontend-v1.9.md](frontend-v1.9.md) |
| Type | Small changes that follow the login lockout by client address (Q-07, [api-v1.10.md](api-v1.10.md)) |
| Trigger | Phase 8. The login can now answer `TOO_MANY_REQUESTS`, and a `LOGIN_LOCKED` row of the audit page can name the address that was locked out |

## Summary

- The login page shows `TOO_MANY_REQUESTS` as the general "Too many requests" message, as it already did through the shared handling of failures; a test now covers it.
- The expanded view of an audit row labels the new field `lockedAddress` as "Locked for address".
- 2 new tests. The latest run: 292 tests in 25 files, all passed, and the lint clean.
- The summary of a sign-in lock row is its remark, which the backend now writes as one of two fixed texts: a lock of one address, or of the whole account. The Figma rows show the first.

## Changes

| Area | Change |
| --- | --- |
| `text.ts` | `audit.fields.lockedAddress` |
| Tests | `LoginPage.test.tsx`: the message for an address with too many failures. New `pages/audit/auditRows.test.ts`: the target, the summary, and the fields of a sign-in lock row |
| Figma | Page "Tickets and Audit", frame "D · Audit log": the summary of the three sign-in lock rows reads "Locked for one address after failed logins from it". The frames show no expanded sign-in lock row, so the new label has no place there |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The message for an address with too many failures | The general "Too many requests" of the other limits | A message of its own: it would tell a stranger which limit stopped them |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated: Change Log, Running the Frontend Locally (the tests and the latest run) |
| Figma | Updated: the three summaries above |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
