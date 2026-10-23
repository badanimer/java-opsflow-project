# OpsFlow Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The initial model contains 16 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Change Log

This document always describes the current design. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-10-24 | 1.7 | Put the warehouse first: `inventory` became the warehouse item with its SKU, name, and optional image, and `products` became an item put on sale, created at its first listing and kept for ever. Added `product_listing_events` and the rules for listing, delisting (with the automatic cancellation of the tickets of the product), the product page, and ordering from it; order creation now locks the product rows with `FOR SHARE` and checks the name and price the user saw; added the search and list indexes and the concurrency rules for products; added `inventory_item_events` and the rules for adding a warehouse item (a generated 8-character SKU, a unique name, an optional starting quantity) and for deleting it softly; required every quantity to be valid; and defined how a person opens a ticket on chosen items; and made an order complete the moment it is placed and final, with no reservation, confirmation, timeout, status, cancellation, or exception path, and defined how a wrong order is corrected through a ticket | [database-v1.7.md](../iterations/database-v1.7.md) |
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

The Simplified ERD and the Business Process Flowchart both reflect an order that completes the moment it is placed and the cancellation that returns its stock. The Business Process Flowchart also shows the idempotency check on order submission: a key already used by the same user returns the original order, and a duplicate-key race rolls back and returns it too. It also shows listing, delisting, adding and deleting warehouse items, and opening a ticket on items.

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

OpsFlow has one built-in identity that is not a person: SYSTEM. It gives what the program does by itself an actor that can be recorded and shown, for example the one who cancels the tickets of a delisted product, or the one who archives a ticket after 7 days. The program opens no ticket by itself today (see Exceptions), but the identity is kept for the tickets it may open later.

- It is one row in `users` with `is_system` true, the username `system`, and the reserved address `system@opsflow.invalid`. `password_hash` holds a fixed marker that is never a valid hash, so no password can match it. A seed migration creates it, together with the roles (T-05).
- It is not a person. It has no role and can never be granted one, so it is never an eligible assignee. It cannot log in: a login for it is refused before any password check and is not counted as a failed attempt. It cannot be edited, disabled, soft-deleted, or have its password changed, and it does not appear in user lists or in the pickers for assigning a ticket.
- It is exempt from the ticket limits.
- Where a user id is required, the SYSTEM identity fills it: `tickets.created_by` for a ticket the program opens, `tickets.closed_by` for an automatic archive, and `ticket_assignment_history.changed_by` for a change the program makes. Audit log rows of system actions keep `operator_id` empty, as before.
- A ticket the program opens also has `tickets.source` set to `SYSTEM`, so the class is stored once on the ticket as well and the screen need not look up the creator.

### Product and Inventory

| Table | Responsibility |
| --- | --- |
| `inventory` | A warehouse item and its stock: a generated SKU, a unique name, an optional image, the two stock quantities, who added the item and when, and who deleted it and when (an item is deleted softly) |
| `products` | An item put on sale: its warehouse item, the name and price it is sold under, whether it is `LISTED`, and who listed or delisted it last and when |
| `product_listing_events` | Append-only record of every listing and delisting of a product |
| `inventory_item_events` | Append-only record of every addition and deletion of a warehouse item. It belongs to the inventory records. |
| `inventory_transactions` | Records every stock movement: restocks, write-offs, adjustments, and sales |

The warehouse comes first. An item exists in `inventory` before it is ever sold, and a product is created only when the item is first listed. An item has at most one product (`UK(inventory_id)`), and the product keeps the same id for ever, so every order placed under it stays tied to it however often it is delisted and listed again. The stock records (`inventory`, `inventory_transactions`, `inventory_item_events`) and the product records (`products`, `product_listing_events`) are separate: listing never creates or changes stock, and a stock-in never lists anything. An inventory record has many inventory transactions, and a transaction references its inventory record only.

`inventory` represents the current state for efficient reads. `inventory_transactions` preserves the history required to explain why stock changed.

#### Warehouse items

- **Adding an item.** A `MANAGER` or an `ADMIN` adds a new kind of item with a name, optionally an image, and optionally a starting quantity. The item is not listed: listing is a separate step.
  - **The name is required** and must not be used by another item that is still in the warehouse. The comparison ignores case, so `A4 paper` and `a4 PAPER` are the same name. The name of a deleted item is free again. The database enforces this with `UNIQUE (name, is_active)`, where `is_active` is a generated column that is 1 for an item that is not deleted and empty for a deleted one.
  - **The SKU is generated by the program**, never typed: 8 characters from `A-Z` and `0-9`, chosen at random. It must differ from every SKU already issued, deleted items included, because the SKU is copied into order items and must keep pointing at one item. On a collision the program generates another one, up to the attempts in Operating Parameters. The database checks the format (`chk_inventory_sku`) and the uniqueness (`uk_inventory_sku`). The SKU never changes.
  - **The starting quantity is optional.** Empty means 0. A number must be valid (see Valid quantities). A starting quantity above 0 is recorded as a `RESTOCK` with the reason `initial stock` and the creator as the operator, so the history explains every unit from the first one. A starting quantity of 0 writes no transaction.
  - **One transaction** inserts the item, the `RESTOCK` if there is one, an `inventory_item_events` row with the action `CREATED` (holding the SKU, the name, and the starting quantity), and the `operation_logs` row.
- **The record of the item** holds who added it (`created_by`) and when (`created_at`), which is the time the item entered the warehouse.
- **The image is optional.** It only makes the product page easier to read, and the lists for listing and for opening a ticket do not need to show it. The database keeps a link or a path, and the application stores the image.
- **Editing.** A `MANAGER` or an `ADMIN` can edit the name or the image of an item only while the item is not listed and not deleted. To change them, delist it first. The new name must follow the same name rule.
- **Deleting an item.** A `MANAGER` or an `ADMIN` can delete an item. No reason is asked for, and the deletion is recorded. The row is not removed from the database, because orders, stock records, tickets, and events refer to it: it is marked deleted with `deleted_at` and `deleted_by`. The stock the item still shows when it is deleted no longer matters, and the program does not touch it or write it off. When the item is added again, a person counts the stock and enters it as the starting quantity. In one transaction the program:
  1. locks the product row of the item, if it has one, and then the `inventory` row, and refuses the deletion when the item is already deleted;
  2. delists the product if it is `LISTED`, in the same way as a delisting, with a `DELISTED` row in `product_listing_events`. The product row is kept as a record and can never be listed again;
  3. cancels every ticket that is `OPEN` or `IN_PROGRESS` and linked to the item, as the SYSTEM identity, with the reason `ITEM_DELETED:` followed by the `inventory` id, and logs each as `TICKET_CANCELLED`;
  4. sets `deleted_at` and `deleted_by`, inserts an `inventory_item_events` row with the action `DELETED` (the SKU, the name, and the stock the item showed, for the record), and writes the `operation_logs` row.
- **A deleted item** leaves every list, the catalog for listing, and the list for opening a ticket. It cannot be listed, edited, stocked, written off, ordered, or linked to a new ticket (an order placed before the deletion can still be cancelled, and the stock goes back to a row that no longer matters), and every such request is refused as "the item no longer exists". It stays readable in the history that refers to it: old orders, stock transactions, tickets, and events. A deletion cannot be undone. To have the item again, add a new one, which gets a new SKU and may use the old name.

#### Listing and delisting

