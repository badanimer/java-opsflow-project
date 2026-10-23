# Database Iteration v1.6

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.6 |
| Date | 2026-10-23 |
| Baseline | v1.5, see [database-v1.5.md](database-v1.5.md) |
| Type | Design rules and schema changes (a new column on `users`, a removed column, a new column, and new checks and indexes on `tickets`) |
| Table count | 14 (unchanged) |
| Trigger | A second review of the ticket system after v1.5 was published. It found that automatic tickets had no creator although `tickets.created_by` is required, that a ticket could be assigned without being started, that a user's tickets were stuck when the user was disabled, and that several ticket rules were still open |

## Summary

Version 1.6 finishes the ticket design that v1.5 began.

- A built-in SYSTEM identity (`users.is_system`) stands for the program. It is the creator of automatic tickets, and it acts for the program on tickets.
- The ticket priority is removed. A new field, `tickets.source`, says whether a ticket came from the system or from a person.
- `OPEN` is a pool nobody holds. Taking or being assigned a ticket makes it `IN_PROGRESS` at once. An assignee can release a ticket back to the pool, and the tickets of a disabled or deleted assignee return to it by themselves.
- A cancelled ticket is set apart in its own area, and nobody can take or revive it.
- A resolved ticket is archived by a manager at any time, by the system 7 days after it was resolved, or by the system when 99 resolved tickets are waiting.
- Inventory links are written only when the ticket is created. A new problem is a new ticket, or a follow-up ticket when it relates to an earlier one.
- An `ORDER_EXCEPTION` ticket must name its order, and the program fills in the order and the problem when it opens an automatic ticket.
- An order that went wrong is stopped and its ticket opened in one transaction. The order shows as waiting to be handled while the ticket is unresolved.
- The indexes needed by the ticket lists, the order list, and the logs are created now.
- Every ticket list shows system tickets before manual tickets, in whichever time direction the user chooses, and a ticket is fixed after creation. The ticket actions have a first log format, and `tickets` has the indexes its screens and jobs need.

## Changes

### The SYSTEM identity

- A new column `users.is_system` (`BOOLEAN NOT NULL DEFAULT FALSE`) marks one built-in row, the SYSTEM identity, with the username `system` and the reserved address `system@opsflow.invalid`. A seed migration creates it with the roles (T-05, T-36).
- It is not a person: no role, never an eligible assignee, no login (refused before the password check), no editing, disabling, soft-deleting, or role grants, hidden from user lists and assignee pickers, and exempt from the ticket limits.
- It fills the user id where one is required: `tickets.created_by` for a ticket the program opens, `tickets.closed_by` for an automatic archive, and `ticket_assignment_history.changed_by` for a change the program makes. This fixes a conflict found in review: automatic tickets had no creator, but `tickets.created_by` is `NOT NULL`.
- Audit log rows of system actions keep `operator_id` empty, as before.

### Ticket source replaces priority

- The four-level `priority` is removed: the column, its `CHECK`, and its index. There are only two classes of ticket, opened by the system or opened by a person.
- A new column `tickets.source` (`VARCHAR(16) NOT NULL`, `SYSTEM` or `MANUAL`, no default) stores the class. It is set at creation and never changes.
- A `SYSTEM` ticket has the SYSTEM identity as creator. The two say the same thing on purpose, so a screen can show or filter by the class without a lookup. The database cannot check them against each other, so the service layer keeps them in step and the tests cover it (T-33).
- The class gives system tickets a higher place on every list (see Ticket list order below). Whether the screen also shows it in another way, such as a background colour, is decided later.
- An earlier draft in this iteration left the column out and told the class from the creator alone. The user asked for the field, so it was added.

### Ticket list order

- Every role that can view tickets can choose the order of a ticket list. The default is newest first, and the user can switch to oldest first. This closes Q-24.
- Whichever direction is chosen, tickets with `source` `SYSTEM` are always shown before tickets with `source` `MANUAL`: the oldest system ticket comes before the newest manual ticket, and the same holds in the other direction. Inside each group the chosen time order applies. It gives system tickets a visually higher priority without a priority field.
- The time that orders a list is the one the list is about (creation time for the pool and the work lists, archive time for the archive, cancellation time for the cancelled area). This is my default, because the owner spoke of "time" in general. The choice is a screen setting and is not stored.
- The indexes of Q-13 will have to lead with `status` and `source` to serve these lists. The existing ones do not, and that is left to the Q-13 discussion.

