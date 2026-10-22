# Database Iteration v1.5

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.5 |
| Date | 2026-10-22 to 2026-10-23 |
| Baseline | v1.4, see [database-v1.4.md](database-v1.4.md) |
| Type | Design rules and schema changes (one new stock type, one new constraint, one new table, new columns and a new status on `tickets`) |
| Table count | 14 (one added: `ticket_inventory`) |
| Trigger | A walk-through of the inventory screen: operators must be able to raise and lower stock, with a record of why, without being able to set the quantity directly. A review of the ticket system followed: a stock exception ticket could not point at the products it was about, because a ticket could only reference an order, and one problem often affects a batch of products |

## Summary

Version 1.5 defines how people change stock by hand, and how a ticket points at stock.

- There are three manual stock operations: stock in, write-off or stock out, and setting the quantity directly. Operators may use the first two, and only managers and administrators may set the quantity. Every manual change needs a reason and the acting user, and each one leaves a row in `inventory_transactions`.
- A new link table, `ticket_inventory`, joins tickets to inventory records many-to-many, so one ticket can cover a batch of products. A usage rule says a ticket is for a problem the finder cannot or may not solve alone, and for the two automatic cases.
- Ticket status flow (closes Q-15): a ticket is never reopened, `CLOSED` is the archive that a manager or an administrator fills in batches, and a new status `CANCELLED` withdraws a ticket that is not yet resolved, with a required reason. A problem that continues opens a new ticket that follows the old one, so the history stays connected.
- Taking and reassigning tickets: an eligible assignee is defined, and only a manager or an administrator can take a ticket from its assignee, directly and with a reason. There is no request process. An `IN_PROGRESS` ticket cannot be followed and stays with its assignee.
- A new section, Concurrency by Module, applies the concurrency thinking to products, tickets, users and roles, and authentication, and requires every later write path to state its protection.

## Changes

### Stock types

- Added the type `WRITE_OFF`: `available_quantity` decreases, for stock that is lost, damaged, or taken out of the warehouse. Together with `RESTOCK`, it gives `OPERATOR` a way to lower stock without being allowed to set it directly.
- The meaning of `ADJUST_IN` and `ADJUST_OUT` is narrowed to "the quantity was set higher or lower by hand". The type is chosen by the sign of the difference between the new and the old quantity.

### Manual stock changes

- The three manual operations are stock in (`RESTOCK`), write-off or stock out (`WRITE_OFF`), and setting the quantity directly (`ADJUST_IN` or `ADJUST_OUT`). `OPERATOR` may use the first two; only `MANAGER` and `ADMIN` may set the quantity directly.
- Every manual change needs a non-blank `reason` and an `operator_id`. A new `CHECK`, `chk_inventory_tx_manual`, enforces it for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. Before, `reason` was optional for every type.
- A change must change something: setting the quantity to its current value is refused.
- Direct set carries the `available_quantity` the user saw. Under the row lock the service layer compares it with the real value and refuses the request when they differ.
- Write-off refuses to go below zero and cannot touch reserved stock.
- Screen rule: an `OPERATOR` sees only a plus and a minus; a `MANAGER` or `ADMIN` also gets a field for the new quantity. Save is enabled only when the quantity changed and the reason is filled in.
- Manual changes use the same row lock, transaction, retry, and deadlock rules as the order flows, so a manual change and an order on the same product wait for each other.

### Permission matrix

- The single "Restock" row and the single "Manual correction" row became three rows: stock in, write-off or stock out, and set the quantity directly. The first two are open to `OPERATOR`, `MANAGER`, and `ADMIN`; the third to `MANAGER` and `ADMIN`. All three require a reason.
- The working default "`OPERATOR` can restock but not correct stock" now reads "can stock in and write off but not set the quantity directly".
- The ticket row "Create manually" now also covers linking inventory records to a ticket.

### When to open a ticket