- Only a `MANAGER` or an `ADMIN` lists or delists. Neither asks for a reason. Both are recorded.
- **The catalog.** Pressing List opens a catalog of the warehouse items that are not listed now, meaning items that were never listed and items that were delisted. It can be searched by the name of the item, and by nothing else. Items that are listed or deleted are not offered, and the service layer refuses them anyway. Only an item that exists in the warehouse can be listed.
- **Listing** asks for the price, which must be above zero, and shows the name, which starts as the name of the item and can be changed in the same dialog. In one transaction it creates the product the first time, or sets it to `LISTED` again, with the price, the name, `listed_at`, and `listed_by`, and it writes a `LISTED` row to `product_listing_events` and an `operation_logs` row. Listing does not need stock: an item with no stock can be listed, and an order for it fails with `OUT_OF_STOCK`.
- **A listed product cannot be edited.** Its name and price are fixed. To change either, delist the product and list it again with the new values. Orders placed before keep the name and price they were placed under.
- **Delisting** sets the product to `UNLISTED` with `delisted_at` and `delisted_by`, writes a `DELISTED` row to `product_listing_events`, and cancels the tickets of the product, all in one transaction. A delisted product leaves the product page at once.
- **Tickets of the product.** Every ticket that is `OPEN` or `IN_PROGRESS` and linked through `ticket_inventory` to the warehouse item of the product is cancelled by the system, with the SYSTEM identity as the one who cancelled it and the reason `PRODUCT_DELISTED:` followed by the product id. A ticket that is already `RESOLVED`, `CLOSED`, or `CANCELLED` is left alone. A ticket opened after the delisting is not cancelled. Each cancellation is logged as `TICKET_CANCELLED`. A ticket that links several products is cancelled when any one of them is delisted, because such a ticket is opened by a person and the person can open a new one for the products that remain. The screen shows the cancelled ticket with a message built from the reason code, for example "Widget has been delisted, please open the ticket again". Because there is no notification system, the program cannot yet tell the person who opened the ticket or the one who holds it. How they should be reminded is open (Q-28, and the to-do list idea in [future-ideas.md](../future-ideas.md)).
- **Orders already placed.** An order is completed the moment it is placed, so delisting changes nothing about orders that exist: they stay as they are and keep the name and price they were placed under.

#### The product page

The product page lists the products that are `LISTED`, in the order of their names. Each product shows its image (taken from the warehouse item, or nothing when the item has none), its name, and its price, and nothing else. It can be searched by name. Every role that can view products sees it. Only users who may order see the Order button, and only a `MANAGER` or an `ADMIN` sees the List and Delist controls.

#### Stock quantities

| Field | Meaning |
| --- | --- |
| `available_quantity` | Stock in the warehouse that can be sold now |
| `sold_quantity` | Stock deducted by orders. A manual stock change that corrects a wrong order does not change it, so it counts what orders took |

Both quantities must stay greater than or equal to zero. Stock is changed only under pessimistic row locking; see Concurrency and duplicate protection. There is no reserved quantity, because an order takes its stock the moment it is placed.

#### Valid quantities

Every value in the program that is a quantity must be valid before it is used. The service layer checks it first and refuses an invalid one without changing anything, and the database keeps its own checks as a second line. This rule covers the starting quantity of an item, the quantity of a stock-in or a write-off, the new quantity of a direct set, and the quantity of an order line. A quantity is valid only when it is:

- a whole number: no fraction, no text, and no blank where a number is required;
- within the range of its use: a starting quantity or a direct-set quantity from 0 to the maximum of one stock input, a stock-in or write-off quantity from 1 to that maximum, and an order line from 1 to the maximum per order line (see Operating Parameters);
- never one that would take a stock quantity below zero, or above what the `INT` column holds. A write-off or an order larger than `available_quantity`, and a stock-in that would pass the limit of the column, are refused.

A new quantity field added later follows the same rule, and its range goes into Operating Parameters.

#### Inventory transaction types

| Type | Effect on `inventory` |
| --- | --- |
| `RESTOCK` | `available_quantity` increases (stock received) |
| `WRITE_OFF` | `available_quantity` decreases (stock lost, damaged, or taken out of the warehouse) |
| `ADJUST_IN` | `available_quantity` increases (the quantity was set higher by hand) |
| `ADJUST_OUT` | `available_quantity` decreases (the quantity was set lower by hand) |
| `SALE` | `available_quantity` decreases, `sold_quantity` increases (an order was placed) |

`quantity` is always greater than zero; the type determines the direction. Each transaction stores `available_before` and `available_after` so that any movement can be audited without replaying history. The snapshots are taken from the inventory row while it is locked by the same transaction, so they are exact.

`order_id` is required for `SALE`, and must be empty for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. `operator_id` is required for every type: the ordering user for a sale, and the acting user for a manual change.

#### Manual stock changes

People change stock by hand in three ways. All three change `available_quantity` only; `sold_quantity` belongs to orders.

| Operation | Recorded as | Who |
| --- | --- | --- |
| Stock in (plus button) | `RESTOCK` | `ADMIN`, `MANAGER`, `OPERATOR` |
| Write-off or stock out (minus button) | `WRITE_OFF` | `ADMIN`, `MANAGER`, `OPERATOR` |
| Set the quantity directly | `ADJUST_IN` if the new number is higher, `ADJUST_OUT` if it is lower | `ADMIN`, `MANAGER` |

- **Reason is required for every manual change.** Stock in, write-off, and direct set all need a non-blank reason and the acting user. `SALE` comes from an order and needs no reason. The database enforces this with a `CHECK`, and the service layer validates it first.
- **A change must change something.** The quantity is greater than zero. Setting the quantity to the number it already has is refused and writes nothing.
- **Screen.** The inventory screen has one Modify button per warehouse item. An `OPERATOR` who presses it sees only a plus and a minus, plus a reason field. A `MANAGER` or `ADMIN` also gets a field to type the new quantity. Save is enabled only when the quantity has changed and the reason is filled in.
- **Direct set.** The user types the new `available_quantity`. The request also carries the `available_quantity` the user saw. Inside the transaction the service layer locks the `inventory` row and compares it with the value seen. If they differ, someone else changed the stock in the meantime, and the request is refused with a "stock changed, please reload" result. If they match, the difference becomes the transaction quantity, and the type follows its sign.
- **Write-off.** Under the same lock, `available_quantity >= q` must hold, otherwise the request is refused.
- **A deleted item.** Under the same lock the service layer also checks that the item is not deleted, and refuses every manual change of a deleted item.
- **One transaction.** Each manual change locks the `inventory` row, updates it, inserts the `inventory_transactions` row with exact snapshots, and writes `operation_logs`, all together. It follows the same lock order, retry, and deadlock rules as the order flows.

### Orders

| Table | Responsibility |
| --- | --- |
| `orders` | Stores order-level information such as the customer, order number, total amount, status, and the details of a cancellation |
| `order_items` | Stores the purchased product snapshot, quantity, and transaction price |