### Fields fixed after creation

- A ticket's `title`, `description`, `type`, `source`, `order_id`, `follows_ticket_id`, `created_by`, and its inventory links never change after creation. Only the fields set by the transitions change. A missed detail goes into a comment, and a ticket opened wrongly is cancelled and opened again. This closes Q-25.
- The service layer enforces it and the database does not, as for the status rules (T-33).

### The pool, and taking a ticket in one step

- `OPEN` is the pool. A ticket in `OPEN` is held by nobody and never has an assignee. Taking a ticket, or being assigned one by a manager or an administrator, makes it `IN_PROGRESS` at once, like accepting a quest.
- This replaces the v1.5 model, in which an `OPEN` ticket could have an assignee and a separate step started the work. There is no "assigned but not started" state, so no extra tickets can hide from the in-progress limit.
- The transitions are now: `OPEN` to `IN_PROGRESS` (take or assign), `IN_PROGRESS` to `OPEN` (release), `IN_PROGRESS` to `RESOLVED`, `RESOLVED` to `CLOSED`, and `OPEN` or `IN_PROGRESS` to `CANCELLED`. A ticket is never reopened.
- An `OPERATOR` moves a ticket forward only when it is assigned to them, and can now only move it to `RESOLVED`.

### Release, and an assignee who stops being usable

- **Release.** The assignee can give up their own `IN_PROGRESS` ticket, and a manager or an administrator can remove the assignee. Both need a non-blank reason. The ticket returns to `OPEN`, the assignee is cleared, and a `ticket_assignment_history` row records it. A released ticket is still a live problem waiting in the pool, so releasing is not cancelling.
- **An assignee whose account is disabled or soft-deleted** loses their `IN_PROGRESS` tickets to the pool automatically, in the same transaction, with a history row whose `changed_by` is the SYSTEM identity. `RESOLVED` tickets keep their assignee, and re-enabling the user does not give the tickets back. This closes the ticket part of Q-16.
- Reassigning an `IN_PROGRESS` ticket stays for managers and administrators only, and now applies only to a ticket in progress. Assigning a ticket from the pool is the other case.

### Limits of 99 (changes from v1.5)

- The two limits stay: 99 `OPEN` tickets a person created, and 99 `IN_PROGRESS` tickets assigned to a person.
- The in-progress limit no longer mentions starting a ticket, because taking makes it `IN_PROGRESS` at once. The SYSTEM identity is exempt from both.
- The count is still made under an exclusive lock on that person's user row.

### The cancelled area

- A `CANCELLED` ticket leaves the pool and every work list. It appears only in its own cancelled area, which every role that can view tickets can open, like the archive.
- Nobody can take, assign, comment on, or bring it back. It is kept and not deleted, because the logs, the follow-up links, and the history refer to it. The user asked for the ticket to be removed or set apart, and setting it apart keeps the trace.
- The permission matrix view row now includes the cancelled area.

### Archive

- A `RESOLVED` ticket is archived in three ways: by a manager or an administrator at any time, singly or in a batch; by the system 7 days after `resolved_at`; and by the system when 99 resolved tickets are waiting in the whole system, in which case it archives all of the `RESOLVED` tickets. This closes Q-22.
- The delay of 7 days, the count of 99, and the interval of the job (1 minute) are operating parameters.
- The system archives ticket by ticket through the status gate, with the SYSTEM identity as `closed_by`. The count of 99 is read before the job starts and need not be exact.
- A separate approval module for managers and administrators is a later idea, kept in [future-ideas.md](../future-ideas.md). Nothing here depends on it.

### Inventory links only at creation

