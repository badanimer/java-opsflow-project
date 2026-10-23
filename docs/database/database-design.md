# OpsFlow Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The initial model contains 14 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Change Log

This document always describes the current design. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-10-23 | 1.6 | Replaced the ticket priority with `tickets.source` and a built-in SYSTEM identity (`users.is_system`) that opens and archives for the program; made `OPEN` a pool and taking a ticket start it at once; added releasing a ticket, returning the tickets of a disabled or deleted assignee, two limits of 99 per person, archiving after 7 days or when 99 are waiting, and a separate cancelled area; required an order on an `ORDER_EXCEPTION` ticket and had the program fill in the order and the problem on automatic tickets; wrote inventory links only at creation; defined the ticket log formats and three ticket indexes, ordered every ticket list with system tickets first, fixed a ticket's fields after creation, made the exception path stop the order and open the ticket together, and added the indexes for the ticket lists and the order and log lists | [database-v1.6.md](../iterations/database-v1.6.md) |
| 2026-10-23 | 1.5 | Added the `WRITE_OFF` stock type and defined manual stock changes: stock in, write-off, and direct quantity setting, a required reason and operator on every manual change, and direct setting for `MANAGER` and `ADMIN` only; added the table `ticket_inventory` so one ticket can cover a batch of products, wrote down when a ticket should be opened, defined the ticket status flow (no reopening, `CLOSED` as the archive, a new `CANCELLED` status with a required reason) and follow-up tickets (`tickets.follows_ticket_id`), added the Concurrency by Module section, and defined who may take a ticket and who may reassign it (only a `MANAGER` or an `ADMIN`), and narrowed cancelling for operators to their own tickets | [database-v1.5.md](../iterations/database-v1.5.md) |
| 2026-10-21 | 1.4 | Defined a usable account, refused role grants to accounts that are not usable, made permission checks ignore them, defined role management (create, update, delete, replace a user's roles) and its audit logs, made a user with no role a valid state, fixed the rule that at least one usable `ADMIN` must always exist, defined how the first administrator is created, and added `users.must_change_password`, and fixed who may change what: the `MANAGER` role may assign roles below manager but cannot touch role definitions, `ADMIN`, or `MANAGER`, and added the permission matrix for the four roles, and defined login lockout, email codes for sign-up and recovery, and per-request token checks | [database-v1.4.md](../iterations/database-v1.4.md) |
| 2026-10-21 | 1.3 | Required the service layer to refuse role grants to soft-deleted users, and required role grants and revocations to be logged in `operation_logs` | [database-v1.3.md](../iterations/database-v1.3.md) |
| 2026-10-14 | 1.2 | Unified stock concurrency on pessimistic row locking, clarified expired confirmation, froze order items, made composite keys explicit, added the initial migration, and verified concurrency and privileges | [database-v1.2.md](../iterations/database-v1.2.md) |
| 2026-10-14 | 1.1 | Added stock reservation, redefined order statuses, and aligned the document with the physical ERD | [database-v1.1.md](../iterations/database-v1.1.md) |
| Before 2026-10-14 | 1.0 | Initial database design baseline with 12 tables | Commit `7ab5f55` |

## Design Diagrams

| Diagram | Purpose | Link |
| --- | --- | --- |
| Simplified ERD | Provides a concise overview of the core entities and relationships | [Open Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) |
| Physical ERD | Defines implementation-oriented fields, keys, and relationships | [Open Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) |
| Business Process Flowchart | Describes the order, inventory, audit, and exception-ticket flow | [Open Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) |

The Simplified ERD and the Business Process Flowchart both reflect inventory reservation and the confirm, cancel, release, timeout, and retry steps. The Business Process Flowchart also shows the idempotency check on order submission: a key already used by the same user returns the original order, and a duplicate-key race rolls back and returns it too.

## Table Overview

### Identity and Access

| Table | Responsibility |
| --- | --- |
| `users` | Stores user accounts, identity information, and account status |
| `roles` | Defines system roles |
| `user_roles` | Associates users with roles and supports multiple roles per user |
| `email_verification_codes` | Holds the one-time email codes used for sign-up and password recovery |

`users` and `roles` have a many-to-many relationship implemented through `user_roles`. A user cannot be granted the same role twice, so `user_roles` has a composite unique key on `(user_id, role_id)`.q

`users` and `products` use soft deletion, which is marked by the `deleted` flag. On `users`, `status` is a separate field: `ACTIVE` or `DISABLED`. `status` says whether the account can be used now and can change back and forth, while `deleted` says whether the account is treated as removed. When a user is soft-deleted, the service layer also sets `status` to `DISABLED`. Unique values such as `username`, `email`, and `sku` stay reserved after soft deletion and cannot be reused. This protects historical orders and audit records.

#### Role grants

The foreign keys on `user_roles` only check that the user row and the role row exist. They do not read `deleted` or `status`, so the database accepts a grant to a soft-deleted or disabled user. A user is a **usable account** only when `status` is `ACTIVE` and `deleted` is false. Two rules follow, and both live in the service layer because the database cannot express them with a foreign key:

- The application must refuse to grant a role to a user who is not a usable account.
- A permission check counts the roles of a user only when the user is a usable account. A disabled or soft-deleted user has no effective permissions, even though the `user_roles` rows still exist.
- Revoking a grant is always allowed, including a grant held by a disabled user, except where Administrator protection refuses it.

Disabling a user does not delete the rows in `user_roles`. When the user is set back to `ACTIVE`, the existing roles take effect again with no re-grant. Because a permission check must read `status` and `deleted` on every request, a login token issued before the account was disabled must not keep working: either check the account on every request, or keep tokens short-lived.

`user_roles` holds only the grants that exist now. It has no history and no deletion mark, so after a grant is revoked, nothing in the table shows that it ever existed. Every grant and revocation is therefore written to `operation_logs`; see Auditing.

#### The SYSTEM identity

OpsFlow has one built-in identity that is not a person: SYSTEM. It gives what the program does by itself an actor that can be recorded and shown, for example the creator of a ticket the program opens, or the one who archives a ticket after 7 days.

- It is one row in `users` with `is_system` true, the username `system`, and the reserved address `system@opsflow.invalid`. `password_hash` holds a fixed marker that is never a valid hash, so no password can match it. A seed migration creates it, together with the roles (T-05).
- It is not a person. It has no role and can never be granted one, so it is never an eligible assignee. It cannot log in: a login for it is refused before any password check and is not counted as a failed attempt. It cannot be edited, disabled, soft-deleted, or have its password changed, and it does not appear in user lists or in the pickers for assigning a ticket.
- It is exempt from the ticket limits.
- Where a user id is required, the SYSTEM identity fills it: `tickets.created_by` for a ticket the program opens, `tickets.closed_by` for an automatic archive, and `ticket_assignment_history.changed_by` for a change the program makes. Audit log rows of system actions keep `operator_id` empty, as before.
- A ticket the program opens also has `tickets.source` set to `SYSTEM`, so the class is stored once on the ticket as well and the screen need not look up the creator.

### Product and Inventory

| Table | Responsibility |
| --- | --- |
| `products` | Stores product details, prices, and sales status |
| `inventory` | Stores the current stock position and concurrency-control data for each product |
| `inventory_transactions` | Records every stock movement: restocks, write-offs, adjustments, reservations, confirmations, and releases |

In the initial version, each product has one current inventory record, so `products` and `inventory` have a one-to-one relationship. An inventory record has many inventory transactions. A transaction references its inventory record only; the product is reached through `inventory`.

`inventory` represents the current state for efficient reads. `inventory_transactions` preserves the history required to explain why stock changed.

#### Stock quantities

| Field | Meaning |
| --- | --- |
| `available_quantity` | Stock that can be sold now. It excludes reserved stock. |
| `locked_quantity` | Stock reserved by orders in `CREATED` status |
| `sold_quantity` | Stock permanently deducted by completed orders |

All three quantities must stay greater than or equal to zero. Stock still held in the warehouse is `available_quantity + locked_quantity`. Stock is changed only under pessimistic row locking; see Concurrency and duplicate protection.

#### Inventory transaction types

| Type | Effect on `inventory` |
| --- | --- |
| `RESTOCK` | `available_quantity` increases (stock received) |
| `WRITE_OFF` | `available_quantity` decreases (stock lost, damaged, or taken out of the warehouse) |
| `ADJUST_IN` | `available_quantity` increases (the quantity was set higher by hand) |
| `ADJUST_OUT` | `available_quantity` decreases (the quantity was set lower by hand) |
| `RESERVE` | `available_quantity` decreases, `locked_quantity` increases |
| `CONFIRM` | `locked_quantity` decreases, `sold_quantity` increases |
| `RELEASE` | `locked_quantity` decreases, `available_quantity` increases |

`quantity` is always greater than zero; the type determines the direction. Each transaction stores `available_before`, `available_after`, `locked_before`, and `locked_after` so that any movement can be audited without replaying history. The snapshots are taken from the inventory row while it is locked by the same transaction, so they are exact.

`order_id` is required for `RESERVE`, `CONFIRM`, and `RELEASE`, and must be empty for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. `operator_id` is empty for system actions such as timeout release.

#### Manual stock changes

People change stock by hand in three ways. All three work on `available_quantity` only, because `locked_quantity` belongs to orders.

| Operation | Recorded as | Who |
| --- | --- | --- |
| Stock in (plus button) | `RESTOCK` | `ADMIN`, `MANAGER`, `OPERATOR` |
| Write-off or stock out (minus button) | `WRITE_OFF` | `ADMIN`, `MANAGER`, `OPERATOR` |
| Set the quantity directly | `ADJUST_IN` if the new number is higher, `ADJUST_OUT` if it is lower | `ADMIN`, `MANAGER` |

- **Reason is required for every manual change.** Stock in, write-off, and direct set all need a non-blank reason and the acting user. `RESERVE`, `CONFIRM`, and `RELEASE` come from orders and need neither. The database enforces this with a `CHECK`, and the service layer validates it first.
- **A change must change something.** The quantity is greater than zero. Setting the quantity to the number it already has is refused and writes nothing.
- **Screen.** The inventory screen has one Modify button per product. An `OPERATOR` who presses it sees only a plus and a minus, plus a reason field. A `MANAGER` or `ADMIN` also gets a field to type the new quantity. Save is enabled only when the quantity has changed and the reason is filled in.
- **Direct set.** The user types the new `available_quantity`. The request also carries the `available_quantity` the user saw. Inside the transaction the service layer locks the `inventory` row and compares it with the value seen. If they differ, someone else changed the stock in the meantime, and the request is refused with a "stock changed, please reload" result. If they match, the difference becomes the transaction quantity, and the type follows its sign.
- **Write-off.** Under the same lock, `available_quantity >= q` must hold, otherwise the request is refused. Reserved stock cannot be written off.
- **One transaction.** Each manual change locks the `inventory` row, updates it, inserts the `inventory_transactions` row with exact snapshots, and writes `operation_logs`, all together. It follows the same lock order, retry, and deadlock rules as the order flows.

### Orders

| Table | Responsibility |
| --- | --- |
| `orders` | Stores order-level information such as the customer, order number, total amount, status, and reservation deadline |
| `order_items` | Stores the purchased product snapshot, quantity, and transaction price |

A user can create many orders. Each order must contain one or more order items. Each `order_items` record references exactly one product, and an order contains at most one item per product (`UK(order_id, product_id)`).

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`

An order item should retain the product name or SKU and price as they were when the order was created. Later product updates must not change the meaning of historical orders.

Once an order is created, its items and amounts are frozen. The product, quantity, price, and composition of the items, as well as `total_amount` and `item_count`, must not be edited or deleted. Confirm and release read `order_items.quantity` to decide how much stock to move, so a changed quantity would move the wrong amount. To change an order, cancel it and create a new one. For the same reason `order_items` has `created_at` but no `updated_at`.

#### Order and ticket numbers

`orders.order_no` is `ORD-` followed by a random UUID without hyphens, for example `ORD-3f2b8c1e9a4d4b6f8e0c5a7d1b2c3d4e`. `tickets.ticket_no` follows the same pattern with `TKT-`. The application generates them in lowercase. The unique constraint on each column stays as the final guard: on the practically impossible collision, the insert fails and the application retries with a new UUID.

#### Order status

| Status | Meaning |
| --- | --- |
| `CREATED` | The order exists and its stock is reserved until `reserved_until` |
| `COMPLETED` | The order was confirmed and the reservation became a permanent deduction |
| `CANCELLED` | The order was cancelled or its reservation timed out, and the reservation was released |
| `FAILED` | The order ended with a definite business failure, and the reservation was released |

`CREATED` is the only non-final status. The only valid transitions are `CREATED → COMPLETED`, `CREATED → CANCELLED`, and `CREATED → FAILED`. There is no payment status because payment is outside the current system boundary.

Additional `orders` fields support this lifecycle:

- `reserved_until` is the deadline for the stock reservation.
- `finished_at` is set when the order reaches a final status.
- `status_reason` records why an order was cancelled or failed. Its values include `RESERVATION_TIMEOUT`, `RESERVATION_EXPIRED`, the text a person gives when cancelling, and `EXCEPTION_TICKET:` followed by the ticket number for an order stopped by the exception path.
- `idempotency_key` is optional and unique per user (`UK(user_id, idempotency_key)`), so a repeated submission returns the original order.

### Tickets

| Table | Responsibility |
| --- | --- |
| `tickets` | Stores tickets: the fixed creator, the current assignee, the resolver, the status, the ticket it follows, and who closed or cancelled it |
| `ticket_assignment_history` | Records every change of assignee: taking, assigning, reassigning, and releasing |
| `ticket_comments` | Stores investigation notes, communication, and resolution details. Append-only. |
| `ticket_inventory` | Links a ticket to the inventory records it is about, written when the ticket is created, so one ticket can cover a batch of products. Append-only. |

The ticket creator is fixed in `created_by`. `assignee_id` contains only the current assignee, and a ticket has at most one current assignee at a time. Each change of assignee creates a new `ticket_assignment_history` record instead of overwriting the history. A record holds the previous assignee (`from_user_id`, empty when the ticket came from the pool), the new assignee (`to_user_id`, empty when the ticket goes back to the pool), the acting user (`changed_by`), the reason, and the time.

`tickets.type` uses `ORDER_EXCEPTION`, `INVENTORY_EXCEPTION`, or `OTHER`. It is required and has no database default, so a missing value fails instead of silently producing a wrong one. The program must set the type when it opens a ticket by itself. `tickets.status` uses `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, or `CANCELLED`, and defaults to `OPEN`. `tickets.source` is `SYSTEM` or `MANUAL` (see Where a ticket came from). `order_id` is optional, except that an `ORDER_EXCEPTION` ticket must name its order. The database enforces that with a `CHECK` (`chk_tickets_order_required`), because it only needs the same row. A ticket of any other type may name an order too.

#### When to open a ticket

A ticket is for a problem that the person who found it cannot, or may not, solve alone, and for the exceptions the system finds by itself. A problem that one person can finish directly is not a ticket: an operator who entered a wrong quantity cancels the order and creates it again.

- **Automatic tickets** are limited to the two cases listed under Exceptions.
- **Manual tickets** are opened by a person for a problem that needs someone else, such as a stock count that disagrees with the records when the finder cannot set the quantity. This is a usage rule. The database and the program do not enforce it, so it is kept in this document and in the screen text.
- `OTHER` is for a problem that needs someone else but is neither an order nor a stock problem. It is not a place for notes.

#### Where a ticket came from

There is no priority. A ticket is either opened by the system or opened by a person, and `tickets.source` says which: `SYSTEM` for a ticket the program opens, `MANUAL` for a ticket a person opens. It is set when the ticket is created and never changes. A `SYSTEM` ticket has the SYSTEM identity (see The SYSTEM identity) as `created_by`, and a `MANUAL` ticket has a real user. The two fields say the same thing on purpose, so that a screen can show or filter by the class without looking up the creator. The database cannot check the two against each other, because a `CHECK` cannot look at another table, so the service layer keeps them in step and the tests cover it (T-33). The class differs only in how the screen may show it, for example a different background colour. How lists are ordered by it is decided (see Ticket list order). Whether the screen shows it in another way as well, such as a background colour, is decided later.

#### Ticket list order

Every role that can view tickets can choose the order of a ticket list. The default is newest first, and the user can switch to oldest first. Whichever direction is chosen, tickets with `source` `SYSTEM` are always shown before tickets with `source` `MANUAL`: the oldest system ticket comes before the newest manual ticket, and the same holds in the other direction. Inside each of the two groups the chosen time order applies. This gives system tickets a higher place on every list without a priority.

The time that orders a list is the one the list is about: the creation time for the pool and the other work lists, the archive time on the archive screen, and the cancellation time in the cancelled area. The choice is only a screen setting and is not stored in the database. The rule needs no extra field, because the order is `source` first and then the time.

#### Fields fixed after creation

A ticket is fixed once it is created. Its `title`, `description`, `type`, `source`, `order_id`, `follows_ticket_id`, `created_by`, and its inventory links never change. Only the fields that the transitions set change: the status, the assignee, `resolution`, `resolved_by`, `resolved_at`, `closed_by`, `closed_at`, `cancel_reason`, `cancelled_by`, and `cancelled_at`. Something that was missed is added in a comment. A ticket that was opened wrongly is cancelled and opened again, so no edit history has to be kept. The service layer enforces this, and the database does not (T-33).

#### Ticket status

| Status | Meaning |
| --- | --- |
| `OPEN` | The ticket is in the pool: nobody holds it. It never has an assignee. |
| `IN_PROGRESS` | Someone holds it and is working on it. It always has an assignee. A ticket enters this status the moment someone takes it or is assigned it, like accepting a quest. |
| `RESOLVED` | The assignee says the problem is solved and has written the resolution. It waits to be archived. |
| `CLOSED` | The problem is solved and the ticket is archived. `CLOSED` does not mean "given up". |
| `CANCELLED` | The ticket was withdrawn before it was resolved, for example because it was opened by mistake or duplicates another one. A reason is required. It leaves the pool and every work list and is shown only in its own cancelled area. Nobody can take, assign, comment on, or revive it. |

`CLOSED` and `CANCELLED` are final. A ticket in either status is frozen: its status, assignee, links, and comments no longer change. A ticket that leaves `IN_PROGRESS` for `RESOLVED`, `CLOSED`, or `CANCELLED` keeps its last assignee, as a record of who held it.

#### Status transitions

A ticket is never reopened. If the problem comes back or continues, a new ticket is opened that follows the old one (see Follow-up tickets). Only the transitions below are allowed. The service layer enforces them, and the database does not (see Decisions in database-v1.6.md).

| From | To | Who | Must have | Also sets |
| --- | --- | --- | --- | --- |
| `OPEN` | `IN_PROGRESS` | **Take**: any eligible assignee, for themselves. **Assign**: `MANAGER` or `ADMIN`, to an eligible assignee. | The ticket is still `OPEN`, and the new assignee is below the in-progress limit | `assignee_id`, and a history row from empty to the assignee |
| `IN_PROGRESS` | `OPEN` | **Release**: the assignee, for their own ticket, or a `MANAGER` or an `ADMIN` removing the assignee. The system does it when the assignee's account is no longer usable. | A non-blank reason. The system writes a fixed reason. | Clears `assignee_id`, and a history row from the assignee to empty |
| `IN_PROGRESS` | `RESOLVED` | The assignee, `MANAGER`, `ADMIN` | A non-blank `resolution` | `resolved_by`, `resolved_at` |
| `RESOLVED` | `CLOSED` | A `MANAGER` or an `ADMIN` at any time, or the system 7 days after `resolved_at` | | `closed_by` (the user, or the SYSTEM identity), `closed_at` |
| `OPEN` or `IN_PROGRESS` | `CANCELLED` | `MANAGER` and `ADMIN` for any ticket. `OPERATOR` only for their own: a ticket assigned to them, or one they created while it is still `OPEN`. `VIEWER` never. | A non-blank reason | `cancel_reason`, `cancelled_by`, `cancelled_at` |

- **Releasing is not cancelling.** A released ticket goes back to the pool for someone else to take, and the problem is still open. A cancelled ticket is withdrawn for good, because it was a mistake or a duplicate.
- A `RESOLVED` ticket cannot be cancelled. It can only be archived.
- **Cancelled tickets are set apart.** A `CANCELLED` ticket leaves the pool and every work list, and appears only in its own cancelled area, which every role that can view tickets can open, like the archive. Nobody can take, assign, comment on, or bring it back. It is kept and not deleted, because the logs, the follow-up links, and the history refer to it.
- "Own" means the ticket's current assignee, or its creator while the ticket is still `OPEN`. Once someone else takes a ticket it is no longer the creator's for cancelling. To withdraw a ticket someone else holds, an operator asks a `MANAGER` or an `ADMIN`.
- Reassigning an `IN_PROGRESS` ticket to another person is not a status change; see Reassigning a ticket.
- Comments are allowed until the ticket is `CLOSED` or `CANCELLED`, so a `RESOLVED` ticket can still be discussed before it is archived.
- A ticket the program opens starts as `OPEN`, in the pool, with the SYSTEM identity as its creator.
- Every transition is made with a conditional update, `WHERE id = ? AND status = ?`, so only one person can make it, as the status gate does for orders. See Concurrency by Module.

#### Who may take a ticket

An **eligible assignee** is a usable account (`status` is `ACTIVE` and `deleted` is false) that holds at least one of `OPERATOR`, `MANAGER`, or `ADMIN`. A `VIEWER`, a user with no role, a disabled user, a soft-deleted user, and the SYSTEM identity are not eligible. The check applies every time a ticket gets an assignee, and it is made inside the transaction that changes the assignee:

- a person takes a ticket from the pool;
- a `MANAGER` or an `ADMIN` assigns a ticket from the pool, or reassigns an `IN_PROGRESS` ticket, to someone.

Further basic limits: the ticket must be in a status that allows the change (`OPEN` for taking and assigning, `IN_PROGRESS` for reassigning), a ticket that is already held cannot be taken, and every person who acts on a ticket must be a usable account, as for every action. Two limits apply to every role that works on tickets, and they are independent of each other. See Ticket limits.

#### Ticket limits

Each person has two limits of 99, counted separately. Both are operating parameters.

| Limit | What is counted | What it stops |
| --- | --- | --- |
| Open tickets created | Tickets the person created that are `OPEN` now | Creating another ticket, including a follow-up. A person with 99 open tickets they created cannot create a 100th until some of them are taken or ended. |
| Tickets in progress | Tickets assigned to the person that are `IN_PROGRESS` now | Taking a ticket, being assigned a ticket by a `MANAGER` or an `ADMIN`, and being given an `IN_PROGRESS` ticket by reassignment |

- The two counts do not touch each other. A ticket someone created and another person is working on counts against the worker's in-progress limit, and no longer against the creator's open limit.
- Because taking and being assigned both make the ticket `IN_PROGRESS` at once, there is no state of "assigned but not started" that could hide extra tickets from the count.
- A reassignment of an `IN_PROGRESS` ticket counts as taking in work for the new assignee, even though the ticket's status does not change, because only its assignee changes. If the new assignee is already at 99, it is refused. The previous assignee's count falls by one.
- Tickets that are `RESOLVED`, `CLOSED`, or `CANCELLED` count towards neither limit. The SYSTEM identity is exempt from both limits.
- Each count is made inside the transaction, under an exclusive lock on that person's user row, so two simultaneous requests cannot both pass it (see Concurrency by Module).

#### Reassigning and releasing a ticket

Whoever holds a ticket controls it. Only the assignee, and a `MANAGER` or an `ADMIN`, can move it forward, and an `OPERATOR` can cancel only their own. So only a `MANAGER` or an `ADMIN` can take a ticket away from its assignee. An `OPERATOR` cannot take, or ask for, a ticket that someone else holds. There is no request process: the manager or administrator decides and acts directly. The ticket keeps its number, status, comments, and links. Only the assignee changes, and the ticket continues in the normal flow. From that moment the previous assignee can no longer move or cancel it.

A reassignment is valid only when all of these hold, checked inside one transaction:

- the actor is a usable `MANAGER` or `ADMIN`;
- the ticket is `IN_PROGRESS`. A ticket in the pool is assigned, not reassigned, and a `RESOLVED`, `CLOSED`, or `CANCELLED` ticket cannot change hands;
- the new assignee is an eligible assignee, is not the current assignee, and has fewer than 99 tickets in progress (a ticket moved in counts as in progress);
- the actor saw the current assignee. The request carries the assignee shown on the screen, and under the ticket row lock it is compared with the real one. If someone changed it in the meantime, the request is refused and the screen is reloaded;
- a reason is given. The service layer requires it to be non-blank, so that every change of hands can be explained later.

A release is valid when the ticket is `IN_PROGRESS`, the actor is its assignee or a usable `MANAGER` or `ADMIN`, and a non-blank reason is given. The ticket goes back to the pool. The assignee cannot be taken away silently: every reassignment and every release writes one `ticket_assignment_history` row and one `operation_logs` row, in the same transaction.

**When an assignee's account stops being usable.** When a user is disabled or soft-deleted, every `IN_PROGRESS` ticket assigned to them goes back to `OPEN` automatically, in the same transaction as the disabling or the soft deletion. Each one gets a history row with `changed_by` the SYSTEM identity and a fixed reason, and an `operation_logs` row. Tickets that are already `RESOLVED` keep their assignee. If the user is enabled again, the tickets do not come back, because someone else may have taken them.

#### Linked inventory

`ticket_inventory` records which stock the ticket is about. One ticket can link to many inventory records, and an inventory record can appear on many tickets. A ticket with no link is valid, for example an order exception or a general problem.

- A link is unique per ticket and inventory record (`UK(ticket_id, inventory_id)`).
- **Links are written only when the ticket is created**, in the same transaction as the ticket, so one ticket can cover a batch of products from the start. They are never added later. A new problem is a new ticket. If the new problem is unrelated, it is a plain new ticket, and it does not name the earlier product. If it relates to an earlier ticket, it is a follow-up ticket (see Follow-up tickets). The table is append-only, so the application account has only `SELECT` and `INSERT` on it.
- The service layer should ask for at least one link on an `INVENTORY_EXCEPTION` ticket, because that is the point of the link, and may allow any ticket to have links. The database does not require it, because a `CHECK` cannot look at another table.
- The inventory record is the target, as in `inventory_transactions`, and the product is reached through it.
- Because links come only with the ticket, a ticket that is `CLOSED` or `CANCELLED` has the links it was created with, and nothing else about links can change.

#### Follow-up tickets

`tickets.follows_ticket_id` is empty for an ordinary ticket. When a problem continues after a ticket was resolved, the person opens a new ticket and chooses the earlier one, and the new ticket records it. This keeps the history of one problem connected across tickets without reopening any of them.

- The earlier ticket must be `RESOLVED` or `CLOSED`. A ticket that is still `OPEN` or `IN_PROGRESS` cannot be followed: an `OPEN` ticket takes a comment, and an `IN_PROGRESS` ticket stays with its assignee, and only a `MANAGER` or an `ADMIN` can hand it to someone else (see Reassigning and releasing a ticket). A `CANCELLED` ticket was withdrawn, so nothing follows it.
- The link is set when the new ticket is created and never changes. A ticket follows at most one earlier ticket, and an earlier ticket can have many follow-ups. A follow-up can itself be followed, so a chain forms.
- The link is visible to everyone who can view tickets, in both directions: a follow-up shows the ticket it follows, and an earlier ticket lists its follow-ups. It is visible while the new ticket waits in the pool, while it is worked on, and after it ends.
- The database rejects a link to a ticket that does not exist, and a ticket that is followed cannot be deleted. A ticket cannot follow itself, because the link is written when the ticket is created and the ticket does not exist yet.
- The follow-up is an ordinary ticket in every other respect: its own number, assignee, status, and resolution.

#### Archive

`CLOSED` is the archive. A ticket is archived in three ways. A `MANAGER` or an `ADMIN` can archive a `RESOLVED` ticket at any time, one by one or in a batch. The system archives a `RESOLVED` ticket 7 days after it was resolved. The system also archives when 99 resolved tickets are waiting in the whole system: when the number of `RESOLVED` tickets reaches 99, it archives all of the `RESOLVED` tickets. The two numbers are operating parameters. The system archives through the same status gate, ticket by ticket, with the SYSTEM identity as `closed_by`. The archive has its own screen, which lists `CLOSED` tickets and is open to every role that can view tickets, `VIEWER` included.

A batch archive and the system job move each ticket in its own transaction through the status gate, so a ticket changed by someone else in the meantime is skipped and the rest still succeed. The system job runs on a schedule and checks both the 7-day rule and the count of 99. A separate approval module for managers and administrators is a later idea (see [future-ideas.md](../future-ideas.md)), and nothing here depends on it.

#### Comments

Ticket comments are append-only. A comment is never edited or deleted, so `ticket_comments` has no `updated_at`. A mistake is corrected by adding a new comment that states the correction. That comment sets `corrects_comment_id` to the comment it corrects, so the screen can show the two together while the original stays unchanged. The field is empty for ordinary comments. A comment may be corrected more than once, and every correction points at the original comment, not at an earlier correction. The foreign key is the pair (`ticket_id`, `corrects_comment_id`) to (`ticket_id`, `id`), backed by a unique key on (`ticket_id`, `id`), so the database rejects a correction that points at a comment of another ticket.

### Auditing

| Table | Responsibility |
| --- | --- |
| `operation_logs` | Records important business actions, actors, target objects, and relevant state changes |

The initial version uses a generic target reference. `target_type` and `target_id` identify an order, inventory record, ticket, or another business object. The application maintains this polymorphic relationship because it cannot be represented by one conventional foreign-key constraint. `before_data` and `after_data` store the changed state as JSON. `operator_id` is empty for system actions.

#### Role management

Role definitions are managed by `ADMIN` only, and every operation here is also subject to Administrator protection and to Authority over roles and accounts. Every operation below writes its `operation_logs` row in the same transaction as the change, and the operation rolls back if the log insert fails. The formats are in Role management logs.

| Operation | Rule |
| --- | --- |
| Create a role | Inserts a `roles` row. `code` must be new, because it is unique. |
| Update a role | `ADMIN` may change `name` and `description`. `code` never changes after creation, because the logs and permission checks refer to it. |
| Delete a role | A physical delete of the `roles` row. A user who holds the role is not an obstacle: in the same transaction the service layer deletes every `user_roles` row of the role and writes one `ROLE_REVOKED` row for each, with `role deleted` in `remark`, and then deletes the role. Those users lose only this role. If a user has no other role, the user is left with no role, and no default role is given. The administrator must see how many users are affected before confirming. The `ADMIN` role can never be deleted. The service layer deletes the grants itself and does not rely on a cascading foreign key, because a cascade would skip the log rows. |
| Replace a user's roles | Sets the user's roles to a given list in one transaction. The user must be a usable account, and every role that is added must exist. Removing `ADMIN` from a user is subject to Administrator protection. Roles the user already holds and that stay in the list are left alone and not logged. Each role that is removed writes `ROLE_REVOKED`, and each role that is added writes `ROLE_GRANTED`. The rows of one replacement carry `replace roles` in `remark`. A list equal to the current roles changes nothing and writes no log. |

#### Authority over roles and accounts

Four roles are seeded: `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`. `ADMIN` and `MANAGER` are **protected roles**: their codes are fixed, they can never be deleted by anyone, and their `code` never changes. Every other role, including any role created later, is **below manager**.

`ADMIN` is the highest authority and is meant for major changes only, for the team that builds and maintains the system. `MANAGER` is the role for the customer's own staff: it manages employees and does the daily work. That is a working convention, not a restriction: an `ADMIN` account can do everything a `MANAGER` can.

| Action | ADMIN | MANAGER |
| --- | --- | --- |
| Create, update, or delete a role | Yes | **No**, `MANAGER` has no authority over role definitions at all |
| Grant, revoke, or replace `ADMIN` or `MANAGER` on a user | Yes | No |
| Grant, revoke, or replace roles below manager | Yes | Yes |
| Change the status of, or soft-delete, a user who holds `ADMIN` or `MANAGER` | Yes | No |
| Change the status of, or soft-delete, any other user | Yes | Yes |
| Products, inventory, orders, and tickets | Yes | Yes. See Permission Matrix |
| Delete or edit audit logs | No one | No one |

How the rules are applied:

- The service layer checks the acting user's roles and the target user's roles in the same transaction as the change. The check counts a role only when the acting account is a usable account.
- A manager cannot change a user who holds `ADMIN` or `MANAGER`, which includes the manager's own account. Without this, a manager could disable an administrator or another manager, or remove their own limits.
- When a manager replaces a user's roles, the list may not add or remove `ADMIN` or `MANAGER`.
- Only `ADMIN` can appoint or dismiss a manager or an administrator. Administrator protection still applies to every operation on `ADMIN`.
- Nobody can delete or edit audit logs, `ADMIN` included, because the application account has only `SELECT` and `INSERT` on `operation_logs`. The database enforces this, not the service layer.

#### Users without roles

A user with no `user_roles` row is a valid state, and the database does not require at least one role. OpsFlow is an internal program: apart from the first administrator, accounts are created through the normal sign-up flow of a web application. The user sets their own password, can log in at once, and starts with `status` `ACTIVE` and no role. Nothing is allowed until an administrator or a manager grants a role. A user can also end up with no role when the last role is revoked, when a replacement list is empty, or when a role is deleted. A permission check returns no permissions for such a user.

After every login the user first lands on a pending page that checks the account, in this order:

| Check | What the page shows |
| --- | --- |
| `status` is `DISABLED` | A message that the account is disabled |
| The user has no role | A message that the account is waiting for an administrator to assign permissions |
| The user has at least one role | Nothing: the page is skipped and the application opens normally |

The check runs again on refresh and on the next login, so a user whose role was granted in the meantime, or before logging in, goes straight in. This is a backend and frontend behavior, not a database rule.

#### First administrator and forced password change

A new user has no role and only an administrator or a manager can grant one, and the first manager is appointed by an administrator, so the first administrator is created by the program itself at startup. If no usable account holds `ADMIN`, the program reads the username, email, and password from the environment variables `OPSFLOW_BOOTSTRAP_ADMIN_USERNAME`, `OPSFLOW_BOOTSTRAP_ADMIN_EMAIL`, and `OPSFLOW_BOOTSTRAP_ADMIN_PASSWORD`. If one is missing, startup fails with a message that names it. Otherwise one transaction hashes the password, creates the user with `must_change_password` set to true, grants `ADMIN`, and writes a `ROLE_GRANTED` row with an empty `operator_id`, because it is a system action. The `ADMIN` role must already exist, so this runs after the roles are seeded. When a usable administrator already exists, the variables are ignored. If the system ever has none, the next startup with the variables set creates one again. The password is never written to a file.

`users.must_change_password` is a boolean that defaults to false. It is used only for the first administrator. While it is true, the user can log in but can use nothing except changing the password, and the application sets it to false when the password has been changed. The bootstrap administrator always starts with it true, because the initial password was typed by the person who set up the server and may remain in a shell history. Every other account sets its own password when it signs up, so it stays false and no password change is forced.

#### Login security and account recovery

**Lockout after wrong passwords.** Failures are counted per account, in `users.failed_login_count`, and the count escalates every five failures:

| Failures in a row | Lock |
| --- | --- |
| 5 | 1 minute |
| 10 | 5 minutes |
| 15 | 15 minutes |
| 20 | Until midnight |

- Failures in a row means since the last successful login, password reset, or midnight reset. Each wrong password updates `failed_login_count` and `last_failed_login_at` in one statement on the locked user row, and sets `locked_until` when a threshold is reached.
- While `locked_until` is in the future, a login attempt is refused without checking the password, and the attempt is not counted. The refusal says when to try again.
- The count resets at midnight. Midnight is taken in Sydney time (`Australia/Sydney`), set in the application configuration, while all times stay stored in UTC. Daylight saving is handled by the time zone rules, so a day may be 23 or 25 hours long. The reset is lazy: when `last_failed_login_at` is before the most recent midnight, the count is treated as zero.
- A successful login and a password reset clear the count and `locked_until`.
- A login error never says whether the username exists.
- The lock is per account only. Anyone who knows a username can lock that account, and its owner gets back in through password recovery. Limiting attempts per address belongs with Q-07.

**Email codes.** Sign-up and password recovery both use a one-time code sent to the email address, stored in `email_verification_codes`.

- A code has 6 digits and is stored hashed. It is valid for 10 minutes, accepts at most 5 wrong entries, and a new code for the same email and purpose cannot be requested within 60 seconds. Requesting a new code marks the earlier unused ones consumed, so only the latest code works. A correct entry sets `consumed_at`.
- **Sign-up**: the user requests a code for an email address that is not in `users` (soft-deleted users count, because their values stay reserved), enters the code, and then the account is created with `status` `ACTIVE`, no role, and `must_change_password` false.
- **Recovery**: the user requests a code for the email of an existing, not soft-deleted account, and enters it before choosing a new password. The response is the same whether or not the email is registered. The reset sets `password_hash` and `password_changed_at`, and clears the lock fields.
- The application account may insert and update code rows but not delete them. Old rows are cleaned up later, when their volume makes it worth a privilege (Future Evolution).

**Token checks.** A login token carries only the user id and its issue time. On every request the service layer loads the user and the user's roles from the database. It refuses the request when the account is not a usable account, or when the token was issued before `users.password_changed_at`. So disabling an account, changing its roles, and resetting its password all take effect on the next request, and a stolen token stops working after a password reset. The token still has an expiry; its length is chosen when authentication is built.

#### Administrator protection

The system must always have at least one usable account that holds the `ADMIN` role. This rule is fixed and has no override: no operation, by anyone including an administrator, may leave the system without one. A new administrator must be made before the only existing one can be changed.

- **Covered operations**: disabling a user, soft-deleting a user, revoking `ADMIN` from a user, replacing a user's roles so that `ADMIN` is removed, and deleting a role. The `ADMIN` role itself can never be deleted, and its `code` never changes.
- **How it is checked**: in the same transaction as the change, the service layer applies the change, counts the usable accounts that hold `ADMIN`, and rolls back with a clear message if the count is zero.
- **Concurrency**: every covered operation first locks the `ADMIN` row in `roles` with `SELECT ... FOR UPDATE`, then counts. Without the lock, two administrators could remove each other at the same moment, each seeing the other still there, and both would pass.
- **Limit**: the rule lives in the service layer. Direct SQL run as `root` or the migration account does not pass through it.

#### Role grant and revocation logs

`user_roles` keeps no history, so `operation_logs` is the only record of who held which role and when. Each grant or revocation writes one log row in the same transaction as the change to `user_roles`. If the log insert fails, the grant or revocation rolls back with it.

| Field | Grant | Revocation |
| --- | --- | --- |
| `action` | `ROLE_GRANTED` | `ROLE_REVOKED` |
| `target_type` | `USER` | `USER` |
| `target_id` | `users.id` of the user who receives or loses the role | the same |
| `operator_id` | The administrator who made the change. Empty only for system actions such as seeding. | the same |
| `before_data` | Empty | `{"roleId": 2, "roleCode": "OPERATOR"}` |
| `after_data` | `{"roleId": 2, "roleCode": "OPERATOR"}` | Empty |

The log stores `roleCode` as well as `roleId`, so it stays readable if the role row is removed later. Removing a role means revoking every grant of it first, and each revocation is logged, so the logs still show who held the role.

#### Role management logs

These follow the same pattern as grants and revocations: `target_type` is `ROLE`, `target_id` is `roles.id`, and `operator_id` is the administrator.

| Operation | `action` | `before_data` | `after_data` |
| --- | --- | --- | --- |
| Create | `ROLE_CREATED` | Empty | `{"roleCode": "AUDITOR", "name": "Auditor", "description": "Read-only audit access"}` |
| Update `name` or `description` | `ROLE_UPDATED` | The changed fields before, for example `{"name": "Auditor"}` | The changed fields after, for example `{"name": "Audit reader"}` |
| Delete | `ROLE_DELETED` | All fields of the row: `roleCode`, `name`, `description` | Empty |

Deleting a role also writes one `ROLE_REVOKED` row for each user who held it, in the v1.3 format with `target_type` `USER`, before the `ROLE_DELETED` row.

`ROLE_UPDATED` stores only the fields that changed. It puts `roleCode` in `remark`, so a log row stays readable after the role is deleted. Replacing a user's roles uses the `ROLE_GRANTED` and `ROLE_REVOKED` formats above, with `target_type` `USER`.

#### Ticket logs

Every ticket action writes one `operation_logs` row in the same transaction as the change. `target_type` is `TICKET`, `target_id` is `tickets.id`, and `operator_id` is the acting user, or empty when the system acts, as for every system action. The reason goes in `remark`. `before_data` and `after_data` hold only the fields that changed. Every change of assignee also writes a `ticket_assignment_history` row. Comments are not logged here, because `ticket_comments` is their own record.

| Action | `action` | `before_data` | `after_data` |
| --- | --- | --- | --- |
| Open a ticket | `TICKET_CREATED` | Empty | `{"type": "INVENTORY_EXCEPTION", "source": "MANUAL", "createdBy": 7, "followsTicketId": null, "orderId": null, "inventoryIds": [3, 5]}`. The links are part of this row, because they are written with the ticket. |
| Take, or assign from the pool | `TICKET_ASSIGNED` | `{"status": "OPEN"}` | `{"status": "IN_PROGRESS", "assigneeId": 9}`. `remark` says `taken` or `assigned`. |
| Reassign | `TICKET_REASSIGNED` | `{"assigneeId": 9}` | `{"assigneeId": 12}`. `remark` is the reason. |
| Release, remove the assignee, or the system returns the ticket | `TICKET_RELEASED` | `{"status": "IN_PROGRESS", "assigneeId": 9}` | `{"status": "OPEN"}`. `remark` is the reason, or the fixed reason of the system. |
| Resolve | `TICKET_RESOLVED` | `{"status": "IN_PROGRESS"}` | `{"status": "RESOLVED", "resolvedBy": 9}`. The resolution text stays in `tickets.resolution`. |
| Archive | `TICKET_CLOSED` | `{"status": "RESOLVED"}` | `{"status": "CLOSED", "closedBy": 4}`. `remark` is `manual`, `batch`, or `auto archive after 7 days`. |
| Cancel | `TICKET_CANCELLED` | `{"status": "IN_PROGRESS"}` | `{"status": "CANCELLED", "cancelledBy": 9}`. `remark` is the reason. |

These formats are a first version. They can change in detail when the audit screen is designed (Q-03).

## Relationship Summary

| Source | Cardinality | Target | Description |
| --- | --- | --- | --- |
| `users` | M:N | `roles` | Implemented through `user_roles` |
| `users` | 1:N | `orders` | A user creates multiple orders |
| `orders` | 1:N | `order_items` | An order contains at least one item |
| `products` | 1:N | `order_items` | Each item references one product |
| `products` | 1:1 | `inventory` | Each product has one current inventory record in the initial version |
| `inventory` | 1:N | `inventory_transactions` | An inventory record produces multiple transactions |
| `orders` | 0..1:N | `inventory_transactions` | Reserve, confirm, and release transactions reference their order |
| `users` | 0..1:N | `inventory_transactions` | A user may perform manual or confirming operations; system actions have no user |
| `orders` | 0..1:N | `tickets` | An order can produce multiple tickets for separate exceptions |
| `users` | 1:N | `tickets` | A user creates tickets |
| `users` | 0..1:N | `tickets` | A user is the current assignee of, or resolves, multiple tickets |
| `tickets` | 0..1:N | `tickets` | A ticket may follow one earlier ticket (`follows_ticket_id`); an earlier ticket can have many follow-ups |
| `users` | 0..1:N | `tickets` | A user may be recorded as the one who closed (`closed_by`) or cancelled (`cancelled_by`) a ticket |
| `tickets` | 1:N | `ticket_assignment_history` | A ticket retains its complete assignment history |
| `users` | 1:N | `ticket_assignment_history` | A user appears as the previous assignee, new assignee, or acting user |
| `tickets` | 1:N | `ticket_comments` | A ticket contains processing notes and communication |
| `tickets` | M:N | `inventory` | Implemented through `ticket_inventory`; a ticket can cover a batch of stock records |
| `ticket_comments` | 1:N | `ticket_comments` | A correction comment points at the comment it corrects, on the same ticket |
| `users` | 0..1:N | `operation_logs` | A user performs multiple audited operations; system actions have no user |

## Order and Inventory Flow

### Create order

1. An authenticated user submits an order containing one or more products and optionally an idempotency key.
2. If the key already exists for this user, the system returns the original order without changing anything. If two identical submissions race, the second insert violates `UK(user_id, idempotency_key)`; it rolls back and returns the existing order.
3. The system validates that each product exists, is available for sale, and has a valid requested quantity.
4. Within one database transaction, the system:
   1. creates `orders` with status `CREATED` and a `reserved_until` deadline;
   2. creates the related `order_items`;
   3. locks the `inventory` row of every item with `SELECT ... FOR UPDATE`, in ascending inventory id, and reads the current quantities;
   4. checks that `available_quantity >= q` for every item. If any item is short, the whole transaction rolls back and the caller receives an out-of-stock result. No order is created and no stock changes, even when other items in the same order had enough stock;
   5. for each item, decreases `available_quantity` and increases `locked_quantity` by `q`, and inserts a `RESERVE` row into `inventory_transactions` whose snapshots come from the values read in step 3;
   6. writes the required `operation_logs`.
5. The transaction commits only when every step succeeds.

### Confirm order

An operator or a confirmation endpoint completes the order.

1. In one transaction, run the status gate: update the order to `COMPLETED` and set `finished_at` with `WHERE id = ? AND status = 'CREATED' AND reserved_until > NOW()`.
2. If one row was updated, the order is confirmed. Lock the `inventory` rows of its items in ascending inventory id, check `locked_quantity >= q`, decrease `locked_quantity` and increase `sold_quantity` by `q`, and insert a `CONFIRM` transaction for each item. Write `operation_logs` and commit.
3. If zero rows were updated, read the order and decide by what it shows. Zero rows does not by itself mean another request got there first.

| Order state | Meaning | Action |
| --- | --- | --- |
| Does not exist | Invalid request | Return not found |
| Final status (`COMPLETED`, `CANCELLED`, `FAILED`) | Already processed | Return the current result. Do not touch inventory. |
| `CREATED` and `reserved_until` has passed | The reservation expired | Run the cancel path with status `CANCELLED` and reason `RESERVATION_EXPIRED`, commit it, and return an "expired and cancelled" result |
| `CREATED` and `reserved_until` has not passed | Another transaction changed the order between the gate and the read | Retry from step 1 |

An expired confirmation releases the stock immediately instead of waiting for the scheduled job. That cancellation is committed, not rolled back.

### Cancel, time out, or fail an order

Cancellation by a user or operator, timeout, and definite business failure share one path.

1. In one transaction, run the status gate: update the order with `WHERE id = ? AND status = 'CREATED'`, setting the target status (`CANCELLED` or `FAILED`), `finished_at`, and `status_reason`.
2. If zero rows were updated, the order is already final. Stop without touching inventory and return the current result.
3. Otherwise, lock the `inventory` rows of the order's items in ascending inventory id, check `locked_quantity >= q`, decrease `locked_quantity` and increase `available_quantity` by `q`, and insert a `RELEASE` transaction for each item.
4. Write `operation_logs` and commit.

A scheduled job runs every minute, finds up to 100 orders with `status = 'CREATED'` and `reserved_until` in the past, and cancels each one in its own transaction. Concurrent job runs are safe because only the transaction that wins the status update releases stock.

Transient database or interface errors must roll back the transaction and leave the order `CREATED` so the operation can be retried. They must never set `FAILED`. `FAILED` is reserved for a definite business outcome.

### Exception path

When something goes wrong while an order is processed and the cause needs a person, the program stops the order and opens the ticket together. An order that went wrong does not stay `CREATED`, because then its stock would stay reserved, an operator could act on it again, and a repeated failure could open a second ticket.

1. The path runs for the two automatic cases only: the retries are exhausted, or the stock and order records disagree. It starts after the failed transaction has been fully rolled back, in a new transaction.
2. The first statement is the status gate on the order: set `status` to `CANCELLED`, `finished_at`, and `status_reason` to `EXCEPTION_TICKET:` followed by the ticket number, `WHERE id = ? AND status = 'CREATED'`. If zero rows are updated, someone else has already handled the order, and the program writes nothing and opens no ticket.
3. Otherwise the program inserts the ticket as the SYSTEM identity (see Exceptions), releases the reserved stock through the normal cancel path (`RELEASE` transactions), links the inventory records involved, writes the `operation_logs` rows, and commits. A failure of any step rolls the whole transaction back.
4. **When the records disagree**, the program does not release the stock, because the numbers cannot be trusted. The order is still stopped and the ticket is still opened, and the stock is left as found. The person who handles the ticket corrects it with a manual stock change whose reason names the ticket.
5. The order is now `CANCELLED`, which is final. Nobody can confirm, cancel, or process it again, and a second failure cannot reach it. `FAILED` is not used: a transient error still never sets `FAILED`, and an order that reaches this path has already run out of retries.
6. **Waiting to be handled.** An order whose `status_reason` starts with `EXCEPTION_TICKET:` is shown as waiting to be handled while its ticket is `OPEN` or `IN_PROGRESS`, and is shown as cancelled once the ticket is `RESOLVED`, `CLOSED`, or `CANCELLED`. The state is derived from `tickets.order_id` and the ticket status, so there is no new order status. The order and the ticket point at each other: the ticket by `order_id`, the order by the ticket number in its reason.
7. If the order should still go ahead, a new order is created. Submitting the old request again with the same idempotency key returns the cancelled order, so the new order needs a new key.
8. If this transaction cannot commit at all, for example because the database is down, nothing is recorded. The order stays `CREATED` and the timeout job cancels it later. The failure is written to the application log.

### Concurrency and duplicate protection

- **Strategy: pessimistic row locking.** Every stock change locks the affected `inventory` rows with `SELECT ... FOR UPDATE`, reads the quantities, validates, updates, and writes the `inventory_transactions` rows, all in one transaction. A plain `SELECT` must not be used to read quantities for a later update, because another transaction can change them in between and the snapshots would be wrong. There is no version counter on `inventory`, and optimistic locking is not used, so the two strategies are never mixed.
- **Lock order.** Every flow locks the `orders` row first (by inserting it or by running the status gate), then the `inventory` rows in ascending inventory id. A fixed order lowers the chance of deadlock but does not remove it.
- **Deadlock and lock-wait retry.** If the database reports a deadlock or a lock wait timeout, the transaction is rolled back completely and retried in a new transaction, up to the attempt limit in Operating Parameters. MySQL rolls back the whole transaction on a deadlock, but a lock wait timeout may roll back only the failed statement, so the application must explicitly roll back before it retries. Retries are safe because the status gate and the unique constraints below prevent a repeated operation from taking effect twice.
- **Status gate.** The conditional status update is the single gate for every transition. Because only one transaction can move an order out of `CREATED`, confirm and cancel can never both succeed, and repeated requests cannot deduct or release stock twice.
- **Constraint backstop.** `CHECK` constraints keep stock quantities non-negative, so a logic error fails instead of corrupting stock.
- **Manual stock changes** use the same row lock and transaction pattern as orders (see Manual stock changes). A manual change and an order on the same product wait for each other on the `inventory` row, so neither can overwrite the other's quantity. A direct set also checks the value the user saw, so a stale screen cannot silently undo a reservation. This is a check made while the row is locked, not an optimistic version counter.
- **Duplicate inventory transactions.** `UK(order_id, inventory_id, type)` on `inventory_transactions` is a second line of defense. A duplicate `RESERVE`, `CONFIRM`, or `RELEASE` for the same order and inventory record violates the constraint and rolls back. Stock-level types have no order, and the constraint does not apply to them, so repeated restocks are allowed.
- **Duplicate orders.** `UK(user_id, idempotency_key)` prevents duplicate order creation from repeated submissions. It is a composite key, not a unique key on `idempotency_key` alone.
- **Time source.** Compare `reserved_until` with the database clock, not with application server clocks.

### Exceptions

Situations that need manual investigation create `tickets`, which then move through assignment, investigation, reassignment, and resolution. In the first version, only cases that need a person create a ticket automatically:

| Creates a ticket | Does not create a ticket |
| --- | --- |
| Stock and order records are inconsistent | Insufficient stock |
| Retries are exhausted and the cause needs investigation | Duplicate or repeated requests |
| | Normal cancellation |
| | Normal reservation timeout |

An automatic ticket is opened by the SYSTEM identity with `source` `SYSTEM`, as `OPEN` in the pool, with no assignee. Anyone who can take tickets may take it. Both automatic cases arise while an order is being processed, so the program traces the problem at the moment it opens the ticket: it always fills `order_id` with the order that went wrong, it writes the `title` and the `description` itself, and it links the inventory records involved. The program also stops the order in the same transaction (see Exception path).

| Case | `type` | What the program fills in |
| --- | --- | --- |
| Stock and order records are inconsistent | `INVENTORY_EXCEPTION` | The order (`order_id`), the inventory records that disagree (`ticket_inventory`), and a description of what was found: for each product the quantity the order expects, the quantity the stock shows, and the step that was running |
| Retries are exhausted and the cause needs investigation | `ORDER_EXCEPTION` | The order (`order_id`), and a description of what was being done, the error, and the number of attempts. It links the inventory records of the order's items. |

## Permission Matrix

This section says what each seeded role may do in each module. It is a first version, kept as it stands for now and expected to be refined before the permission module is built. It sits on top of Authority over roles and accounts, which says who may change roles and accounts. A role that is not allowed an action is refused with 403, and the frontend hides the control.

Principles:

- **Deny by default.** An action that is not listed is refused. A user with no role can do nothing (see Users without roles).
- **`VIEWER` is read-only.** It never changes data in any module.
- **`OPERATOR` runs the day-to-day flow**: orders, stock in, write-offs, and tickets. It does not change master data, set stock quantities directly, or see audit logs, user lists, or role lists.
- **`MANAGER` can do everything `OPERATOR` can, plus master data, setting stock quantities directly, user and role assignment (below manager), and audit logs.**
- **`ADMIN` can do everything `MANAGER` can, plus the actions reserved for it in Authority over roles and accounts.**
- A role counts only when the acting account is a usable account. System actions, such as the timeout job and automatic tickets, have no acting user and are not covered by this matrix.

| Module | Action | ADMIN | MANAGER | OPERATOR | VIEWER |
| --- | --- | --- | --- | --- | --- |
| Products | View | Yes | Yes | Yes | Yes |
| Products | Create, edit, change price, put on or off sale, soft-delete | Yes | Yes | No | No |
| Inventory | View stock and stock transactions | Yes | Yes | Yes | Yes |
| Inventory | Stock in (`RESTOCK`), reason required | Yes | Yes | Yes | No |
| Inventory | Write-off or stock out (`WRITE_OFF`), reason required | Yes | Yes | Yes | No |
| Inventory | Set the quantity directly (`ADJUST_IN`, `ADJUST_OUT`), reason required | Yes | Yes | No | No |
| Orders | View | Yes | Yes | Yes | Yes |
| Orders | Create | Yes | Yes | Yes | No |
| Orders | Confirm | Yes | Yes | Yes | No |
| Orders | Cancel | Yes | Yes | Yes | No |
| Tickets | View, including the archive screen of `CLOSED` tickets, the cancelled area of `CANCELLED` tickets, the follow-up links, and the assignment history | Yes | Yes | Yes | Yes |
| Tickets | Create manually, with the inventory records it concerns (links are written only at creation), or as a follow-up to a `RESOLVED` or `CLOSED` ticket | Yes | Yes | Yes | No |
| Tickets | Add a comment or a correction | Yes | Yes | Yes | No |
| Tickets | Take a ticket from the pool (it becomes `IN_PROGRESS` at once), if an eligible assignee | Yes | Yes | Yes | No |
| Tickets | Assign a ticket from the pool, or reassign an `IN_PROGRESS` ticket, to an eligible assignee, with a reason (the only way to take a ticket from its assignee) | Yes | Yes | No | No |
| Tickets | Release a ticket back to the pool, with a reason | Yes, any | Yes, any | Only their own (assigned to them) | No |
| Tickets | Move to `RESOLVED` | Yes | Yes | Only a ticket assigned to them | No |
| Tickets | Cancel an `OPEN` or `IN_PROGRESS` ticket, with a reason | Yes, any | Yes, any | Only their own (assigned to them, or created by them while `OPEN`) | No |
| Tickets | Archive a `RESOLVED` ticket (move to `CLOSED`) at any time, singly or in a batch. The system also archives after 7 days. | Yes | Yes | No | No |
| Tickets | Reopen a ticket | No one | No one | No one | No one |
| Audit logs | View | Yes | Yes | No | No |
| Audit logs | Edit or delete | No one | No one | No one | No one |
| Users | View the user list | Yes | Yes | No | No |
| Users | Change status, soft-delete | See Authority over roles and accounts | See Authority over roles and accounts | No | No |
| Roles | View the role list | Yes | Yes | No | No |
| Roles | Create, update, delete, appoint managers and administrators | Yes | No | No | No |
| Roles | Assign roles below manager | Yes | Yes | No | No |
| Own account | View it, change own password | Yes | Yes | Yes | Yes |

Where the permissions live: for the four seeded roles, they are fixed in the application code and keyed by role code. A role created later by an administrator has no permissions until a mapping is added, so creating one grants nothing by itself. This is intended: the system is built for customers, and any new capability goes through the backend developers, so a new role gets its permissions together with the code that implements them.

Several entries are working defaults and can be changed in this table before the permission module is built: `OPERATOR` may cancel any order and not only its own, `OPERATOR` may stock in and write off but not set the quantity directly, only `ADMIN` and `MANAGER` may archive a ticket or reassign it to someone else, any role that works on tickets may cancel an `OPEN` or `IN_PROGRESS` ticket whoever owns it, and an `OPERATOR` may move only their own tickets forward.

## Concurrency by Module

Order and inventory concurrency is specified under Order and Inventory Flow. This section applies the same thinking to every other module, because any two people can act at the same moment. Each rule below is a working default for the service layer. It is checked against the real code when the module is built, and the order and inventory checks in `db/verification` are the model for how.

**Principles**

- A write that depends on something the program just read must be protected by one of four things: a row lock (`SELECT ... FOR UPDATE`, or `FOR SHARE` for a check that must not change), a conditional update (`WHERE id = ? AND status = ?`), an atomic update (`SET n = n + 1`), or a unique constraint. A plain read followed by a write is never enough.
- A unique constraint is the last line of defense. It does not replace the check the program makes first.
- When one transaction locks several kinds of rows, it locks them in this order: `users` (ascending id), then `roles`, then `tickets`, then `orders`, then `inventory` (ascending id). A fixed order lowers the chance of deadlock but does not remove it, so every module uses the deadlock and lock-wait retry in Concurrency and duplicate protection.
- A transaction that holds locks makes no call to anything outside the database, such as sending an email.
- Every audit log row is written in the same transaction as the change it describes.

| Module | What can race | Rule |
| --- | --- | --- |
| Products | Two managers edit the same product | Lock the product row, update only the fields the user changed, and log `before_data` and `after_data`. If both changed the same field, the later commit wins and the log shows both. |
| Products | A product is edited while an order is created | Order creation reads the status, price, SKU, and name of each product in one statement and snapshots them. It takes no lock on `products`. An order that saw the product on sale is valid even if the product is put off sale a moment later, and its price is never a mix of two values. |
| Products | Two products get the same SKU | The unique constraint rejects the second, and the program returns a conflict. |
| Inventory | Manual changes, orders, and timeouts on the same stock | Row lock on `inventory`, as specified under Manual stock changes and Concurrency and duplicate protection. |
| Orders | Confirm, cancel, timeout, repeated submission | Status gate, row locks, and unique keys, as specified under Order and Inventory Flow. |
| Orders and tickets | The exception path runs twice for one order, or meets a confirm, a cancel, or the timeout job | The first statement is the status gate on the order, `WHERE id = ? AND status = 'CREATED'`. Only the transaction that moves the order out of `CREATED` goes on to open the ticket and release the stock. The others see zero rows, write nothing, and open no ticket, so one order never gets two tickets. The path takes the order row first and then inserts a brand-new ticket row. Nobody else can hold a new row, so this does not break the lock order. The foreign key from the ticket to the order needs only a shared lock on a row this transaction already holds exclusively. |
| Tickets | Two people take the same ticket from the pool, or a take meets an assignment | `UPDATE ... SET assignee_id = ?, status = 'IN_PROGRESS' WHERE id = ? AND status = 'OPEN'`. Zero rows updated means someone else got it first. |
| Tickets | Two status changes, such as archive and cancel, at once | Every transition is a conditional update on the current status, so only one succeeds. The other sees zero rows, reads the ticket, and reports its current state. |
| Tickets | Two managers reassign the same ticket, the assignee releases it as a manager reassigns it, or a reassignment meets a cancel or a resolve | Lock the ticket row, check the status is `IN_PROGRESS`, and compare the current assignee with the one the actor saw on the screen. If it differs, refuse with "ticket changed, please reload", so one change cannot silently overwrite another. Then update `assignee_id` and insert the `ticket_assignment_history` row, all in one transaction. |
| Tickets | A ticket is taken, assigned, or moved in while the person's account is disabled, loses its role, or is soft-deleted, or two tickets reach a person at 98 in progress | Before the change, lock that person's user row with `FOR UPDATE`, re-check that they are an eligible assignee, and count the tickets assigned to them that are `IN_PROGRESS`. Refuse at 99. The exclusive lock makes two simultaneous requests for the same person wait for each other, so the second one sees the first and cannot slip past the limit. Disabling a user or changing roles needs the same row, so one waits for the other. Lock order: `users`, then `tickets`. |
| Tickets | Two tickets are created at once by a person at 98 open | Lock the creator's user row with `FOR UPDATE`, count the tickets they created that are `OPEN`, refuse at 99, then insert the ticket, all in one transaction. Creations by the same person wait for each other, and creations by different people do not. Lock order: `users`, then `tickets`. |
| Tickets | A comment arrives as the ticket is archived or cancelled | Lock the ticket row with `FOR SHARE`, check the status is not `CLOSED` or `CANCELLED`, then insert. The archive or cancel update needs exclusive access, so it waits for the insert or comes first and makes the check fail. Links are not a case, because they are written in the same transaction that creates the ticket. |
| Tickets | A batch archive, the system job (7 days, or 99 waiting), and a manager's archive or a cancel overlap | Each ticket is archived in its own transaction through the status gate, `WHERE id = ? AND status = 'RESOLVED'`. A ticket already moved is skipped. Running the same batch or the same job twice is safe. The count of 99 is read before the job starts and need not be exact: if a ticket is resolved or archived in the meantime, the job only archives a few more or fewer, and the next run corrects it. |
| Tickets | A follow-up is opened as the earlier ticket changes | No lock is needed. A ticket that is `RESOLVED` or `CLOSED` never goes back, so the permission to follow it cannot be lost. The foreign key rejects a ticket that does not exist. |
| Tickets | A user is disabled or soft-deleted while their tickets are released, resolved, or taken | Disabling or deleting locks the user row exclusively, then sets every `IN_PROGRESS` ticket of that user back to `OPEN` in the same transaction, each by a conditional update on the status and the assignee, and writes the history and log rows. A take or an assignment for that user waits for the same row and then fails the eligibility check. A release or a resolve by the user at the same moment is a conditional update on the status, so whichever commits first wins and the other sees zero rows. Lock order: `users`, then `tickets`. |
| Users and roles | Grant, revoke, replace, or disable at once, including two managers on the same user | Lock the target user's row first, then check the user is usable and the actor has authority, then change. The second request waits and sees the first one's result. |
| Users and roles | The last administrator is removed twice at once | After the user row, lock the `ADMIN` row in `roles`, apply the change, and count usable holders, as under Administrator protection. |
| Users and roles | The same role is granted twice | `UK(user_id, role_id)` rejects the second, and the program treats it as already granted. |
| Users and roles | A role is granted while it is deleted | Deleting locks the role row and removes the grants. The grant's foreign key check needs a shared lock on the role row, so one waits for the other, and a grant after the delete fails. This order differs from the grant path, so a deadlock is possible, and the retry rule covers it. |
| Authentication | Two wrong passwords at once | `UPDATE users SET failed_login_count = failed_login_count + 1 ...` is atomic, and the lock time is worked out from the new value in the same transaction, so both attempts count. |
| Authentication | An email code is submitted twice at once | Consume it with `UPDATE ... SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL AND expires_at > ? AND attempts < 5`. Only one request updates a row. Wrong entries increase `attempts` atomically. |
| Authentication | A new code is requested while an older one is used | Inserting the new code and consuming the older ones is one transaction, so only the latest code works. Two requests at the same moment may both pass the 60-second check and both send a code. That is harmless, because only the latest code works. |
| Authentication | Two sign-ups with the same username or email | The unique constraints reject the second. |
| Authentication | Two instances start with no administrator | The startup creation locks the `ADMIN` row in `roles`, re-checks for a usable administrator, then creates one. The second instance finds it and does nothing. |

A new or changed write path states in this document, before it is built, what it reads, which of the four protections covers it, and where it falls in the lock order.

## Operating Parameters

These values belong in application configuration, not in the database. The database stores only the actual `reserved_until` of each order, so changing the configured duration does not affect existing orders.

| Parameter | Value | Reason |
| --- | --- | --- |
| Reservation duration | 30 minutes | Operators confirm orders and need time to process them |
| Timeout scan interval | 1 minute | Simple, and expired stock is usually released within about a minute |
| Orders per scan batch | 100 | Avoids processing too many at once; each order uses its own transaction |
| Attempts on deadlock or lock-wait timeout | 3 in total, including the first | At most two retries, so retrying never loops |
| Wait between retries | 50 ms, then 150 ms, each with a small random delay | Prevents conflicting requests from retrying at the same moment |
| Login lock steps | 5 failures: 1 minute; 10: 5 minutes; 15: 15 minutes; 20: until midnight | Slows guessing while giving a person who mistyped a quick way back |
| Failure count reset | Midnight in Sydney time (`Australia/Sydney`), configurable | A new day starts clean, on the clock the users live by |
| Open tickets created per person | 99 | A person cannot flood the board with tickets. It counts tickets the person created that are `OPEN`, and it applies to creating a ticket. |
| Tickets in progress per person | 99 | A person cannot hoard work. It counts tickets assigned to the person that are `IN_PROGRESS`, and it applies to taking, assigning, and receiving a ticket by reassignment. |
| Auto-archive delay | 7 days after `resolved_at` | A resolved ticket that nobody archived does not wait for ever. A `MANAGER` or an `ADMIN` can archive it earlier at any time. |
| Resolved tickets waiting before auto-archive | 99 in the whole system | When 99 `RESOLVED` tickets are waiting, the system archives all of them, so the waiting list stays short. |
| Archive job interval | 1 minute | The same simple schedule as the reservation timeout scan. The job checks the 7-day rule and the count of 99. |
| Email code | 6 digits, valid 10 minutes, 5 wrong entries, resend after 60 seconds | Long enough to be inconvenient to guess, short enough to be quick to use |

Retry only after the previous transaction has been fully rolled back, and run the retry in a new transaction.

## Field Specifications

The physical ERD shows generic types. The migration must use the types below. Keep API validation limits equal to the database lengths so that over-long input is rejected instead of being silently truncated.

### Numbers, money, and time

| Field | Type and rule |
| --- | --- |
| Product price, order item unit price | `DECIMAL(12,2)`, not negative |
| Order item subtotal, order total amount | `DECIMAL(14,2)`, not negative |
| Stock quantities, order quantity, transaction quantity | `INT`. Stock quantities are not negative. Order and transaction quantities are greater than zero. |
| Primary keys and foreign keys | `BIGINT`, with the same type on both sides of every relationship |
| Creation, update, expiry, and completion times | `DATETIME(3)`, stored in UTC |

Money is kept to two decimal places and calculated with Java `BigDecimal`.

### Strings

| Field | Type |
| --- | --- |
| `users.username` | `VARCHAR(64)` |
| `users.email` | `VARCHAR(254)` |
| `users.password_hash` | `VARCHAR(255)` |
| `email_verification_codes.email` | `VARCHAR(254)`, the same as `users.email` |
| `email_verification_codes.code_hash` | `VARCHAR(255)` |
| `products.sku`, `order_items.product_sku` | `VARCHAR(64)` |
| `products.name`, `order_items.product_name` | `VARCHAR(200)` |
| `orders.order_no`, `tickets.ticket_no` | `VARCHAR(64)` |
| `orders.idempotency_key` | `VARCHAR(128)` |
| Status and type fields, `roles.code` | `VARCHAR(32)`, restricted to the allowed values |
| `tickets.title` | `VARCHAR(200)` |
| `orders.status_reason`, `inventory_transactions.reason`, `ticket_assignment_history.reason`, `tickets.cancel_reason` | `VARCHAR(255)` |
| `remark` fields | `VARCHAR(500)` |
| `operation_logs.ip_address` | `VARCHAR(45)` |
| `operation_logs.action`, `operation_logs.target_type` | `VARCHAR(64)` |
| Product and ticket descriptions, `tickets.resolution`, `ticket_comments.content` | `TEXT` |
| `operation_logs.before_data`, `after_data` | `JSON` |

`orders.order_no`, `tickets.ticket_no`, and `orders.idempotency_key` use the `utf8mb4_0900_bin` collation, so they are compared exactly and `Abc` and `abc` are different values. `users.username`, `users.email`, `products.sku`, and `roles.code` keep the default case-insensitive, accent-insensitive collation, so two usernames that differ only in case count as the same.

`roles.name` and `roles.description` were not covered by the supplied values. Use `VARCHAR(64)` and `VARCHAR(255)` unless a reason to change appears.

### Time zone

Times are stored in UTC. `DATETIME` carries no time zone, and the defaults for `created_at` and `updated_at` use `CURRENT_TIMESTAMP(3)`, which follows the session time zone. Application code and the database must therefore follow one convention:

- Set the session time zone of every application connection to UTC. Changing only `serverTimezone` is not enough. For Connector/J, use `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- Have Java generate every time it writes, such as `reserved_until`, in UTC.
- Run `SET time_zone = '+00:00';` before any manual SQL that involves time.
- Convert to a local time zone only when displaying times in the user interface.

## Initial Migration

The first executable migration is [`db/migration/V1__initial_schema.sql`](../../db/migration/V1__initial_schema.sql). It creates all 14 tables for MySQL 8.0.16 or later, using the types in Field Specifications. It creates structure only. The roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER` are added by a separate seed migration when the permission module is implemented, so an empty `roles` table is expected for now. Never edit a migration that has been applied. Add a new one.

The first application of this migration is done by Flyway, when the backend is connected to Flyway. Do not apply it to `opsflow` by hand, so that Flyway's schema history exists from the start. Experiments use a temporary database.

Run migrations with the `opsflow_migrator` account, which can change the schema. The application connects as `opsflow_app`, which can only read, insert, and update rows in `opsflow`. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#23-mysql-849) for the accounts and their credentials.

When the Spring Boot backend is created, move the migrations to its `src/main/resources/db/migration/`, which Flyway reads by default (`classpath:db/migration`). Move them, do not copy them, so that only one copy exists.

Beyond the rules listed in Initial Constraints, the migration adds these checks, each verified against MySQL 8.4:

- An order is `CREATED` exactly when `finished_at` is empty.
- An order item's `subtotal` equals `unit_price * quantity`.
- The snapshots of an inventory transaction agree with its type and quantity. For example, `RESERVE` lowers `available` and raises `locked` by `quantity`.
- A ticket assignment record must change the assignee. A move from empty to empty, or from a user to the same user, is rejected.
- A correction comment must belong to the same ticket as the comment it corrects. The composite foreign key on `ticket_comments` enforces this, and leaves ordinary comments unchecked.

## Concurrency and privilege verification

The schema, the per-table privileges, and the order and inventory flows were verified against MySQL 8.4 in a temporary database, using [`db/verification/run-db-verification.ps1`](../../db/verification/run-db-verification.ps1). The flows in this document are written as stored procedures in [`concurrency-fixtures.sql`](../../db/verification/concurrency-fixtures.sql), and several `mysql` sessions run them at the same moment, under the restricted application account, so the verification also proves that the per-table grants are enough for the flows. Every test is listed by block, with what it shows and what is not tested yet, in the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md).

