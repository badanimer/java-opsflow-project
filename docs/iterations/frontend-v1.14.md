# Frontend Iteration v1.14

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.14 |
| Date | 2026-11-19 |
| Baseline | [frontend-v1.13.md](frontend-v1.13.md) |
| Type | A new action in the Tickets area of the audit page. Drawn in Figma and built |
| Trigger | The owner's request of 2026-11-19 ([database-v1.21.md](database-v1.21.md)) |

## Summary

- The Action filter of the Tickets area offers Ticket taken after Ticket created, apart from Ticket assigned.
- A take reads "Taken from the pool", with the person who took it in the User column; an assignment still reads "Assigned to olive", with the manager in the User column.

## Changes

| Area | Change |
| --- | --- |
| `api/audit.ts` | `TICKET_TAKEN` among the actions of `TICKET` |
| `pages/audit/auditRows.ts` | The summary of `TICKET_TAKEN` |
| `text.ts` | "Ticket taken" and "Taken from the pool" |
| Tests | `auditRows.test.ts`: a take and an assignment read differently. `AuditPages.test.tsx`: the Action filter offers Ticket taken apart from Ticket assigned and sends `action=TICKET_TAKEN` (2 tests) |
| Figma | "Audit / Tickets / Action menu": the option Ticket taken |
| Documentation | Page design: Change Log, Audit log page |

## Verification

| Check | Result |
| --- | --- |
| `npm test` | 302 passed in 25 files, 0 failed (2026-11-19); the run before had 300 |
| `npm run lint`, `tsc` | No problem |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated |
| [UI design in Figma](https://www.figma.com/design/68brMszRAYI4d261GKL1GU) | Updated: page "Tickets and Audit" |

## Open Items

None.