- Links in `ticket_inventory` are written only when the ticket is created, in the same transaction, so one ticket can cover a batch of products from the start. They are never added later. This closes the first part of Q-23.
- A new problem is a new ticket. If it is unrelated, for example a pen is also short while A4 paper was the first ticket, it is a plain new ticket that does not name the pen. If it relates to an earlier ticket, it is a follow-up ticket.
- The log action for adding a link later is removed. The links are part of the creation row.

### Orders on exception tickets

- An `ORDER_EXCEPTION` ticket must name its order. A `CHECK`, `chk_tickets_order_required`, enforces it, because it only needs the same row. This closes the second part of Q-23. A ticket of another type may name an order too.
- Both automatic cases arise while an order is being processed, so the program traces the problem when it opens the ticket. It fills `order_id`, writes the `title` and `description` itself, and links the inventory records involved. An inconsistency is an `INVENTORY_EXCEPTION` that records, for each product, the quantity the order expects and the quantity the stock shows. Exhausted retries are an `ORDER_EXCEPTION` that records what was being done, the error, and the number of attempts.

### Ticket logs and indexes

- The ticket actions have a first log format in the design document: `TICKET_CREATED`, `TICKET_ASSIGNED`, `TICKET_REASSIGNED`, `TICKET_RELEASED`, `TICKET_RESOLVED`, `TICKET_CLOSED`, and `TICKET_CANCELLED`, with `target_type` `TICKET`. The details may change when the audit screen is designed (Q-03).
- The ticket indexes are described under Indexes below.

### The exception path stops the order

- When an order goes wrong and the cause needs a person (retries exhausted, or the stock and order records disagree), the program stops the order and opens the ticket in one transaction. The first statement is the status gate on the order, `CREATED` to `CANCELLED`, with `EXCEPTION_TICKET:` and the ticket number in `status_reason`. If the gate finds the order already handled, nothing is written and no ticket is opened. This closes Q-26 with plan A.
- The order is final, so nobody can act on it again, and a second failure cannot open a second ticket. The order shows as waiting to be handled while its ticket is `OPEN` or `IN_PROGRESS`, and as cancelled afterwards. This is derived from `tickets.order_id` and the ticket status, so there is no new order status. A new order needs a new idempotency key.
- The reserved stock is released through the normal cancel path. **When the records disagree the stock is left as found**, and the person who handles the ticket corrects it with a manual stock change that names the ticket. This is my addition to the owner's plan, and it can be changed.
- `FAILED` is not used, and a transient error still never sets it. If the exception transaction cannot commit, nothing is recorded, the order stays `CREATED`, and the timeout job cancels it later.
- The design document has a new Exception path section under the order flows, and a row in Concurrency by Module. The lock order is kept: the order row first, then a brand-new ticket row that nobody else can hold.

### Indexes

- The owner asked for the indexes that are needed to be created now (closes Q-13): an extra index changes little, and a later one is a new migration.
- Tickets: `idx_tickets_created_by` became `idx_tickets_created_status (created_by, status)` for the open count. `idx_tickets_order` became `idx_tickets_order_status (order_id, status)` for the "waiting to be handled" lookup. `idx_tickets_status_resolved (status, resolved_at)` serves the archive job and the count of 99. `idx_tickets_status_closed` was replaced by three indexes that lead with `status` and `source`, `idx_tickets_status_source_created`, `idx_tickets_status_source_closed`, and `idx_tickets_status_source_cancelled`, because every ticket list shows system tickets first. `idx_tickets_status_priority` was removed with the priority.
- Added `idx_orders_status_created (status, created_at)` for the order list and `idx_operation_logs_created (created_at)` for the latest logs.
- An index for a screen that is not designed yet is added by a migration when the screen is designed.

### Concurrency by Module

- Rows were added or changed for: taking a ticket from the pool in one conditional update, a person at 98 in progress or 98 open, a user disabled while their tickets are released or resolved, and the archive job meeting a manager's archive or a cancel. Principles and lock order are unchanged: `users`, `roles`, `tickets`, `orders`, `inventory`.

### Documentation

- The Tickets section of the design document was rewritten and reordered. It also fixes paragraphs that had slipped under the Archive heading in v1.5.
- v1.5 is left as it was published.
- A new test log, [`db/verification/concurrency-tests.md`](../../db/verification/concurrency-tests.md), lists every structure, privilege, and concurrency test by block, with the scenario, what must hold, and the design rule, and it lists the races that are not tested yet. The sync rules now say when it must be updated.