A user can create many orders. Each order must contain one or more order items. Each `order_items` record references exactly one product, and an order contains at most one item per product (`UK(order_id, product_id)`).

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`

An order item should retain the product name or SKU and price as they were when the order was created. Later product updates must not change the meaning of historical orders.

Once an order is created, its items and amounts are frozen. The product, quantity, price, and composition of the items, as well as `total_amount` and `item_count`, must not be edited or deleted. An order is final: it is never edited and never cancelled. A wrong order is corrected through a ticket, a manual stock change, and a new order (see Correcting a wrong order). For the same reason `order_items` has `created_at` but no `updated_at`.

#### Order and ticket numbers

`orders.order_no` is `ORD-` followed by a random UUID without hyphens, for example `ORD-3f2b8c1e9a4d4b6f8e0c5a7d1b2c3d4e`. The application generates it in lowercase.

`tickets.ticket_no` is 8 random characters from `A-Z` and `0-9`, for example `K7Q2M9XA`. The application generates it when the ticket is created. It is short on purpose: people read it, say it, and type it to name a ticket, for example to open a follow-up. The database checks the format (`chk_tickets_ticket_no`), and the form turns what a person types into upper case. The unique constraint on each column stays as the final guard: on a collision, the insert fails and the application generates another number and tries again, up to the attempts in Operating Parameters.

#### Order lifecycle

An order has no status. It exists only if it was placed and its stock was deducted in the same transaction, so a placement that fails leaves no order, and an order that exists is complete and final. It is never edited, cancelled, or deleted. There is no payment status because payment is outside the current system boundary.

Additional `orders` fields:

- `created_at` is the time the order was placed and completed.
- `idempotency_key` is optional and unique per user (`UK(user_id, idempotency_key)`), so a repeated submission returns the original order.
- `remark` is optional free text.

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

A ticket is for a problem that the person who found it cannot, or may not, solve alone, and for the exceptions the system finds by itself. A problem that one person can finish directly is not a ticket. A wrong order is a ticket, because an operator cannot undo an order alone (see Correcting a wrong order).

- **Automatic tickets** are not used today (see Exceptions).
- **Manual tickets** are opened by a person for a problem that needs someone else, such as a stock count that disagrees with the records when the finder cannot set the quantity. This is a usage rule. The database and the program do not enforce it, so it is kept in this document and in the screen text.
- `OTHER` is for a problem that needs someone else but is neither an order nor a stock problem. It is not a place for notes.

#### Creating a ticket on items

A person with the right to create tickets (see the Permission Matrix) opens the ticket pool screen and presses the Create ticket button. The form works in this order:

1. **Choose the items, if any.** The person picks zero or more warehouse items from a list that works like the catalog for listing: it shows the items that are in the warehouse, listed or not, and it can be searched by name and by nothing else. Several items can be chosen, and none is also allowed. A ticket that is not about stock, such as one of type `OTHER`, needs no item.
2. **Fill in the details**, the type, the title, and the description, below the chosen items. There is also an optional field for the number of an earlier ticket. When it is filled in, the new ticket is a follow-up to that ticket (see Follow-up tickets). Leaving it empty makes an ordinary ticket.
3. **Confirm.** The program creates the ticket in one transaction. It locks the creator's user row, counts the open tickets the creator holds against the limit, finds the earlier ticket by the number the person typed, if there is one, inserts the ticket, then locks each chosen item with `SELECT ... FOR SHARE` in ascending `inventory` id, checks that it still exists and is not deleted, and inserts the links. If any chosen item is gone, the whole request is refused: no ticket and no link is written, and the screen shows which items changed, for example "Widget no longer exists". The details the person typed are kept, and the item list is reloaded.

| Result | Shown to the person | When |
| --- | --- | --- |
| `CREATED` | The ticket was opened, with its number | The ticket and its links were written |
| `FOLLOW_NOT_FOUND` | No ticket has that number | The number in the follow-up field belongs to no ticket |
| `FOLLOW_NOT_ALLOWED` | That ticket cannot be followed yet: only a resolved or archived ticket can | The ticket exists but is `OPEN`, `IN_PROGRESS`, or `CANCELLED` |
| `ITEM_CHANGED` | The named item no longer exists, please choose again | A chosen item was deleted after the list was loaded |
| `LIMIT_REACHED` | You already have 99 open tickets | The open limit of the creator is reached |
| `NOT_ALLOWED` | You may not create tickets | The account is not usable or its role does not allow it |
| `BUSY` | The system is busy, please try again | The retries on a deadlock or a lock wait were used up. Nothing was created. |

The shared lock on each item is what keeps a ticket from being linked to an item that is being deleted: a deletion needs the item exclusively and cancels the tickets linked to it after it holds the lock, so a ticket that committed first is cancelled, and a ticket that comes later is refused (see Concurrency by Module).

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
| `OPEN` or `IN_PROGRESS` | `CANCELLED` | `MANAGER` and `ADMIN` for any ticket. `OPERATOR` only for their own: a ticket assigned to them, or one they created while it is still `OPEN`. `VIEWER` never. The system also cancels the tickets of a product when the product is delisted (see Listing and delisting). | A non-blank reason | `cancel_reason`, `cancelled_by`, `cancelled_at` |

- **Releasing is not cancelling.** A released ticket goes back to the pool for someone else to take, and the problem is still open. A cancelled ticket is withdrawn for good, because it was a mistake or a duplicate.
- A `RESOLVED` ticket cannot be cancelled. It can only be archived.
- **Cancelled tickets are set apart.** A `CANCELLED` ticket leaves the pool and every work list, and appears only in its own cancelled area, which every role that can view tickets can open, like the archive. Nobody can take, assign, comment on, or bring it back. It is kept and not deleted, because the logs, the follow-up links, and the history refer to it.
- "Own" means the ticket's current assignee, or its creator while the ticket is still `OPEN`. Once someone else takes a ticket it is no longer the creator's for cancelling. To withdraw a ticket someone else holds, an operator asks a `MANAGER` or an `ADMIN`.
- Reassigning an `IN_PROGRESS` ticket to another person is not a status change; see Reassigning a ticket.
- Comments are allowed until the ticket is `CLOSED` or `CANCELLED`, so a `RESOLVED` ticket can still be discussed before it is archived.
- A ticket the program may open in the future starts as `OPEN`, in the pool, with the SYSTEM identity as its creator.
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

- A link is unique per ticket and inventory record (`UK(ticket_id, inventory_id)`). An item that is deleted cannot be linked to a new ticket. A ticket already linked to an item is cancelled when the item is deleted, if it is still `OPEN` or `IN_PROGRESS`.
- **Links are written only when the ticket is created**, in the same transaction as the ticket, so one ticket can cover a batch of products from the start. They are never added later. A new problem is a new ticket. If the new problem is unrelated, it is a plain new ticket, and it does not name the earlier product. If it relates to an earlier ticket, it is a follow-up ticket (see Follow-up tickets). The table is append-only, so the application account has only `SELECT` and `INSERT` on it.
- The service layer should ask for at least one link on an `INVENTORY_EXCEPTION` ticket, because that is the point of the link, and may allow any ticket to have links. The database does not require it, because a `CHECK` cannot look at another table.
- The inventory record is the target, as in `inventory_transactions`, and the product is reached through it.
- Because links come only with the ticket, a ticket that is `CLOSED` or `CANCELLED` has the links it was created with, and nothing else about links can change.

#### Follow-up tickets

`tickets.follows_ticket_id` is empty for an ordinary ticket. When a problem continues after a ticket was resolved, the person opens a new ticket and types the number of the earlier one in the optional follow-up field of the form, and the new ticket records it. The program looks the number up in `tickets.ticket_no`, inside the transaction that creates the ticket, and the ticket is refused if no ticket has that number or if that ticket cannot be followed. This keeps the history of one problem connected across tickets without reopening any of them.

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
| Cancel | `TICKET_CANCELLED` | `{"status": "IN_PROGRESS"}` | `{"status": "CANCELLED", "cancelledBy": 9}`. `remark` is the reason. When the system cancels the tickets of a delisted product, `operator_id` is empty and `remark` is `PRODUCT_DELISTED:` and the product id. |

These formats are a first version. They can change in detail when the audit screen is designed (Q-03).

#### Product logs

Every action on a warehouse item or a product writes one `operation_logs` row in the same transaction as the change. Listing and delisting also write a `product_listing_events` row, and adding or deleting an item also writes an `inventory_item_events` row. `operator_id` is the acting user. These formats are a first version, like the ticket logs (Q-03).

| Action | `action` | `target_type` | `before_data` | `after_data` |
| --- | --- | --- | --- | --- |
| Add a warehouse item | `ITEM_CREATED` | `ITEM`, the `inventory` id | Empty | `{"sku": "K7Q2M9XA", "name": "A4 paper", "imageUrl": null, "quantity": 25}` |
| Edit the name or image of an item | `ITEM_UPDATED` | `ITEM` | The changed fields before | The changed fields after |
| Delete a warehouse item | `ITEM_DELETED` | `ITEM` | `{"name": "A4 paper", "availableQuantity": 10}` | `{"deleted": true, "writtenOff": 10, "cancelledTicketIds": [12, 15]}` |
| List a product | `PRODUCT_LISTED` | `PRODUCT`, the `products` id | Empty on the first listing, `{"status": "UNLISTED"}` afterwards | `{"status": "LISTED", "inventoryId": 3, "name": "A4 paper", "price": "4.50"}` |
| Delist a product | `PRODUCT_DELISTED` | `PRODUCT` | `{"status": "LISTED"}` | `{"status": "UNLISTED", "cancelledTicketIds": [12, 15]}` |

Each ticket cancelled by a delisting also writes its own `TICKET_CANCELLED` row, with an empty `operator_id` because the system acts, and `PRODUCT_DELISTED:` and the product id in `remark`. A deletion of an item does the same with `ITEM_DELETED:` and the `inventory` id, and when the item had a listed product it also writes a `PRODUCT_DELISTED` row, because the product is delisted in the same transaction.

## Relationship Summary

| Source | Cardinality | Target | Description |
| --- | --- | --- | --- |
| `users` | M:N | `roles` | Implemented through `user_roles` |
| `users` | 1:N | `orders` | A user creates multiple orders |
| `orders` | 1:N | `order_items` | An order contains at least one item |
| `products` | 1:N | `order_items` | Each item references one product |
| `inventory` | 1:0..1 | `products` | A warehouse item has at most one product, created the first time the item is listed |
| `products` | 1:N | `product_listing_events` | Every listing and delisting of a product is recorded |
| `inventory` | 1:N | `product_listing_events` | The record names the warehouse item as well |
| `users` | 1:N | `inventory` | A user adds a warehouse item (`created_by`) and may delete it (`deleted_by`) |
| `inventory` | 1:N | `inventory_item_events` | Every addition and deletion of a warehouse item is recorded |
| `users` | 1:N | `inventory_item_events` | The user who added or deleted the item |
| `users` | 1:N | `products` | A user lists a product (`listed_by`) and may delist it (`delisted_by`) |
| `users` | 1:N | `product_listing_events` | The user who listed or delisted |
| `inventory` | 1:N | `inventory_transactions` | An inventory record produces multiple transactions |
| `orders` | 0..1:N | `inventory_transactions` | Sale transactions reference their order |
| `users` | 1:N | `inventory_transactions` | The user who caused the movement: a manual change or a sale |
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

1. An authenticated user submits an order containing one or more products and optionally an idempotency key. For each product the request carries the quantity, and the name and price the user saw on the screen. The product page sends one product at a time, and the flow itself accepts several.
2. If the key already exists for this user, the system returns the original order without changing anything. If two identical submissions race, the second insert violates `UK(user_id, idempotency_key)`; it rolls back and returns the existing order.
3. Within one database transaction, the system:
   1. reads the row of every product with `SELECT ... FOR SHARE`, in ascending product id, before it writes anything. It checks that each product exists and is `LISTED`, that its name and price are the ones the user saw, and that the quantity is a whole number from 1 to the maximum. A product that is not listed fails the order with `PRODUCT_DELISTED`, a product whose name or price changed fails it with `PRODUCT_CHANGED`, and a bad quantity fails it with `INVALID_QUANTITY`. The shared lock lets orders run side by side, while a delisting waits for them or comes first;
   2. creates `orders`;
   3. creates the related `order_items`, copying the SKU from the warehouse item, and the name and price from the product. The SKU is read with a plain read, never with a locking read and never with `INSERT ... SELECT` from `inventory`. A shared lock on an `inventory` row taken here would later have to be upgraded to the exclusive lock of step 4, and two orders that list the same items in opposite order would each hold shared locks the other needs, which is a deadlock (the verification found this);
   4. locks the `inventory` row of every item, reached through `products.inventory_id`, with `SELECT ... FOR UPDATE`, in ascending inventory id, and reads the current quantities;
   5. checks that `available_quantity >= q` for every item. If any item is short, the whole transaction rolls back and the caller receives an out-of-stock result. No order is created and no stock changes, even when other items in the same order had enough stock;
   6. for each item, decreases `available_quantity` and increases `sold_quantity` by `q`, and inserts a `SALE` row into `inventory_transactions` whose snapshots come from the values read in step 4 and whose operator is the ordering user;
   7. writes the required `operation_logs`.
4. The transaction commits only when every step succeeds. The order is complete the moment it commits: nobody confirms it, and no timer runs over it.

#### The order dialog

On the product page an `OPERATOR`, `MANAGER`, or `ADMIN` sees an Order button on each product, and nothing else to do with stock or prices. The button opens a dialog for the quantity, which starts at 1 and is changed by typing a number or with the plus and minus buttons. Confirming sends the order, and the screen shows the result:

| Result | Shown to the user | When |
| --- | --- | --- |
| `COMPLETED` | The order was placed, with its order number | The order was created and its stock deducted. A repeated submission with the same key shows the original order in the same way. |
| `PRODUCT_DELISTED` | This product has been delisted, and the page reloads | The product is no longer `LISTED` when the order is checked |
| `PRODUCT_CHANGED` | This product has changed, please reload | The name or price differs from what the user saw, for example after a delisting and a new listing |
| `OUT_OF_STOCK` | Not enough stock | Some item is short |
| `INVALID_QUANTITY` | The quantity must be a whole number from 1 to 9999 | The quantity is not valid |
| `NOT_ALLOWED` | You may not place orders | The account is not usable or its role does not allow it |
| `BUSY` | The system is busy, please try again | The retries on a deadlock or a lock wait were used up. Nothing was created. |

The screen does not show the stock. A user learns that stock is short only when the order fails. When the page was already open while the product was delisted, the user still sees the product until the order is sent, and then gets `PRODUCT_DELISTED`.

### Correcting a wrong order

An order cannot be cancelled. When an operator placed the wrong quantity or the wrong product, the correction goes through a ticket, so that it is explained, recorded, and done by a person who is allowed to change stock:

1. The operator opens a ticket (see Creating a ticket on items). The `ORDER_EXCEPTION` type names the order, and the items that were wrong can be linked.
2. The person who handles the ticket, the operator or someone else, puts the stock back to what it was before the order with a manual stock change: a stock-in (`RESTOCK`) or, for a manager or an administrator, a direct set. The reason names the ticket, as in the stock-change rules.
3. The handler marks the ticket resolved.
4. The operator places the order again, with a new idempotency key.

The wrong order stays in the history unchanged, and so does its `SALE`. `sold_quantity` keeps counting what orders took, and the manual change restores `available_quantity` only.

### Concurrency and duplicate protection

- **Strategy: pessimistic row locking.** Every stock change locks the affected `inventory` rows with `SELECT ... FOR UPDATE`, reads the quantities, validates, updates, and writes the `inventory_transactions` rows, all in one transaction. A plain `SELECT` must not be used to read quantities for a later update, because another transaction can change them in between and the snapshots would be wrong. There is no version counter on `inventory`, and optimistic locking is not used, so the two strategies are never mixed.
- **Lock order.** Order creation reads the `products` rows first (shared), then inserts the `orders` row, then locks the `inventory` rows in ascending inventory id. A fixed order lowers the chance of deadlock but does not remove it.
- **Deadlock and lock-wait retry.** If the database reports a deadlock or a lock wait timeout, the transaction is rolled back completely and retried in a new transaction, up to the attempt limit in Operating Parameters. MySQL rolls back the whole transaction on a deadlock, but a lock wait timeout may roll back only the failed statement, so the application must explicitly roll back before it retries. Retries are safe because the status gate and the unique constraints below prevent a repeated operation from taking effect twice, and because a placement that failed left nothing behind.
- **Constraint backstop.** `CHECK` constraints keep stock quantities non-negative, so a logic error fails instead of corrupting stock.
- **Manual stock changes** use the same row lock and transaction pattern as orders (see Manual stock changes). A manual change and an order on the same product wait for each other on the `inventory` row, so neither can overwrite the other's quantity. A direct set also checks the value the user saw, so a stale screen cannot silently undo a sale. This is a check made while the row is locked, not an optimistic version counter.
- **Duplicate inventory transactions.** `UK(order_id, inventory_id, type)` on `inventory_transactions` is a second line of defense. A duplicate `SALE` for the same order and inventory record violates the constraint and rolls back. Stock-level types have no order, and the constraint does not apply to them, so repeated restocks are allowed.
- **Duplicate orders.** `UK(user_id, idempotency_key)` prevents duplicate order creation from repeated submissions. It is a composite key, not a unique key on `idempotency_key` alone.

### Exceptions

The program opens no ticket by itself today. An order is placed in one transaction, so every failure (not enough stock, the retries used up, a database error) rolls everything back and leaves no order, no stock change, and nothing to investigate. The user sees the result (`OUT_OF_STOCK`, `BUSY`) and can try again.

A person who finds a problem with an order or with stock opens a ticket (see When to open a ticket). An `ORDER_EXCEPTION` ticket names the order. The ticket fields for tickets the program opens (`tickets.source` `SYSTEM`, the SYSTEM identity as creator) stay in the design for the day an automatic case is added. It would be added with its own rule here, and it must stop the thing it reports in the same transaction that opens the ticket.

## Permission Matrix

This section says what each seeded role may do in each module. It is a first version, kept as it stands for now and expected to be refined before the permission module is built. It sits on top of Authority over roles and accounts, which says who may change roles and accounts. A role that is not allowed an action is refused with 403, and the frontend hides the control.

Principles:

- **Deny by default.** An action that is not listed is refused. A user with no role can do nothing (see Users without roles).
- **`VIEWER` is read-only.** It never changes data in any module.
- **`OPERATOR` runs the day-to-day flow**: orders, stock in, write-offs, and tickets. It does not change master data, set stock quantities directly, or see audit logs, user lists, or role lists.
- **`MANAGER` can do everything `OPERATOR` can, plus master data, setting stock quantities directly, user and role assignment (below manager), and audit logs.**
- **`ADMIN` can do everything `MANAGER` can, plus the actions reserved for it in Authority over roles and accounts.**
- A role counts only when the acting account is a usable account. System actions, such as the archive job and the cancellation of tickets by a delisting, have no acting user and are not covered by this matrix.

| Module | Action | ADMIN | MANAGER | OPERATOR | VIEWER |
| --- | --- | --- | --- | --- | --- |
| Products | View the product page (image, name, and price of the listed products) and search it by name | Yes | Yes | Yes | Yes |
| Products | List an item: open the catalog of warehouse items that are not listed, set the price and, if needed, the name | Yes | Yes | No | No |
| Products | Delist a product, with no reason asked | Yes | Yes | No | No |
| Inventory | Add an item to the warehouse (name, optional image, optional starting quantity; the SKU is generated), and edit the name or image of an item that is not listed | Yes | Yes | No | No |
| Inventory | Delete an item (soft), with no reason asked | Yes | Yes | No | No |
| Inventory | View stock and stock transactions | Yes | Yes | Yes | Yes |
| Inventory | Stock in (`RESTOCK`), reason required | Yes | Yes | Yes | No |
| Inventory | Write-off or stock out (`WRITE_OFF`), reason required | Yes | Yes | Yes | No |
| Inventory | Set the quantity directly (`ADJUST_IN`, `ADJUST_OUT`), reason required | Yes | Yes | No | No |
| Orders | View | Yes | Yes | Yes | Yes |
| Orders | Create, from the product page (it completes at once) | Yes | Yes | Yes | No |
| Tickets | View, including the archive screen of `CLOSED` tickets, the cancelled area of `CANCELLED` tickets, the follow-up links, and the assignment history | Yes | Yes | Yes | Yes |
| Tickets | Create manually, choosing zero or more warehouse items (links are written only at creation), or as a follow-up to a `RESOLVED` or `CLOSED` ticket | Yes | Yes | Yes | No |
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
- When one transaction locks several kinds of rows, it locks them in this order: `users` (ascending id), then `roles`, then `products` (ascending id), then `tickets`, then `orders`, then `inventory` (ascending id). A fixed order lowers the chance of deadlock but does not remove it, so every module uses the deadlock and lock-wait retry in Concurrency and duplicate protection.
- A transaction that holds locks makes no call to anything outside the database, such as sending an email.
- Every audit log row is written in the same transaction as the change it describes.

| Module | What can race | Rule |
| --- | --- | --- |
| Products | Two managers list the same item at once | The first listing inserts the product row, and `UK(inventory_id)` rejects the second insert. Listing again is `UPDATE products SET status = 'LISTED' ... WHERE id = ? AND status = 'UNLISTED'`. Zero rows updated, or a duplicate key, means someone else listed it first, and the program reports that the item is already listed. |
| Products | A listing and a delisting of the same product at once | Both are conditional updates on the status, so only one succeeds, and the other sees zero rows and reports the current state. |
| Products | An order is placed while its product is delisted or listed again | Order creation reads each product row with `FOR SHARE`, in ascending product id, before it writes anything. A delisting or a listing needs the row exclusively. So either the order reads first and the delisting waits until the order commits, or the delisting commits first and the order sees `UNLISTED` and fails with `PRODUCT_DELISTED`, or sees a new price and fails with `PRODUCT_CHANGED`. An order is never created under a product after its delisting has committed. Lock order: `products`, then `orders`, then `inventory`. |
| Products and tickets | A delisting cancels tickets while they are taken, resolved, cancelled, or archived | The delisting locks the product row, then cancels each related ticket by a conditional update, `WHERE id = ? AND status IN ('OPEN', 'IN_PROGRESS')`, all in one transaction. A ticket that someone resolved or cancelled a moment earlier is skipped. A ticket opened after the delisting has committed is not cancelled: the rule covers the tickets that exist at the moment of the delisting. Lock order: `products`, then `tickets`. |
| Warehouse items | Two managers add an item with the same name at once | `UNIQUE (name, is_active)` makes the second insert wait for the first and then fail. The program reports that the name exists, and the failed transaction writes nothing, not even its starting restock or its event. |
| Warehouse items | Two generated SKUs are the same | `uk_inventory_sku` rejects the second insert. The program generates another SKU and tries again, up to the attempts in Operating Parameters. |
| Warehouse items | Two managers delete the same item at once | The deletion locks the product row of the item, if any, and then the `inventory` row, and reads `deleted_at` under that lock. The second request waits, sees the item deleted, and reports it. The delisting, the cancellations, and the event are written once. Lock order: `products`, then `inventory`, and then the `tickets` it cancels (see the rows below). |
| Warehouse items and orders | An order is placed as the item is deleted | An order reads its product rows with `FOR SHARE` before it writes. The deletion needs the product row exclusively, so one waits for the other. If the order commits first, it is complete and stays valid, and the deletion goes on to delist the product and mark the item deleted. If the deletion commits first, the product is `UNLISTED` and the order fails with `PRODUCT_DELISTED`. An order is never placed under an item after its deletion has committed. |
| Warehouse items and tickets | A ticket is opened on an item as it is deleted | The ticket creation inserts the ticket and then locks each item with `FOR SHARE` and checks it is not deleted. The deletion takes the `inventory` row exclusively and only then cancels the open tickets linked to it, so it sees every ticket that committed before it. A creation that committed first is cancelled by the deletion. A creation that waits for the deletion sees the item deleted and is refused as `ITEM_CHANGED`. This is the one place where tickets are locked after `inventory`; it is safe because the only transaction that waits for a warehouse row while holding a ticket row is a creation, and its ticket row is new and invisible to the deletion. |
| Warehouse items and products | A listing and a deletion of the same item at once | The listing checks that the item is not deleted under a shared lock on the `inventory` row, after the product row. The deletion re-reads the product row after it holds the `inventory` row, and rolls back and retries if a product appeared in between. The item is never left deleted with a `LISTED` product. |
| Warehouse items | An item is edited while it is listed or being listed, or after it was deleted | The edit locks the item's product row first, if there is one, and refuses when the product is `LISTED`. Only then does it lock the `inventory` row and change the name or image. Listing takes the product row first as well, so the two cannot interleave. Lock order: `products`, then `inventory`. |
| Inventory | Manual changes, orders, and cancellations on the same stock | Row lock on `inventory`, as specified under Manual stock changes and Concurrency and duplicate protection. A manual change also checks under the lock that the item is not deleted, and a deletion needs the same lock, so one waits for the other. |
| Orders | A repeated submission | The unique key `UK(user_id, idempotency_key)` and the row locks, as specified under Order and Inventory Flow. |
| Tickets | Two people take the same ticket from the pool, or a take meets an assignment | `UPDATE ... SET assignee_id = ?, status = 'IN_PROGRESS' WHERE id = ? AND status = 'OPEN'`. Zero rows updated means someone else got it first. |
| Tickets | Two status changes, such as archive and cancel, at once | Every transition is a conditional update on the current status, so only one succeeds. The other sees zero rows, reads the ticket, and reports its current state. |
| Tickets | Two managers reassign the same ticket, the assignee releases it as a manager reassigns it, or a reassignment meets a cancel or a resolve | Lock the ticket row, check the status is `IN_PROGRESS`, and compare the current assignee with the one the actor saw on the screen. If it differs, refuse with "ticket changed, please reload", so one change cannot silently overwrite another. Then update `assignee_id` and insert the `ticket_assignment_history` row, all in one transaction. |
| Tickets | A ticket is taken, assigned, or moved in while the person's account is disabled, loses its role, or is soft-deleted, or two tickets reach a person at 98 in progress | Before the change, lock that person's user row with `FOR UPDATE`, re-check that they are an eligible assignee, and count the tickets assigned to them that are `IN_PROGRESS`. Refuse at 99. The exclusive lock makes two simultaneous requests for the same person wait for each other, so the second one sees the first and cannot slip past the limit. Disabling a user or changing roles needs the same row, so one waits for the other. Lock order: `users`, then `tickets`. |
| Tickets | A ticket is created on items that are deleted at the same moment | See Warehouse items and tickets above: the creation locks each chosen item with `FOR SHARE` in ascending id after it inserts the ticket, and refuses the whole ticket if one is gone. Lock order: `users`, then `tickets` (a new row), then `inventory`. |
| Tickets | Two tickets are created at once by a person at 98 open | Lock the creator's user row with `FOR UPDATE`, count the tickets they created that are `OPEN`, refuse at 99, then insert the ticket, all in one transaction. Creations by the same person wait for each other, and creations by different people do not. Lock order: `users`, then `tickets`. |
| Tickets | A comment arrives as the ticket is archived or cancelled | Lock the ticket row with `FOR SHARE`, check the status is not `CLOSED` or `CANCELLED`, then insert. The archive or cancel update needs exclusive access, so it waits for the insert or comes first and makes the check fail. Links are not a case, because they are written in the same transaction that creates the ticket. |
| Tickets | A batch archive, the system job (7 days, or 99 waiting), and a manager's archive or a cancel overlap | Each ticket is archived in its own transaction through the status gate, `WHERE id = ? AND status = 'RESOLVED'`. A ticket already moved is skipped. Running the same batch or the same job twice is safe. The count of 99 is read before the job starts and need not be exact: if a ticket is resolved or archived in the meantime, the job only archives a few more or fewer, and the next run corrects it. |
| Tickets | A follow-up is opened as the earlier ticket changes | No lock is needed. The number is looked up in the creating transaction. A ticket that is `RESOLVED` or `CLOSED` never goes back, so the permission to follow it cannot be lost, and a number that matches no ticket is refused. The foreign key is the last line of defence. |
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

These values belong in application configuration, not in the database.

| Parameter | Value | Reason |
| --- | --- | --- |
| Attempts on deadlock or lock-wait timeout | 3 in total, including the first | At most two retries, so retrying never loops |
| Wait between retries | 50 ms, then 150 ms, each with a small random delay | Prevents conflicting requests from retrying at the same moment |
| Login lock steps | 5 failures: 1 minute; 10: 5 minutes; 15: 15 minutes; 20: until midnight | Slows guessing while giving a person who mistyped a quick way back |
| Failure count reset | Midnight in Sydney time (`Australia/Sydney`), configurable | A new day starts clean, on the clock the users live by |
| Maximum quantity per order line | 9999 | A typing slip such as 99999 is refused with `INVALID_QUANTITY`, and the quantity stays small. It limits one order line only, not the stock, and the screen never shows the stock. |
| Maximum quantity in one stock input | 999,999 | The starting quantity of an item, a stock-in, a write-off, and a direct set are refused above it, so a slip does not reach the stock. The stock itself is limited only by the `INT` column. |
| SKU and ticket number generation attempts | 5 | A generated SKU or ticket number that collides is replaced, and the creation fails with an error if all attempts collide, which is practically impossible with 36^8 values. |
| Open tickets created per person | 99 | A person cannot flood the board with tickets. It counts tickets the person created that are `OPEN`, and it applies to creating a ticket. |
| Tickets in progress per person | 99 | A person cannot hoard work. It counts tickets assigned to the person that are `IN_PROGRESS`, and it applies to taking, assigning, and receiving a ticket by reassignment. |
| Auto-archive delay | 7 days after `resolved_at` | A resolved ticket that nobody archived does not wait for ever. A `MANAGER` or an `ADMIN` can archive it earlier at any time. |
| Resolved tickets waiting before auto-archive | 99 in the whole system | When 99 `RESOLVED` tickets are waiting, the system archives all of them, so the waiting list stays short. |
| Archive job interval | 1 minute | A simple schedule. The job checks the 7-day rule and the count of 99. |
| Email code | 6 digits, valid 10 minutes, 5 wrong entries, resend after 60 seconds | Long enough to be inconvenient to guess, short enough to be quick to use |

Retry only after the previous transaction has been fully rolled back, and run the retry in a new transaction.

## Field Specifications

The physical ERD shows generic types. The migration must use the types below. Keep API validation limits equal to the database lengths so that over-long input is rejected instead of being silently truncated.

### Numbers, money, and time

| Field | Type and rule |
| --- | --- |
| Product price, order item unit price | `DECIMAL(12,2)`, not negative |
| Order item subtotal, order total amount | `DECIMAL(14,2)`, not negative |
| Stock quantities, order quantity, transaction quantity | `INT`. Stock quantities are not negative. Order and transaction quantities are greater than zero. Every quantity must also be valid (see Valid quantities). |
| Primary keys and foreign keys | `BIGINT`, with the same type on both sides of every relationship |
| Creation, update, cancellation, and expiry times | `DATETIME(3)`, stored in UTC |

Money is kept to two decimal places and calculated with Java `BigDecimal`.

### Strings

| Field | Type |
| --- | --- |
| `users.username` | `VARCHAR(64)` |
| `users.email` | `VARCHAR(254)` |
| `users.password_hash` | `VARCHAR(255)` |
| `email_verification_codes.email` | `VARCHAR(254)`, the same as `users.email` |
| `email_verification_codes.code_hash` | `VARCHAR(255)` |
| `inventory.sku`, `inventory_item_events.sku` | `CHAR(8)`, generated, `A-Z` and `0-9` only (`chk_inventory_sku`) |
| `order_items.product_sku` | `VARCHAR(64)`, a copy of `inventory.sku` |
| `inventory.name`, `inventory_item_events.name`, `products.name`, `product_listing_events.name`, `order_items.product_name` | `VARCHAR(200)` |
| `inventory.image_url` | `VARCHAR(500)`, optional. It holds a link or a path to the image, and the image itself is stored by the application. |
| `orders.order_no` | `VARCHAR(64)` |
| `tickets.ticket_no` | `CHAR(8)`, generated, `A-Z` and `0-9` only (`chk_tickets_ticket_no`) |
| `orders.idempotency_key` | `VARCHAR(128)` |
| Status and type fields, `roles.code` | `VARCHAR(32)`, restricted to the allowed values |
| `products.status`, `product_listing_events.action`, `inventory_item_events.action`, `tickets.source` | `VARCHAR(16)`, restricted to the allowed values |
| `tickets.title` | `VARCHAR(200)` |
| `orders.cancel_reason`, `inventory_transactions.reason`, `ticket_assignment_history.reason`, `tickets.cancel_reason` | `VARCHAR(255)` |
| `remark` fields | `VARCHAR(500)` |
| `operation_logs.ip_address` | `VARCHAR(45)` |
| `operation_logs.action`, `operation_logs.target_type` | `VARCHAR(64)` |
| Ticket descriptions, `tickets.resolution`, `ticket_comments.content` | `TEXT` |
| `operation_logs.before_data`, `after_data` | `JSON` |

`orders.order_no`, `tickets.ticket_no`, and `orders.idempotency_key` use the `utf8mb4_0900_bin` collation, so they are compared exactly and `Abc` and `abc` are different values. `users.username`, `users.email`, `inventory.sku`, and `roles.code` keep the default case-insensitive, accent-insensitive collation, so two usernames that differ only in case count as the same.

`roles.name` and `roles.description` were not covered by the supplied values. Use `VARCHAR(64)` and `VARCHAR(255)` unless a reason to change appears.

### Time zone

Times are stored in UTC. `DATETIME` carries no time zone, and the defaults for `created_at` and `updated_at` use `CURRENT_TIMESTAMP(3)`, which follows the session time zone. Application code and the database must therefore follow one convention:

- Set the session time zone of every application connection to UTC. Changing only `serverTimezone` is not enough. For Connector/J, use `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- Have Java generate every time it writes, such as the expiry of an email code, in UTC.
- Run `SET time_zone = '+00:00';` before any manual SQL that involves time.
- Convert to a local time zone only when displaying times in the user interface.