- Tickets are for a problem that needs someone else, and for exceptions the system finds itself. A problem that one person can finish directly is not a ticket.
- Automatic tickets stay limited to the two cases already listed under Exceptions.
- Manual tickets are open to any role that may create them, but the purpose is narrowed by a usage rule. The rule is not enforced by the database or the program.
- `OTHER` means a problem that needs someone else but is neither an order nor a stock problem.
- Link to the stock rules above: an `OPERATOR` cannot set a stock quantity directly, so a count that disagrees with the records becomes a ticket to a `MANAGER`.

### Linked inventory

- New table `ticket_inventory` (`id`, `ticket_id`, `inventory_id`, `created_at`). Both foreign keys are `RESTRICT`. `UK(ticket_id, inventory_id)` stops the same link from being added twice. An index on `inventory_id` supports looking up the tickets of a product.
- A ticket can have no links, one, or many. An order exception or a general ticket needs none.
- The table is append-only. A link added by mistake is explained in a comment and is not removed, which keeps it consistent with comments and assignment history. The application account gets `SELECT` and `INSERT` only.
- The link points at the inventory record, as `inventory_transactions` does, so the product is reached through `inventory`.
- The service layer should ask for at least one link on an `INVENTORY_EXCEPTION` ticket. The database cannot require it, because a `CHECK` cannot look at another table.

### Ticket status flow

- Five statuses: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, and the new `CANCELLED`. Only these transitions are allowed: `OPEN` to `IN_PROGRESS`, `IN_PROGRESS` to `OPEN` (when the assignee is removed), `IN_PROGRESS` to `RESOLVED`, `RESOLVED` to `CLOSED`, and `OPEN` or `IN_PROGRESS` to `CANCELLED`.
- **No reopening, ever.** A `RESOLVED` ticket cannot go back to `IN_PROGRESS`, and `CLOSED` and `CANCELLED` are final and frozen.
- **`CLOSED` is the archive.** It means the problem is solved and filed, not given up. A `MANAGER` or `ADMIN` archives `RESOLVED` tickets from time to time, one by one or in a batch. The archive has its own screen, open to every role that can view tickets, `VIEWER` included.
- **`CANCELLED`** withdraws a ticket before it is resolved, for example one opened by mistake or a duplicate. A `MANAGER` or an `ADMIN` may cancel any `OPEN` or `IN_PROGRESS` ticket. An `OPERATOR` may cancel only their own: a ticket assigned to them, or one they created while it is still `OPEN`. `VIEWER` never. A reason is required. A `RESOLVED` ticket can only be archived.
- `IN_PROGRESS` always has an assignee, so removing the assignee returns the ticket to `OPEN`.
- An `OPERATOR` may move a ticket to `IN_PROGRESS` or `RESOLVED` only when it is assigned to them. This narrows the earlier matrix row, which let any operator do it.
- Comments are allowed until the ticket is `CLOSED` or `CANCELLED`. Priority and assignment can change while it is `OPEN` or `IN_PROGRESS`.
- New columns on `tickets`: `closed_by`, `cancelled_by`, `cancelled_at`, `cancel_reason` (`VARCHAR(255)`), and `follows_ticket_id`.
- **The database checks only the allowed status values.** The transitions and the fields each one must fill are enforced in the service layer, on purpose. This is recorded in Initial Constraints and in T-33 so that it is not forgotten when the service is built.

### Follow-up tickets

- New column `tickets.follows_ticket_id`, a nullable foreign key to `tickets(id)` with `RESTRICT`. It is set when the ticket is created and never changes.
- The earlier ticket must be `RESOLVED` or `CLOSED`. An `OPEN` ticket takes a comment instead, an `IN_PROGRESS` ticket stays with its assignee and only a manager or an administrator can hand it over (see Taking and reassigning tickets), and a `CANCELLED` ticket was withdrawn.
- A ticket follows at most one earlier ticket, and an earlier ticket can have many follow-ups, so a chain can form. The link is visible to everyone who can view tickets, in both directions and in every status of the new ticket.
- Anyone who may create a ticket may open a follow-up.
- A ticket cannot follow itself, because the link is written at creation and the ticket does not exist yet. MySQL does not allow a `CHECK` on an `AUTO_INCREMENT` column, so none is added.