Latest full run (2026-10-23, MySQL 8.4): 107 checks passed, 0 failed. The previous full run (2026-10-22) had 66, and the one before it (2026-10-14, MySQL 8.4.9) had 62. The new checks cover 14 tables; the manual stock types and their rule that a reason and an operator are required (`WRITE_OFF`, and the four manual types without a reason, with a blank reason, or without an operator); linking several inventory records to one ticket and refusing duplicate or missing links; the ticket statuses (`CANCELLED` and `CLOSED` accepted, no `REOPENED`), the follow-up link and its foreign keys, and `closed_by` and `cancelled_by`; the application account's privileges on `ticket_inventory`; and the final ticket structure: `users.is_system` defaults to false, `tickets` has no `priority` column, the three ticket indexes for the open count, the archive screen, and the auto-archive job exist, `tickets.source` accepts only `SYSTEM` or `MANUAL` and is required, and an `ORDER_EXCEPTION` ticket needs an order while other types do not; the new list indexes exist; and the exception path is raced: two exception paths for one order at once open one ticket and release the stock once, a confirmation racing the exception path has exactly one winner, and when the records disagree the order is stopped and the stock is left as found. Earlier checks cover the defaults of `users.must_change_password` and `users.failed_login_count`, the allowed purposes of `email_verification_codes`, and the application account's rights on that table. Races were repeated 4 to 12 rounds each. An earlier 24-round stress run of the same races also passed, before the comment-correction checks were added. The temporary database and accounts were dropped afterwards. The ticket rules, the manual stock changes, and the concurrency rules of the other modules have no race checks yet; they come with the code (T-17, T-34).