### Migration

`V1__initial_schema.sql` was edited in place, because it has not been applied anywhere (T-01 is still open). `users` gets `is_system`. `tickets` loses `priority`, gets `source`, and gets `chk_tickets_source` and `chk_tickets_order_required`. Its indexes changed as listed above.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Creator of automatic tickets | A built-in SYSTEM user, `users.is_system`, fixed by a seed migration | Making `tickets.created_by` nullable, which leaves a ticket with no creator and does not help `ticket_assignment_history.changed_by`, which is also required; a SYSTEM user known only by its name, which cannot be told from a real account |
| Ticket class (priority) | No priority. A stored `source` column, `SYSTEM` or `MANUAL`, set at creation and never changed, next to the SYSTEM creator | The four-level priority; the class told from the creator alone, which was drafted first and dropped because the user wanted a field. The service layer keeps `source` and the creator in step |
| Taking a ticket | One step. Taking or being assigned makes the ticket `IN_PROGRESS` at once, and `OPEN` never has an assignee | A separate "assigned" step and "started" step, which needed a second action and let assigned but unstarted tickets escape the limit |
| Giving up a ticket | Release: the assignee, or a manager or an administrator, returns an `IN_PROGRESS` ticket to `OPEN`, with a reason | No way for an assignee to give up, which forces a request to a manager; cancelling instead, which would withdraw a problem that is still live |
| Assignee account becomes unusable | The `IN_PROGRESS` tickets return to `OPEN` automatically, in the same transaction, by the SYSTEM identity | Leaving them stuck until a manager reassigns them; reassigning them automatically to someone, which picks a person nobody asked for |
| Cancelled tickets | Kept, and shown only in their own area. Nobody can take or revive them | Physically deleting them, which would break the logs, links, and history that refer to them |
| When a resolved ticket is archived | A manager or an administrator at any time, the system after 7 days, or the system when 99 resolved tickets are waiting, ticket by ticket through the status gate | Manual archive only, which leaves resolved tickets for ever; one transaction for the whole job, which fails all of them for one conflict |
| What the 99 trigger archives | All `RESOLVED` tickets in the system when the count reaches 99. This is my reading of "archive when 99 are waiting" | Archiving only the oldest until the count falls below 99, which keeps newly resolved tickets visible longer |
| Links on a ticket | Written only at creation, in the same transaction. A new problem is a new ticket, or a follow-up | Adding links later, which would let one ticket grow into several problems and breaks the "one ticket, one problem" idea |
| `ORDER_EXCEPTION` and its order | The order is required, enforced by a `CHECK` on the same row | A service-layer rule only, which direct SQL would bypass. The rule for `INVENTORY_EXCEPTION` stays in the service layer, because it needs another table |
| Automatic tickets trace themselves | The program fills in the order, the problem, and the inventory records when it opens the ticket | Leaving the text to a person, who may not know which order failed |
| Order of a ticket list | The user chooses the direction, newest first by default, and system tickets always come before manual tickets inside either direction. No extra field | A single time order, which lets a new manual ticket sit above an old system ticket; a stored priority, which was removed |
| Editing a ticket after creation | Not allowed. A missed detail goes into a comment, and a wrong ticket is cancelled and opened again | Letting the creator edit while the ticket is `OPEN`, which needs an edit history or loses what was first written |
| Audit formats of the ticket actions | A first version in the design document, with `TICKET_*` actions and the reason in `remark` | Leaving them open until the audit screen is designed |
| Indexes | Created for the queries known now: the ticket lists lead with `status` and `source`, the open count, the order lookup, and the archive job each have one, and `orders(status, created_at)` and `operation_logs(created_at)` were added. An index for a screen that is not designed yet is added later by a migration | Keeping the old ticket indexes, which cannot serve the system-first lists; waiting for every screen before indexing, which the owner declined because an extra index changes little |
| An order that went wrong | Plan A: the program stops the order through the status gate (`CANCELLED`, with the ticket number in the reason) and opens the ticket in the same transaction, and releases the stock. The order shows as waiting to be handled while the ticket is unresolved. The stock is left as found when the records disagree. This last part is my addition, because releasing numbers that cannot be trusted could make the disagreement worse | Plan B, a new "on hold" order status that keeps the stock reserved until the ticket is resolved, which needs a second set of rules for the reservation clock; leaving the order `CREATED`, which keeps the stock reserved, lets an operator act on the order again, and can open a second ticket |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: the whole Tickets section, The SYSTEM identity, Ticket logs, Exceptions, the Permission Matrix, Concurrency by Module, Operating Parameters, Initial Constraints, and the Change Log |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | Updated in place (`users.is_system`; on `tickets` the removal of `priority`, the column `source`, `chk_tickets_source`, `chk_tickets_order_required`, and the new indexes); not yet applied anywhere |
| [Verification script](../../db/verification/run-db-verification.ps1) | Updated: the ticket inserts carry `source` and no priority, and there are checks for `users.is_system`, the absence of `tickets.priority`, the three ticket indexes, the allowed sources, a missing source, an `ORDER_EXCEPTION` without an order, with an order, and another type without an order, and the new index names. The fixtures have a new procedure, `open_exception_ticket`, with three race tests: two exception paths for one order at once, a confirmation racing the exception path, and the stock left as found when the records disagree. **Re-run on 2026-10-23 against the final schema (MySQL 8.4): 107 checks passed, 0 failed**, including the order and inventory races and the three exception path races. No race checks exist yet for the ticket rules, because they need the application code (T-17, T-34) |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Not affected: `tickets` is already writable and `ticket_inventory` is insert-only |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated: `users` gets `is_system`; `tickets` loses `priority`, gets `source`, and the notes on `created_by` and `assignee_id` were changed |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated: `users` gets `is_system`; `tickets` loses `priority` and gets `source` |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn) | Updated: the old Exception Handling section is shortened, because the system opens an exception ticket into the pool and the later steps are in the two ticket sections. Ticket Creation and Ticket Handling show the pool, taking in one step, release, the return of a disabled or deleted assignee's tickets, archiving (manager, 7 days, or 99 waiting), the cancelled area, and follow-ups instead of reopening. The exception section now stops the order as it opens the ticket, and the order shows as waiting to be handled |
| [project-todo.md](../project-todo.md) | Updated: T-05, T-11, T-14, T-33, and T-35 changed, T-36 added, Q-16 and Q-03 narrowed, Q-13, Q-22, Q-23, Q-24, Q-25, and Q-26 closed |