### Taking and reassigning tickets

- **Eligible assignee.** A usable account (`ACTIVE`, not soft-deleted) that holds `OPERATOR`, `MANAGER`, or `ADMIN`. `VIEWER`, a user with no role, a disabled user, and a soft-deleted user cannot take or be given a ticket. The check runs in the same transaction every time an assignee changes: taking and assigning.
- **Whoever holds a ticket controls it.** Only the assignee, and a `MANAGER` or an `ADMIN`, can move it forward. An `OPERATOR` can cancel only their own ticket: one assigned to them, or one they created while it is still `OPEN`.
- **Reassigning is for managers and administrators only.** An operator cannot take, or ask for, a ticket that someone else holds. The manager or administrator acts directly. Only the assignee changes; the ticket keeps its number, status, comments, links, and priority, and continues in the normal flow. The previous assignee can no longer move or cancel it.
- **A reassignment is valid only when:** the actor is a usable `MANAGER` or `ADMIN`; the ticket is `OPEN` or `IN_PROGRESS`; the new assignee is an eligible assignee and differs from the current one; the assignee the actor saw is still the current one (compared under the ticket row lock, otherwise the screen reloads); and a non-blank reason is given. It writes a `ticket_assignment_history` row and an `operation_logs` row in the same transaction.
- **Concurrency.** Two managers reassigning the same ticket are serialized by the ticket row lock and the expected-assignee check. An assignment racing the disabling of the new assignee is serialized by a `FOR UPDATE` lock on the assignee's user row, which is also what makes the 99-ticket count safe. Both rows are in Concurrency by Module.
- **Withdrawn design.** A request process was drafted in this iteration: an operator asks to take over a ticket in progress, with a reason, and a manager approves or rejects, with the request kept in a new table. The user withdrew it, because reassignment by a manager or an administrator gives the same result with no extra states, table, or voiding rules. Nothing of it remains in the schema or the other documents, apart from this note and the matching row under Decisions.
- **Workload limit (closes Q-20).** A person may hold at most 99 unresolved tickets: assigned to them and `OPEN` or `IN_PROGRESS`. At the limit they cannot take another, and a manager or an administrator cannot assign or reassign one to them. It is an operating parameter, so it can change without a migration.

### Concurrency by module

- A new section in the design document lists, for every module, what can race and what protects it: products (row lock, changed fields only, one-statement product read for orders), tickets (conditional updates, row locks, batch archive in separate transactions), users and roles (lock the user row first, then the `ADMIN` role row), and authentication (atomic counters and one-time code consumption).
- Principles: a read-then-write is never enough; a unique constraint is the last line and not the only one; a fixed lock order (`users`, `roles`, `tickets`, `orders`, `inventory`); no outside calls while holding locks; every audit log written in the same transaction.
- Any new or changed write path states its reads, its protection, and its place in the lock order before it is built. This is also a line in the checklist of [documentation-sync-rules.md](../documentation-sync-rules.md).

### Migration

