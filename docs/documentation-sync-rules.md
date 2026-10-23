# Documentation Sync Rules

## Purpose

This file defines how OpsFlow documentation is kept consistent as the project changes. It tells people and AI assistants which artifacts must be updated together, how iterations are recorded, and how to extend these rules when a new area joins the project.

Read this file before changing any design document, diagram, or iteration record.

## Iteration Logic

OpsFlow records design history in three layers:

1. **Current-state documents** describe how the system is designed now. Example: [database-design.md](database/database-design.md). They are edited in place and always reflect the latest design.
2. **Iteration files** in [`docs/iterations`](iterations/) summarize one change set: what changed, why, which decisions were made, and what is still open. One file per area and version. They are never rewritten after release, except to fix errors.
3. **Change logs** are short indexes that point to the iteration files. They live in `README.md` (Project Log) and at the top of each current-state document (Change Log). Entries are listed newest first.

To understand the project, read the current-state document first, then follow the Change Log to the iteration files for history.

## Sync Map

When an area in the left column changes, update everything in the right column in the same change.

| Area changed | Must also update |
| --- | --- |
| Database design: tables, fields, constraints, order or inventory flow | [database-design.md](database/database-design.md) and its Change Log; the [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1); the migrations in [`db/migration`](../db/migration/); a new iteration file; the Project Log in `README.md` |
| The schema, the application privileges, or the order and inventory flows change | Re-run [`db/verification/run-db-verification.ps1`](../db/verification/run-db-verification.ps1), record the result in the iteration file, and update the test log [`db/verification/concurrency-tests.md`](../db/verification/concurrency-tests.md) (the latest run and, if a check changed, its entry). Keep [`concurrency-fixtures.sql`](../db/verification/concurrency-fixtures.sql) and [`restrict-app-privileges.sql`](../db/admin/restrict-app-privileges.sql) in step with the flows in [database-design.md](database/database-design.md). |
| A verification check is added, changed, or removed, in [`run-db-verification.ps1`](../db/verification/run-db-verification.ps1) or in [`concurrency-fixtures.sql`](../db/verification/concurrency-fixtures.sql) | Update [`db/verification/concurrency-tests.md`](../db/verification/concurrency-tests.md): the block, the check name exactly as the script prints it, the scenario, how it is run, what must hold, and the design rule it supports; the counts under Latest run and the table of how the count grew; and What is not tested yet, when a race there gets a test. Re-run the script and record the result in the iteration file. A removed test needs its reason in the iteration file. |
| Database migration added or changed | The Field Specifications and Initial Constraints in [database-design.md](database/database-design.md), and the Physical ERD if tables or fields changed. Never edit a migration that has been applied; add a new one. |
| Database structure: tables added or removed, or relationships changed | The [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1), in addition to the row above |
| Business flow that changes what the diagrams show | The Business Process Flowchart linked from [database-design.md](database/database-design.md) |
| A new iteration file | The Change Log of the matching current-state document; the Project Log in `README.md`; every item in its Open Items must also be in [project-todo.md](project-todo.md) |
| A task is confirmed, finished, or newly found, or a question is decided | [project-todo.md](project-todo.md): add the task, remove it when done, or move a decided question from Potential to Confirmed. Record the decision in the iteration that makes it. |
| A file is renamed or moved | Every link that points to it. Search the repository for the old name. |
| A new area is introduced (see below) | This sync map and the Documentation Structure list in `README.md`, plus the new area's first iteration file and Project Log entry |

The Physical ERD and the other diagrams are Figma boards, so they are not tracked by git. If an assistant cannot edit one, it must say so and list the exact changes still needed in the board.

## Minor Changes

Minor changes do not need to be synchronized. A change is minor only when all of the following are true:

- It adds no new feature or module.
- It does not substantially change the structure or runtime logic of an existing feature or module. As a rough guide, the change adds and removes about 30 lines or fewer in total, across code and documents.
- It deletes no existing feature or module.

The line count is only a guide. A change that adds, removes, or alters behavior, such as a state transition rule, is not minor even when it is short.

Examples of minor changes:

- Fixing wording, typos, or formatting
- Adding or editing a note, remark, or description
- Fixing a small bug inside existing logic
- Renaming a variable or field without changing behavior

For a minor change, update only the artifact being edited. No other document, diagram, Change Log entry, or iteration file is required. If a change fails any condition above, treat it as significant and follow the Sync Map.

Changes to this file and to `CLAUDE.md` are process changes. They are not recorded in the Project Log. Changes to [project-todo.md](project-todo.md) are not recorded in the Project Log either, because it is a working list and not a design iteration.

## Iteration Files

- Location: `docs/iterations/`
- Name: `<area>-v<major>.<minor>.md`, for example `database-v1.1.md`
- Version rule: increase the minor number for changes within the current design, and the major number for a redesign.
- Required sections: Purpose, Iteration Info, Summary, Changes, Decisions Made, Affected Artifacts, Open Items. Use [database-v1.1.md](iterations/database-v1.1.md) as the template.
- Language: English.

## Adding a New Area

An area is a distinct part of the project with its own design or code, such as the backend framework, API contract, cache, message queue, or frontend. When one is introduced:

1. Create its current-state document under `docs/<area>/`, with a Change Log section.
2. Add a row to the Sync Map above that lists what must be updated when this area changes.
3. Add the document to the Documentation Structure list in `README.md`.
4. Record the first iteration in `docs/iterations/<area>-v<major>.<minor>.md` and add a Project Log entry.

## Checklist Before Finishing A Change

- The current-state document matches the change.
- Every artifact in the Sync Map row for this area is updated, or listed as not updated with a reason.
- A new iteration file exists, and the Change Log and Project Log point to it.
- [project-todo.md](project-todo.md) matches the change: finished tasks are removed, and new open items are added.
- Every new or changed write path states in [database-design.md](database/database-design.md) what it reads, how a concurrent request is stopped from breaking it, and where it falls in the lock order. See Concurrency by Module.
- The test log [`db/verification/concurrency-tests.md`](../db/verification/concurrency-tests.md) matches the script: every check has its entry, the counts are the counts of the latest run, and a race that now has a test is no longer listed as untested.
- No link points to a missing file.
- All documentation is written in English.
