# Frontend Iteration v1.6

## Purpose

This file records one iteration of the OpsFlow page design and frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.6 |
| Date | 2026-11-15 |
| Baseline | [frontend-v1.5.md](frontend-v1.5.md) |
| Type | The scope of the pages of Phase 4. This iteration stays open and records their design in Figma and their build, on the branch `phase-4-frontend` |
| Trigger | The owner's decisions of 2026-11-15 at the start of Phase 4 |

## Summary

- The stock page is built in two steps. Phase 4: the rows, the search, the paging, and adding, editing, and deleting an item. Phase 5: the quantity changes, the View buttons, and the logs.
- An item can be edited (its name or image) only while it is not on sale, and each row says whether it is.
- The product page of Phase 4 has no Order button; ordering comes in Phase 5.

## Changes

| Page | Before (v1.5) | Now |
| --- | --- | --- |
| Stock page | Built at once in Phase 5 | In two steps; Phase 4 builds what adding items needs |
| Stock page | No edit; open whether a row shows that the item is listed | Edit of the name and image while the item is not on sale; each row shows whether it is on sale |
| Open Details | Whether the stock page shows whether an item is listed | Settled: it does |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where a manager adds items in Phase 4 | The first part of the stock page (the owner's choice) | The whole stock page in Phase 5: Phase 4 could not be checked on screen, because nothing could be listed |
| The Order button | Phase 5, with the stock changes (the owner's choice) | Phase 4: the work would nearly double, and the order shares its locks with the stock changes |
| How a price is shown | In Australian dollars, `$12.50`, as the system runs on Sydney time (the owner's choice after the Figma review) | The number alone; yuan |
| The catalog of List item when it is empty | One state, "No items to list", with a hint to add items on the Stock page and Go to Stock for a person with `STOCK_VIEW` (the owner's choice after the Figma review) | Three states told apart by a second request to `GET /items`, as drawn: one more request and two more screens for little help |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: Product page (the price in Australian dollars), Stock page, Open Details, and the Change Log |
| [Implementation roadmap](../implementation-roadmap.md), [project-todo.md](../project-todo.md) | Phase 4 holds the first part of T-46; T-39 leaves the order dialog to T-10 |
| Figma file | The page "Stock and Products" drawn by Codex and reviewed on 2026-11-15 (about 130 frames, with handoff notes), and the shared components Tags (On sale, Not on sale), Filter bar (Name search), and Paged list (Cursor first, middle, last). The three empty states of the catalog are built as one, as decided above |
| Code under `frontend/` | Not changed |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- Build the stock page (first part), the product page, and their dialogs from the reviewed design, on the branch `phase-4-frontend` (T-39, T-42, T-46).