| Area | What the run shows |
| --- | --- |
| Confirm racing cancel | Exactly one wins. Stock, order status, and inventory transactions are consistent in every round, whichever side wins. |
| Repeated submission | One order is created and stock is reserved once. The same key from two different users creates two orders. |
| Multi-item order with one item short | The whole transaction is rolled back: no order, no items, no stock change, no transaction. Five buyers racing for the last unit give one winner and four out-of-stock results, with nothing half-done. |
| Many reservations on one stock row | No oversell. The snapshots of the inventory transactions form an unbroken chain that ends at the real stock. |
| Negative control | The same race with an unlocked read breaks the chain. This shows that the check would catch the problem that row locking prevents. |
| Timeout and expired confirmation | Two timeout jobs at once release each expired order exactly once and leave live orders alone. Confirming an expired order cancels it and releases the stock at once. Double confirmation and double cancellation take effect once. |
| Deadlock | Locking rows in opposite orders makes MySQL abort exactly one session, with its earlier writes rolled back, and the retry succeeds. Orders that list the same items in opposite orders do not deadlock, because rows are locked by ascending inventory id. |
| Lock wait timeout | Only the statement that timed out is rolled back. The earlier statements of the transaction stay pending until the application rolls back explicitly, which confirms the rule under Concurrency and duplicate protection. |
| Privileges | The application account can update the mutable tables and insert into every table it writes. It is refused any update or delete on the append-only and frozen tables, and any change to the schema. |
| Comment corrections | A correction can point at a comment of the same ticket, and a comment can be corrected more than once. A correction that points at another ticket's comment or at a missing comment is rejected by the database, and a corrected comment cannot be deleted. |
| Identifiers | `Abc` and `abc` are different idempotency keys, `ORD-aaa` and `ORD-AAA` are different order numbers, and `Alice` and `alice` are still the same username. |