## Open Items

- Create the SYSTEM identity in a seed migration, and enforce its rules (T-05, T-36).
- Implement the ticket status rules in the service layer, including `OPEN` without an assignee and `IN_PROGRESS` with one, release, the automatic return when an assignee account stops being usable, the follow-up rule, the cancelled area, the archive screen, and the archive job with its 7-day and 99 triggers (T-33). The database does not enforce them, so this task must not be skipped.
- Implement taking, assigning, reassigning, and the two limits (T-35).
- Implement ticket creation: links written only with the ticket, an order on an `ORDER_EXCEPTION` ticket, and the program filling in the order and the problem on automatic tickets (T-14). Implement the exception path with the order flows: the order stopped by the status gate, the ticket opened, and the stock released in one transaction, except when the records disagree (T-10, T-14).
- Keep `tickets.source` in step with the SYSTEM creator in the service layer, and cover it with a test (T-33).
- Implement the ticket list order on the screen: the chosen direction, with system tickets always before manual tickets (T-37).
- Repeat the order and inventory style of race checks for tickets, roles, and authentication once the code exists (T-17, T-34).
- Open for discussion, because they reach beyond tickets: the audit formats of the other actions (Q-03), and what happens to the orders and products of a disabled user (Q-16).
- The open items of [database-v1.5.md](database-v1.5.md), [database-v1.4.md](database-v1.4.md), [database-v1.3.md](database-v1.3.md), and [database-v1.2.md](database-v1.2.md) still apply, except those listed above.