## Initial Migration

The first executable migration is [`db/migration/V1__initial_schema.sql`](../../db/migration/V1__initial_schema.sql). It creates all 16 tables for MySQL 8.0.16 or later, using the types in Field Specifications. It creates structure only. The roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER` are added by a separate seed migration when the permission module is implemented, so an empty `roles` table is expected for now. Never edit a migration that has been applied. Add a new one.

The first application of this migration is done by Flyway, when the backend is connected to Flyway. Do not apply it to `opsflow` by hand, so that Flyway's schema history exists from the start. Experiments use a temporary database.

Run migrations with the `opsflow_migrator` account, which can change the schema. The application connects as `opsflow_app`, which can only read, insert, and update rows in `opsflow`. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#23-mysql-849) for the accounts and their credentials.

When the Spring Boot backend is created, move the migrations to its `src/main/resources/db/migration/`, which Flyway reads by default (`classpath:db/migration`). Move them, do not copy them, so that only one copy exists.

Beyond the rules listed in Initial Constraints, the migration adds these checks, each verified against MySQL 8.4:

- An order has no status and no cancellation fields: it is complete and final when it exists.
- An order item's `subtotal` equals `unit_price * quantity`.
- The snapshots of an inventory transaction agree with its type and quantity. For example, `SALE` lowers `available` by `quantity`.
- A ticket assignment record must change the assignee. A move from empty to empty, or from a user to the same user, is rejected.
- A correction comment must belong to the same ticket as the comment it corrects. The composite foreign key on `ticket_comments` enforces this, and leaves ordinary comments unchecked.

## Concurrency and privilege verification

The schema, the per-table privileges, and the order and inventory flows were verified against MySQL 8.4 in a temporary database, using [`db/verification/run-db-verification.ps1`](../../db/verification/run-db-verification.ps1). The flows in this document are written as stored procedures in [`concurrency-fixtures.sql`](../../db/verification/concurrency-fixtures.sql), and several `mysql` sessions run them at the same moment, under the restricted application account, so the verification also proves that the per-table grants are enough for the flows. Every test is listed by block, with what it shows and what is not tested yet, in the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md).

Latest full run (2026-10-24, MySQL 8.4): 149 checks passed, 0 failed. The previous full run (2026-10-23) had 107. The run covers the 16 tables and their checks; the warehouse items (the generated SKU, the unique name among the items that are not deleted, the soft delete) and their events; products and listing events; the orders that complete at once and their cancellation, with the `SALE` and `RETURN` transaction rules; the manual stock types and their reason and operator rules; the ticket structure; the privileges of the application account, including the new append-only table; and the races listed below. Races were repeated 4 to 12 rounds each. The temporary database and accounts were dropped afterwards. The ticket rules, the manual stock changes, and the concurrency rules of the other modules have no race checks yet; they come with the code (T-17, T-34). The checks that were removed (confirmation, timeout, expired confirmation, cancellation, and the exception path, 10 in all) belong to flows that no longer exist.

The run found one design problem, now fixed in the text: copying the SKU into the order items with `INSERT ... SELECT` from `inventory` took shared locks on the stock rows before the exclusive locks, and two orders listing the same items in opposite order deadlocked on the lock upgrade (10 of 12 rounds needed a retry). The SKU is read with a plain read.

| Area | What the run shows |
| --- | --- |
| Repeated submission | One order is created and stock is deducted once. The same key from two different users creates two orders. |
| Multi-item order with one item short | The whole transaction is rolled back: no order, no items, no stock change, no transaction. Five buyers racing for the last unit give one winner and four out-of-stock results, with nothing half-done. |
| Many sales on one stock row | No oversell. The snapshots of the inventory transactions form an unbroken chain that ends at the real stock. |
| Negative control | The same race with an unlocked read breaks the chain. This shows that the check would catch the problem that row locking prevents. |
| Products | Two managers listing the same item give one product and one event. Listing again after a delisting keeps the product id. Orders are refused for a stale price, a delisted product, or a bad quantity. Five orders racing a delisting are each completed or refused, and none is created after it. A delisting cancels the open and in-progress tickets of the item once. |
| Warehouse items | Adding an item records its starting stock as a restock and an event; a repeated name or SKU and a bad quantity are refused; two additions with one name give one item. Deleting an item delists its product, cancels its open tickets, leaves its stock alone, records the event, and frees its name; two deletions at once give one. An order, a ticket, or a listing racing a deletion ends in one of the two consistent outcomes. |
| Tickets on items | A ticket can be opened on no items or on several, and one deleted item refuses the whole ticket with nothing written. |
| Deadlock | Locking rows in opposite orders makes MySQL abort exactly one session, with its earlier writes rolled back, and the retry succeeds. Orders that list the same items in opposite orders do not deadlock, because rows are locked by ascending inventory id and the SKU is read without a lock. |
| Lock wait timeout | Only the statement that timed out is rolled back. The earlier statements of the transaction stay pending until the application rolls back explicitly, which confirms the rule under Concurrency and duplicate protection. |
| Privileges | The application account can update the mutable tables and insert into every table it writes. It is refused any update or delete on the append-only and frozen tables, and any change to the schema. |
| Comment corrections | A correction can point at a comment of the same ticket, and a comment can be corrected more than once. A correction that points at another ticket's comment or at a missing comment is rejected by the database, and a corrected comment cannot be deleted. |
| Identifiers | `Abc` and `abc` are different idempotency keys, `ORD-aaa` and `ORD-AAA` are different order numbers, and `Alice` and `alice` are still the same username. |

What this does not show: the procedures are an executable specification of the flows in SQL, not the application code, so the application's own retry logic, connection pool, and time zone handling still need the same checks once the backend exists. The run uses one server at the default `REPEATABLE READ` isolation level. A passing run shows that the design holds under the interleavings it produces, not that every possible interleaving was tried.

To run it, use `powershell -STA -File db/verification/run-db-verification.ps1`. It asks for the MySQL root password in a masked dialog, creates a temporary database and two temporary accounts, and drops them at the end. It never touches `opsflow`. Run it again whenever the schema, the privileges, or the flows change.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries. The ticket lists show `SYSTEM` tickets before `MANUAL` ones and then the chosen time order, so their indexes lead with `status` and `source`: `(status, source, created_at)` for the pool and the work lists, `(status, source, closed_at)` for the archive, and `(status, source, cancelled_at)` for the cancelled area. `tickets(created_by, status)` serves the open count, `tickets(assignee_id, status)` the in-progress count, `tickets(order_id, status)` the lookup of the tickets of an order, and `tickets(status, resolved_at)` the archive job. `orders(created_at)` serves the order list, and `operation_logs(created_at)` the latest logs. An index for a screen that does not exist yet is added by a new migration when the screen is designed.
- Apply unique constraints to business identifiers such as usernames, role codes, warehouse item SKUs, and order numbers. Keep the names of the warehouse items that are not deleted unique with `UNIQUE (name, is_active)`, and put a unique constraint on `products(inventory_id)`, so an item has at most one product.
- Apply composite unique constraints to `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, `inventory_transactions(order_id, inventory_id, type)`, and `ticket_inventory(ticket_id, inventory_id)`. Create each as one composite key and never as separate unique keys on its columns. The physical ERD writes each one in the key column, for example `UK(user_id, idempotency_key)`.
- Require quantities to be greater than zero, stock quantities to be greater than or equal to zero, and monetary values to be non-negative.
- On `inventory_transactions`, require `quantity > 0`, require the two snapshot fields to be greater than or equal to zero, require `operator_id` for every type, require that `order_id` is present exactly when `type` is `SALE`, and require a non-blank `reason` for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. Enforce these with `CHECK` constraints and validate them in the service layer.
- Use fixed-precision decimals for money.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation, and do not edit or delete order items after the order is created.
- Place order creation, order-item creation, the stock deduction, and the `SALE` transactions in the same transaction boundary.
- Treat inventory transactions, ticket assignment history, ticket comments, ticket inventory links, product listing events, inventory item events, and operation logs as append-only trace data. Enforce this with privileges as well as in the service layer: after the first migration is applied, the application account gets only `SELECT` and `INSERT` on these tables, and on `order_items`, which is frozen. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) and [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql).
- Use controlled status values. Enforce valid state transitions in the service layer, and enforce the order status gate with the conditional update described above.
- Enforce the ticket status transitions, and the fields each one must fill, in the service layer only: an assignee for `IN_PROGRESS` and none for `OPEN`, a resolution for `RESOLVED`, `closed_by` and `closed_at` for `CLOSED`, and a reason, `cancelled_by`, and `cancelled_at` for `CANCELLED`. The database checks only the allowed status values and the foreign keys, so a service-layer bug can leave a ticket without a resolution. This was chosen on purpose; the service layer and its tests must not skip these rules (T-33).
- Create the SYSTEM identity (`users.is_system` true) in a seed migration. Refuse in the service layer to log it in, edit it, disable it, soft-delete it, or grant it a role, keep it out of user lists and assignee pickers, and exempt it from the ticket limits (T-36).
- On `products`, require a price that is not negative, a status of `LISTED` or `UNLISTED`, and the pairing of the delisting fields: a `LISTED` product has no `delisted_at` and no `delisted_by`, and an `UNLISTED` product has both (`chk_products_delisted`). The rule that name and price do not change while a product is `LISTED`, and that a listing needs a price above zero, is enforced in the service layer.
- On `inventory`, require the SKU to be 8 characters from `A-Z` and `0-9` (`chk_inventory_sku`), and require a deleted item to carry both `deleted_at` and `deleted_by` (`chk_inventory_deleted`). Never delete an `inventory` row: delete it softly. The rules that a deleted item cannot be listed, edited, stocked, ordered, or linked are enforced in the service layer under the row lock.
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
- Do not cascade deletes into history. Foreign keys from `inventory_transactions`, `ticket_assignment_history`, `ticket_comments`, `ticket_inventory`, `product_listing_events`, `inventory_item_events`, and `operation_logs` must not delete or null out history rows.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Add a reservation step and a separate reservation table if orders ever need partial confirmation, partial release, or reservations that outlive their order. Orders complete at once today.
- Add payment and refund entities and a payment-driven order status when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it.

When a field or relationship changes, update the migration history, physical ERD, and this document together.