`V1__initial_schema.sql` was edited in place, because it has not been applied anywhere (T-01 is still open). The `type` check allows `WRITE_OFF`, the movement check handles it, `chk_inventory_tx_manual` is new, and so is the table `ticket_inventory`. `tickets` gets five columns, five foreign-key and index additions, and `CANCELLED` in the status check.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Reducing stock by hand (operators) | A separate type, `WRITE_OFF`, open to `OPERATOR` | Letting `OPERATOR` use `ADJUST_OUT`, which would blur "write-off" with "set the quantity"; no way to reduce, which leaves operators unable to record damaged goods |
| Reason on manual stock changes | Required for all four manual types, and the operator too, enforced by a `CHECK` and the service layer | An optional reason, which leaves the most disputed stock changes unexplained; a service-layer check only, which direct SQL would bypass |
| Direct set under concurrency | Lock the row, compare with the value the user saw, refuse if it differs | Overwriting with the typed number, which silently undoes reservations made since the screen loaded; a version column, which would mix optimistic locking into a pessimistic design |
| What manual changes touch | `available_quantity` only | Letting a manual change touch `locked_quantity`, which belongs to orders |
| Manual ticket scope | Narrow it by a written usage rule: a problem the finder cannot or may not solve alone | Leaving it open, which turns the ticket list into a place for notes and hides the tickets that need action |
| How a ticket points at stock | A link table, `ticket_inventory`, many-to-many | A single `inventory_id` column on `tickets`, which cannot describe a batch of products that fail together |
| Links after creation | Append-only; a mistaken link is explained in a comment | Allowing deletion, which needs `DELETE` on the table and loses the record that the link once existed |
| Link target | The inventory record | The product, which is the same thing today because the relationship is one-to-one, but would be less precise if warehouses are added later (Q-04) |
| Requiring a link on `INVENTORY_EXCEPTION` | The service layer should ask for one; the database does not require it | A database rule, which a `CHECK` cannot express across tables |
| Reopening a resolved ticket | Never. A continuing problem opens a new ticket | Allowing a reopen by a manager, which keeps one ticket per problem but rewrites a ticket that was already declared solved |
| Tracing a problem across tickets | `tickets.follows_ticket_id`, set at creation, to a `RESOLVED` or `CLOSED` ticket, visible to everyone | A separate link table, which allows several parents but is not needed because a follow-up continues one problem; a free-text reference in the description, which cannot be searched |
| Meaning of `CLOSED` | The archive: solved and filed, done by a manager or an administrator, with its own screen | `CLOSED` as "given up", which would blur it with `CANCELLED` and leave no state for archiving |
| Withdrawing a ticket | A new status, `CANCELLED`, allowed from `OPEN` and `IN_PROGRESS`, with a required reason and `cancel_reason`, `cancelled_by`, and `cancelled_at` columns | Reusing `CLOSED` with a reason, which cannot tell a solved ticket from a withdrawn one; keeping the reason only in a comment or log, which cannot be queried as a field |
| Who may cancel | `MANAGER` and `ADMIN` any ticket; `OPERATOR` only their own (assigned to them, or created by them while `OPEN`) | Any role that works on tickets, whatever the owner, which lets an operator withdraw someone else's ticket. This was the first draft and was narrowed by the user |
| Changing a ticket's owner | Only a `MANAGER` or an `ADMIN`, directly, with a reason, to an eligible assignee, while the ticket is `OPEN` or `IN_PROGRESS` | A transfer request process (an operator asks, a manager decides) with its own table and statuses, drafted and then withdrawn by the user; letting any operator take a ticket at will, which lets a person pull work away from a colleague |
| Two managers reassigning at once | Lock the ticket row and compare the assignee the manager saw with the real one; refuse if it differs | Overwriting with the later request, which silently undoes the first reassignment |
| Limit on tickets per person | 99 unresolved tickets (`OPEN` or `IN_PROGRESS`), counted under an exclusive lock on the assignee's user row | No limit, which lets a person hoard work; counting only `IN_PROGRESS`, which lets a manager pile up assigned but unstarted tickets on one person; a shared lock for the count, which lets two simultaneous assignments both pass at 98 |
| Where the ticket status rules live | The service layer only, with the allowed status values checked by the database. Recorded in Initial Constraints and T-33 | `CHECK` constraints that tie each status to its fields, which would also stop a service-layer bug, at the cost of a stricter schema |
| Archive in batches | Each ticket is archived in its own transaction through the status gate | One transaction for the whole batch, which would lock many tickets at once and fail all of them for one conflict |
| Concurrency | A per-module section, and every later write path states its protection | Handling each race when the code is written, which leaves it to chance |
| Linking a ticket to the stock change that fixed it | Not done. The correction's reason can name the ticket | A nullable `ticket_id` on `inventory_transactions`, which can be added later with a new migration if tracing proves hard |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: stock types, a new Manual stock changes section, concurrency rules, the Permission Matrix, the table count, the Tickets section (When to open a ticket, Linked inventory), Relationship Summary, Initial Constraints, and the Change Log |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | Updated in place (`WRITE_OFF` and `chk_inventory_tx_manual` on `inventory_transactions`, the table `ticket_inventory`, and on `tickets` the `CANCELLED` status and the columns `follows_ticket_id`, `closed_by`, `cancelled_by`, `cancelled_at`, and `cancel_reason`); not yet applied anywhere |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: `INSERT` on `ticket_inventory`, in the append-only group |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: `ticket_inventory` added to the append-only table list |
| [Verification script and fixtures](../../db/verification/run-db-verification.ps1) | Updated: checks for `WRITE_OFF` and the reason and operator rule, the two `RESTOCK` fixtures given a reason and an operator, the table count is 14, checks for several links, duplicate links, a missing inventory record, and deleting a linked ticket, insert and refusals for the application account, and `reset_data` clears the new table and deletes follow-up tickets before the tickets they follow. For tickets: `CANCELLED` and `CLOSED` are accepted, `REOPENED` is rejected, a ticket can follow another, following a missing ticket is rejected, a followed ticket cannot be deleted, and `closed_by` and `cancelled_by` must be real users. **Re-run on 2026-10-23 against the final schema (MySQL 8.4): 94 checks passed, 0 failed**, including the order and inventory races. No race checks were added for the new ticket and stock rules, because they need the application code (T-17, T-34) |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated: in `inventory_transactions`, `type` lists `WRITE_OFF`, and the notes on `operator_id` and `reason` say they are required for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. The new table `ticket_inventory` and two connectors, `tickets` to `ticket_inventory` (links) and `inventory` to `ticket_inventory` (appears on), both one to zero-or-more and solid because both keys are required. In `tickets`: the rows `follows_ticket_id`, `closed_by`, `cancelled_by`, `cancel_reason`, and `cancelled_at`, and `CANCELLED` in the status values; and three connectors, `tickets` to itself (continued by), and `users` to `tickets` (may close and may cancel), all dashed because the keys are optional. |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated: the table `ticket_inventory` with its main columns, and the same two connectors; in `tickets`, the row `follows_ticket_id` and a connector from `tickets` to itself (continued by) |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn) | Updated: the ticket step "Resolve and Close" in Exception Handling now reads "Resolve, then Archive (CLOSED)". Two new sections draw the ticket lifecycle. **Ticket Creation**: opening a ticket, the follow-up check (the earlier ticket must be `RESOLVED` or `CLOSED`), creating it as `OPEN`, taking it or having a manager assign it, the eligible-assignee check, and starting work as `IN_PROGRESS`. **Ticket Handling**: investigating and commenting, marking `RESOLVED`, archiving to `CLOSED` by a manager, the archive being visible to everyone, a continuing problem opening a follow-up instead of reopening, reassignment by a manager or an administrator, and the cancel path (own ticket or manager, with a reason) from `OPEN` or `IN_PROGRESS`. Manual stock changes and ticket links are not drawn |

## Open Items

- Implement manual stock changes, with a concurrency check against order creation on the same product (T-30).
- Implement ticket creation with linked inventory, and show the usage rule in the screen text (T-14).
- Implement the ticket status rules in the service layer, including the fields each transition must fill, the follow-up rule, the archive screen, and batch archiving (T-33). The database does not enforce them, so this task must not be skipped.
- Implement taking and reassigning a ticket in the service layer: the eligibility checks, the expected-assignee check under the ticket row lock, and the history and log rows in one transaction (T-35).
- Implement the concurrency rules of every module as they are built, and repeat the order and inventory style of check for tickets, roles, and authentication (T-34).
- Decide the audit log formats for ticket actions, now including cancel, archive, and follow-up (Q-03), and which indexes the archive screen and the follow-up chain need (Q-13).
- The open items of [database-v1.4.md](database-v1.4.md), [database-v1.3.md](database-v1.3.md), and [database-v1.2.md](database-v1.2.md) still apply.