What this does not show: the procedures are an executable specification of the flows in SQL, not the application code, so the application's own retry logic, connection pool, and time zone handling still need the same checks once the backend exists. The run uses one server at the default `REPEATABLE READ` isolation level. A passing run shows that the design holds under the interleavings it produces, not that every possible interleaving was tried.

To run it, use `powershell -STA -File db/verification/run-db-verification.ps1`. It asks for the MySQL root password in a masked dialog, creates a temporary database and two temporary accounts, and drops them at the end. It never touches `opsflow`. Run it again whenever the schema, the privileges, or the flows change.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries, including `orders(status, reserved_until)` for the timeout job. The ticket lists show `SYSTEM` tickets before `MANUAL` ones and then the chosen time order, so their indexes lead with `status` and `source`: `(status, source, created_at)` for the pool and the work lists, `(status, source, closed_at)` for the archive, and `(status, source, cancelled_at)` for the cancelled area. `tickets(created_by, status)` serves the open count, `tickets(assignee_id, status)` the in-progress count, `tickets(order_id, status)` the "waiting to be handled" lookup of an order, and `tickets(status, resolved_at)` the archive job. `orders(status, created_at)` serves the order list, and `operation_logs(created_at)` the latest logs. An index for a screen that does not exist yet is added by a new migration when the screen is designed.
- Apply unique constraints to business identifiers such as usernames, role codes, product SKUs, and order numbers.
- Apply composite unique constraints to `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, `inventory_transactions(order_id, inventory_id, type)`, and `ticket_inventory(ticket_id, inventory_id)`. Create each as one composite key and never as separate unique keys on its columns. The physical ERD writes each one in the key column, for example `UK(user_id, idempotency_key)`.
- Require quantities to be greater than zero, stock quantities to be greater than or equal to zero, and monetary values to be non-negative.
- On `inventory_transactions`, require `quantity > 0`, require the four snapshot fields to be greater than or equal to zero, require that `order_id` is present exactly when `type` is `RESERVE`, `CONFIRM`, or `RELEASE`, and require `operator_id` and a non-blank `reason` for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. Enforce these with `CHECK` constraints and validate them in the service layer.
- Use fixed-precision decimals for money.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation, and do not edit or delete order items after the order is created.
- Place order creation, order-item creation, stock reservation, and reservation transactions in the same transaction boundary. Do the same for confirm and release.
- Treat inventory transactions, ticket assignment history, ticket comments, ticket inventory links, and operation logs as append-only trace data. Enforce this with privileges as well as in the service layer: after the first migration is applied, the application account gets only `SELECT` and `INSERT` on these tables, and on `order_items`, which is frozen. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) and [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql).
- Use controlled status values. Enforce valid state transitions in the service layer, and enforce the order status gate with the conditional update described above.
- Enforce the ticket status transitions, and the fields each one must fill, in the service layer only: an assignee for `IN_PROGRESS` and none for `OPEN`, a resolution for `RESOLVED`, `closed_by` and `closed_at` for `CLOSED`, and a reason, `cancelled_by`, and `cancelled_at` for `CANCELLED`. The database checks only the allowed status values and the foreign keys, so a service-layer bug can leave a ticket without a resolution. This was chosen on purpose; the service layer and its tests must not skip these rules (T-33).
- Create the SYSTEM identity (`users.is_system` true) in a seed migration. Refuse in the service layer to log it in, edit it, disable it, soft-delete it, or grant it a role, keep it out of user lists and assignee pickers, and exempt it from the ticket limits (T-36).
- On `tickets`, require `source` to be `SYSTEM` or `MANUAL`, with no default so that a missing value fails, and require `order_id` for an `ORDER_EXCEPTION` ticket (`chk_tickets_order_required`). The rule that `source` agrees with the creator being the SYSTEM identity, and that `ticket_inventory` links are written only with the ticket, is enforced in the service layer (T-33).
- Keep the fields of a ticket that are fixed at creation (`title`, `description`, `type`, `source`, `order_id`, `follows_ticket_id`, `created_by`) unchanged, in the service layer only (T-33).
- A ticket follows at most one earlier ticket, set only when the ticket is created. The foreign key `follows_ticket_id` rejects a missing ticket. The rule that the earlier ticket is `RESOLVED` or `CLOSED` is enforced in the service layer.
- Include `created_at` and `updated_at` on primary business tables. Historical and frozen tables, such as `inventory_transactions`, `ticket_comments`, and `order_items`, must include at least a creation timestamp.
- Refuse role grants to users who are not usable accounts (`status` is not `ACTIVE`, or `deleted` is true) in the service layer, because no foreign key can. Count a user's roles in a permission check only when the user is a usable account. Set `status` to `DISABLED` when a user is soft-deleted. Write a `ROLE_GRANTED` or `ROLE_REVOKED` row to `operation_logs` in the same transaction as every change to `user_roles`.
- Never change `roles.code` after creation, and never delete the `ADMIN` role. When a role is deleted, delete its `user_roles` rows and write a `ROLE_REVOKED` row for each in the same transaction, then write `ROLE_DELETED`. Write `ROLE_CREATED`, `ROLE_UPDATED`, or `ROLE_DELETED` to `operation_logs` in the same transaction as every change to `roles`.
- Refuse any change that would leave no usable account holding `ADMIN`, after locking the `ADMIN` row in `roles`. A new user has no role until an administrator grants one.
- Treat `ADMIN` and `MANAGER` as protected roles: never delete them and never change their `code`. Let only `ADMIN` create, update, or delete roles, and appoint or dismiss managers and administrators. Let `MANAGER` assign only roles below manager, and only to users who hold neither `ADMIN` nor `MANAGER`.
- Create the first administrator at startup from environment variables only when no usable `ADMIN` exists, with `must_change_password` true. Never write the initial password to a file.
- Lock an account after repeated wrong passwords on the schedule in Login security and account recovery, and refuse attempts without checking the password while it is locked. Store email codes hashed, and let the application account insert and update `email_verification_codes` but never delete. On every request, load the account and its roles from the database, and refuse a token issued before `users.password_changed_at`.
- Do not cascade deletes into history. Foreign keys from `inventory_transactions`, `ticket_assignment_history`, `ticket_comments`, `ticket_inventory`, and `operation_logs` must not delete or null out history rows.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Add a separate reservation table if one order needs partial confirmation, partial release, or reservations that outlive their order.
- Add payment and refund entities and a payment-driven order status when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it.

When a field or relationship changes, update the migration history, physical ERD, and this document together.
