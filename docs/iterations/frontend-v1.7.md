# Frontend Iteration v1.7

## Purpose

This file records one iteration of the OpsFlow page design and frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.7 |
| Date | 2026-11-16 |
| Baseline | [frontend-v1.6.md](frontend-v1.6.md) |
| Type | The layout of the frontend code, before Phase 5, on the branch `phase-5-frontend` |
| Trigger | The owner's review of the file layout on 2026-11-16, which found `src/components` and the top of `src` holding three kinds of file |

## Summary

The code under `frontend/src` is sorted by what a file is, so that the pages of Phase 5 and 6 have a clear place for what they share. No page, text, or behavior changes.

## Changes

| File | Before | After |
| --- | --- | --- |
| `usePagedList.ts` | `src/components` | `src/hooks` |
| `useEmailCode.ts` | `src/pages` | `src/hooks` |
| `format.ts`, `format.test.ts`, `validation.ts`, `validation.test.ts`, `roles.ts` | `src` | `src/lib` |
| `imageFile.ts` | `src/components` | `src/lib` |
| `failure.ts` | `src/pages` | `src/lib` |
| `LoginPage`, `SignUpPage`, `ForgotPasswordPage`, `PendingPage`, with their tests | `src/pages` | `src/pages/account` |

Every relative import was rewritten to the new places. `text.ts` stays at the top of `src`, because every part of the application reads it, and `StatusPages.tsx` stays at the top of `src/pages`, because Not allowed and Page not found belong to no part.

## Decisions Made

| Topic | Decision | Rejected alternative |
| --- | --- | --- |
| Where a shared piece goes | `components` holds what is drawn, `hooks` the React hooks, and `lib` plain functions with no React | One `components` folder for all three: a hook or a check could not be found by its kind, and the folder grows with every page |
| The pages | One folder for each part of the application, the account pages included | The account pages at the top of `pages` beside the folders of the other parts |
| When | Before Phase 5, while no other work is open on the frontend | During Phase 5: the move would mix with the new pages in one change |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: the layout table and the list of tests in Running the Frontend Locally, and the Change Log |
| Code under `frontend/` | The files above moved and their imports rewritten. `npm test` on 2026-11-16: 206 tests in 15 files, all passed; `npm run lint` and `npm run build` passed |
| Figma file | Not changed: no page or text changed |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
