# OpsFlow Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The model contains 17 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Change Log

This document always describes the current design. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-16 | 1.16 | The refusals of orders and manual stock changes are named as the code gives them: `STOCK_CHANGED` for a correction from a stale stock, `OUT_OF_STOCK` for a write-off larger than the stock, and `FORBIDDEN` for an order the roles do not allow. The quantity of an order is checked before its transaction, and the times of an order are taken after its locks. Two operating parameters: the lines of one order and the attempts at an order number. The search by one order number keeps `ORD-` in upper case. The person chooses 8, 16, or 24 cards a page on the product page, and every list shows the number of its page. No table or flow changed | [database-v1.16.md](../iterations/database-v1.16.md) |
| 2026-11-15 | 1.15 | The application account loses `INSERT` on `roles`: the roles are fixed, so it keeps only `UPDATE` there, for the row lock of Administrator protection | [database-v1.15.md](../iterations/database-v1.15.md) |
| 2026-11-15 | 1.14 | One signed-in device per account: a login writes a new `users.session_id` that its token carries, the token check refuses an earlier login with `SESSION_REPLACED`, and the open pages of the earlier login are told at once by an event stream. A delisting takes its time after the lock on the product row | [database-v1.14.md](../iterations/database-v1.14.md) |
| 2026-11-15 | 1.13 | The user page is built, with the search defined as a part of the username without regard to case. The four changes to a user are built, so their refusals have result codes (`USER_CHANGED`, `USER_NOT_ACTIVE`, `LAST_ADMIN`), returning the tickets of a disabled or deleted user to the pool waits for the ticket module, the application account has `DELETE` on `user_roles` and keeps `UPDATE` on `roles` for the row lock, and the SYSTEM identity is refused after the locks and not before | [database-v1.13.md](../iterations/database-v1.13.md) |
| 2026-11-14 | 1.12 | Reviewed the Permission Matrix with the owner before the permission module is built. The four seeded roles are fixed: no role is created, updated, or deleted while the program runs, and a new role comes only with a backend change. A user may hold several roles, and the permissions are the union. Nobody may disable or soft-delete their own account or change their own roles. Setting the quantity directly is renamed Correction and opened to `OPERATOR`. A user picker that returns only ids and usernames serves the ticket filters and the choice of an assignee. The other working defaults of the matrix are confirmed. No table changed | [database-v1.12.md](../iterations/database-v1.12.md) |
| 2026-11-12 | 1.11 | Defined the login steps: the password is still checked outside the transaction, and the short transaction that records the result locks the user row and confirms that the hash it checked is still the current one, so a login cannot succeed with a password that a reset has just replaced. Defined what a disabled account gets: `ACCOUNT_DISABLED` and no token at login when the password is right, and `ACCOUNT_DISABLED` on the next request when it is disabled after login. The pending page is now only for a user with no role. Added the seed migration `V2` with the four roles and the SYSTEM identity, and made the token rule exact: a token is accepted only when it was issued after `password_changed_at`, to the millisecond. No table changed Extended on 2026-11-13 when sign-up and password recovery were built: sign-up says when an address already has an account (`EMAIL_TAKEN`), and a request for a recovery code is answered the same way whether or not a mail is sent; a sign-up logs the new user in, so the user goes straight to the pending page; requests are limited per client address (10 in an hour) and for the whole system (200 in a day), counted in the new table `email_code_request_counts` (migration `V3`, 17 tables); a code is stored as a keyed hash; the steps of sign-up and of the password reset are written down; a username is 3 to 64 letters and digits, with no space and no other character; and the mail goes out by SMTP from a Gmail account made for OpsFlow (Q-12) | [database-v1.11.md](../iterations/database-v1.11.md) |
| 2026-11-03 | 1.10 | Shortened the keeping of email codes: a code is deleted as soon as it expires (10 minutes after it was made), by a job that runs once a minute, instead of 24 hours after it expires | [database-v1.10.md](../iterations/database-v1.10.md) |
| 2026-10-27 | 1.9 | Decided the open database questions: an item with no stock cannot be listed (`NO_STOCK`); a deleted item still refuses manual stock changes, with the reason stated; every record is kept for ever except expired email codes, which a job deletes 24 hours after they expire (the application account gets `DELETE` on `email_verification_codes` only); and defined the log formats that were missing: stock logs, order records (`order_attempts` is the record), user and account logs (sign-up, status, soft delete, password reset, login lock), and the ticket number and batch id on archived tickets; added `order_attempts.product_name`, the name of the product at the time of an attempt; made a deleted item unavailable in the pickers, gave deleted items their own module in the warehouse log, defined the labels every audit row carries, added the search by order number, and redesigned the audit page as a menu of seven areas, with a `category` on every audit row | [database-v1.9.md](../iterations/database-v1.9.md) |
| 2026-10-26 | 1.8 | Cleaned the order part of every statement left from the old order lifecycle (an order is complete and final, with no status and no cancellation); defined the ticket screens and what each role sees on them (an `OPERATOR` sees the pool, and only the tickets they hold or resolved); fixed the username and the email after sign-up and reserved `users.username_changed_at` and `users.email_changed_at`; defined item images (type, size, storage); and stated that the `OPEN` tickets of a disabled creator stay in the pool; removed ticket comments and their table (15 tables); dropped the order list index; defined the stock page and log, ticket search, the user and audit pages, paging, and the order number on the ticket form; and made the first administrator a console command on the server with a hidden password prompt (the forced password change and `users.must_change_password` are gone); added `order_attempts`, the record of every attempt to place an order, successful or not, with an order number for each (16 tables), and defined two logs, the product log (listing records and order records) and the warehouse log (item records and stock movements), each read as all records, one chosen, or the own log of one, with a shared searchable picker; and let a ticket name the number of any order attempt, a refused one included, so that a failed order can be reported | [database-v1.8.md](../iterations/database-v1.8.md) |
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

The Simplified ERD and the Business Process Flowchart both reflect an order that completes the moment it is placed and is final: it has no status, and it is never cancelled. The Business Process Flowchart also shows the idempotency check on order submission: a key already used by the same user returns the original order, and a duplicate-key race rolls back and returns it too. It also shows how a wrong order is corrected through a ticket, listing, delisting, adding and deleting warehouse items, and opening a ticket on items.

## Table Overview

### Identity and Access

| Table | Responsibility |
| --- | --- |
| `users` | Stores user accounts, identity information, and account status |
| `roles` | Defines system roles |
| `user_roles` | Associates users with roles and supports multiple roles per user |
| `email_verification_codes` | Holds the one-time email codes used for sign-up and password recovery |
| `email_code_request_counts` | Counts the accepted requests for an email code: per client address in an hour, and for the whole system in a day, so that the mail service cannot be used for spam (see Email codes) |

`users` and `roles` have a many-to-many relationship implemented through `user_roles`. A user cannot be granted the same role twice, so `user_roles` has a composite unique key on `(user_id, role_id)`.

`users` uses soft deletion, which is marked by the `deleted` flag, and `inventory` uses soft deletion marked by `deleted_at` (see Warehouse items). On `users`, `status` is a separate field: `ACTIVE` or `DISABLED`. `status` says whether the account can be used now and can change back and forth, while `deleted` says whether the account is treated as removed. When a user is soft-deleted, the service layer also sets `status` to `DISABLED`. Unique values such as `username`, `email`, and `sku` stay reserved after soft deletion and cannot be reused. This protects historical orders and audit records.

#### Role grants

The foreign keys on `user_roles` only check that the user row and the role row exist. They do not read `deleted` or `status`, so the database accepts a grant to a soft-deleted or disabled user. A user is a **usable account** only when `status` is `ACTIVE` and `deleted` is false. Two rules follow, and both live in the service layer because the database cannot express them with a foreign key:

- The application must refuse to grant a role to a user who is not a usable account.
- A permission check counts the roles of a user only when the user is a usable account. A disabled or soft-deleted user has no effective permissions, even though the `user_roles` rows still exist.
- Revoking a grant is always allowed, including a grant held by a disabled user, except where Administrator protection refuses it.

Disabling a user does not delete the rows in `user_roles`. When the user is set back to `ACTIVE`, the existing roles take effect again with no re-grant. Because a permission check must read `status` and `deleted` on every request, a login token issued before the account was disabled must not keep working: either check the account on every request, or keep tokens short-lived.

`user_roles` holds only the grants that exist now. It has no history and no deletion mark, so after a grant is revoked, nothing in the table shows that it ever existed. Every grant and revocation is therefore written to `operation_logs`; see Auditing.

#### Username and email are fixed

A user cannot change the username or the email address after the account is created. There is no screen or endpoint for it. A person who cannot log in uses the Forgot password link on the login page, which sets a new password by an email code (see Login security and account recovery). That is the only account change a user makes alone (see Account pages).

The database keeps room for a later change flow. `users.username_changed_at` and `users.email_changed_at` are empty today and are written only by a future flow that changes the value. The unique keys on `username` and `email` stay as they are, so a value that someone once held is never reused. A fuller user system (changing them, a profile, and so on) is an idea for later; see [future-ideas.md](../future-ideas.md).

#### The SYSTEM identity

OpsFlow has one built-in identity that is not a person: SYSTEM. It gives what the program does by itself an actor that can be recorded and shown, for example the one who cancels the tickets of a delisted product, or the one who archives a ticket after 7 days. The program opens no ticket by itself today (see Exceptions), but the identity is kept for the tickets it may open later.

- It is one row in `users` with `is_system` true, the username `system`, and the reserved address `system@opsflow.invalid`. `password_hash` holds a fixed marker that is never a valid hash, so no password can match it. The seed migration [`V2__seed_roles_and_system_identity.sql`](../../backend/src/main/resources/db/migration/V2__seed_roles_and_system_identity.sql) creates it, together with the roles.
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
- **The image is optional.** It only makes the product page easier to read, and the lists for listing and for opening a ticket do not need to show it. The database keeps a link or a path, and the application stores the image (see Item images).
- **Editing.** A `MANAGER` or an `ADMIN` can edit the name or the image of an item only while the item is not listed and not deleted. To change them, delist it first. The new name must follow the same name rule.
- **Deleting an item.** A `MANAGER` or an `ADMIN` can delete an item. No reason is asked for, and the deletion is recorded. The row is not removed from the database, because orders, stock records, tickets, and events refer to it: it is marked deleted with `deleted_at` and `deleted_by`. The stock the item still shows when it is deleted no longer matters, and the program does not touch it or write it off. When the item is added again, a person counts the stock and enters it as the starting quantity. In one transaction the program:
  1. locks the product row of the item, if it has one, and then the `inventory` row, and refuses the deletion when the item is already deleted;
  2. delists the product if it is `LISTED`, in the same way as a delisting, with a `DELISTED` row in `product_listing_events`. The product row is kept as a record and can never be listed again;
  3. cancels every ticket that is `OPEN` or `IN_PROGRESS` and linked to the item, as the SYSTEM identity, with the reason `ITEM_DELETED:` followed by the `inventory` id, and logs each as `TICKET_CANCELLED`;
  4. sets `deleted_at` and `deleted_by`, inserts an `inventory_item_events` row with the action `DELETED` (the SKU, the name, and the stock the item showed, for the record), and writes the `operation_logs` row.
- **A deleted item** leaves every list, the catalog for listing, and the list for opening a ticket. It cannot be listed, edited, stocked, written off, ordered, or linked to a new ticket, and every such request is refused as "the item no longer exists". Orders placed before the deletion stay as they are, because an order is final. It stays readable in the history that refers to it: old orders, stock transactions, tickets, and events. A deletion cannot be undone. To have the item again, add a new one, which gets a new SKU and may use the old name.

#### Item images

The usual practice for a back-office system applies: the file is kept outside the database, and the database holds only a path.

- **Upload:** a `MANAGER` or an `ADMIN` uploads the image when adding an item, or when editing an item that is not listed. The service layer accepts JPEG, PNG, and WebP only, up to 2 MB. It checks the real file type from the first bytes of the file, not from the name or the type the browser sends, and it refuses anything else with a clear message.
- **Storage:** the application renames the file to a random name (a UUID with the right extension) and never uses the name the person gave, which prevents path tricks and name clashes. In the first version the file is kept in a folder outside the web root that the application configures (`opsflow.image-dir`), and `inventory.image_url` holds the relative path, for example `items/3f2b8c1e....webp`. The storage can move to an object store later without changing the column, because the column holds a path and not a full address.
- **Serving:** the application serves the file through a controlled endpoint, or a static mapping of that one folder, with the correct content type and a cache header. Only the product page and the item lists show images.
- **Replacing and deleting:** a replaced image is not removed at once, and a deleted item keeps its image, because history refers to the item. Image files are kept for ever, like every other record.
- **Order of work:** the file is stored first and the row is written second, so a failed row insert leaves an unreferenced file and never a row that points to nothing.

#### Listing and delisting

- Only a `MANAGER` or an `ADMIN` lists or delists. Neither asks for a reason. Both are recorded.
- **The catalog.** Pressing List opens a catalog of the warehouse items that are not listed now, meaning items that were never listed and items that were delisted. It can be searched by the name of the item, and by nothing else. Items that are listed or deleted are not offered, and the service layer refuses them anyway. Only an item that exists in the warehouse can be listed.
- **Listing** asks for the price, which must be above zero, and shows the name, which starts as the name of the item and can be changed in the same dialog. In one transaction it creates the product the first time, or sets it to `LISTED` again, with the price, the name, `listed_at`, and `listed_by`, and it writes a `LISTED` row to `product_listing_events` and an `operation_logs` row. **Listing needs stock:** under a shared lock on the `inventory` row the service layer checks that `available_quantity` is above zero, and an item with no stock is refused with `NO_STOCK`, because a product nobody can buy should not be on the page. The rule applies at the moment of listing only: a listed product whose stock later falls to zero stays listed, and an order for it fails with `OUT_OF_STOCK`.
- **A listed product cannot be edited.** Its name and price are fixed. To change either, delist the product and list it again with the new values. Orders placed before keep the name and price they were placed under.
- **Delisting** sets the product to `UNLISTED` with `delisted_at` and `delisted_by`, writes a `DELISTED` row to `product_listing_events`, and cancels the tickets of the product, all in one transaction. A delisted product leaves the product page at once.
- **The time of a delisting is taken after the lock.** Delisting first locks the product row with `FOR UPDATE`, which waits for the orders that hold it shared, and only then takes the time of `delisted_at`. So every order that committed before the delisting has an earlier `created_at`. Taken before the lock, in the statement that then waits (MySQL fixes `NOW()` when a statement starts), the time could be earlier than orders that committed first. The same holds for every time written by a statement that may wait for a lock: lock first, then take the time.
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

Every value in the program that is a quantity must be valid before it is used. The service layer checks it first and refuses an invalid one without changing anything, and the database keeps its own checks as a second line. This rule covers the starting quantity of an item, the quantity of a stock-in or a write-off, the new quantity of a correction, and the quantity of an order line. A quantity is valid only when it is:

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
| Correction (type the quantity that was counted) | `ADJUST_IN` if the new number is higher, `ADJUST_OUT` if it is lower | `ADMIN`, `MANAGER`, `OPERATOR` |

- **Reason is required for every manual change.** Stock in, write-off, and correction all need a non-blank reason and the acting user. `SALE` comes from an order and needs no reason. The database enforces this with a `CHECK`, and the service layer validates it first.
- **A change must change something.** The quantity is greater than zero. Setting the quantity to the number it already has is refused and writes nothing: a correction whose counted number equals the number the person saw is refused before the transaction, because the request alone shows it.
- **Screen.** The inventory screen has one Modify button per warehouse item. An `OPERATOR`, a `MANAGER`, or an `ADMIN` who presses it chooses one of three: stock in (plus), write-off (minus), or Correction (a field to type the new quantity), and fills in the reason field. Save is enabled only when the quantity has changed and the reason is filled in.
- **Correction.** It is for a stock count that differs from the number in the system: the person types the number that was counted, where a stock in or a write-off would need the difference worked out by hand. The user types the new `available_quantity`. The request also carries the `available_quantity` the user saw. Inside the transaction the service layer locks the `inventory` row and compares it with the value seen. If they differ, someone else changed the stock in the meantime, and the request is refused with `STOCK_CHANGED` ("stock changed, please reload"). If they match, the difference becomes the transaction quantity, and the type follows its sign.
- **Write-off.** Under the same lock, `available_quantity >= q` must hold, otherwise the request is refused with `OUT_OF_STOCK`.
- **Stock in.** Under the same lock, the new quantity must fit the `INT` column, otherwise the request is refused with `INVALID_QUANTITY` (see Valid quantities).
- **A deleted item.** Under the same lock the service layer also checks that the item is not deleted, and refuses every manual change of a deleted item. For the person, a deleted item is gone from the warehouse: it cannot be stocked, ordered, listed, or linked, and adding it again means creating a new item and counting its stock by hand. The `inventory` row is kept only because orders, stock records, tickets, and events refer to it. This is different from delisting a product, which never touches the stock: the stock of an item and the product made from it only meet when an order is checked.
- **One transaction.** Each manual change locks the `inventory` row, updates it, inserts the `inventory_transactions` row with exact snapshots, and writes `operation_logs`, all together. It follows the same lock order, retry, and deadlock rules as the order flows.

### Orders

| Table | Responsibility |
| --- | --- |
| `orders` | Stores order-level information such as the ordering user, order number, total amount, and item count. An order has no status: it is complete and final when it exists |
| `order_items` | Stores the purchased product snapshot, quantity, and transaction price |
| `order_attempts` | Append-only record of every attempt to place an order, successful or not: who, when, which product (by id and by the name it had at the time), how many, the result, and how much stock was deducted |

A user can create many orders. Each order must contain one or more order items. Each `order_items` record references exactly one product, and an order contains at most one item per product (`UK(order_id, product_id)`).

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`
- `users` 1:N `order_attempts`, `products` 1:N `order_attempts`, and `orders` 0..1:N `order_attempts` (see Order attempts)

An order item should retain the product name or SKU and price as they were when the order was created. Later product updates must not change the meaning of historical orders.

Once an order is created, its items and amounts are frozen. The product, quantity, price, and composition of the items, as well as `total_amount` and `item_count`, must not be edited or deleted. An order is final: it is never edited and never cancelled. A wrong order is corrected through a ticket, a manual stock change, and a new order (see Correcting a wrong order). For the same reason `order_items` has `created_at` but no `updated_at`.

#### Order and ticket numbers

`orders.order_no` is `ORD-` followed by a random UUID without hyphens, for example `ORD-3f2b8c1e9a4d4b6f8e0c5a7d1b2c3d4e`. The application generates it in lowercase, once for each attempt to place an order, before the attempt starts. The number is returned in the result whether the attempt succeeds or fails, and a completed attempt keeps its number as the number of its order.

`tickets.ticket_no` is 8 random characters from `A-Z` and `0-9`, for example `K7Q2M9XA`. The application generates it when the ticket is created. It is short on purpose: people read it, say it, and type it to name a ticket, for example to open a follow-up. The database checks the format (`chk_tickets_ticket_no`), and the form turns what a person types into upper case. The unique constraint on each column stays as the final guard: on a collision, the insert fails and the application generates another number and tries again, up to the attempts in Operating Parameters.

#### Order lifecycle

An order has no status. It exists only if it was placed and its stock was deducted in the same transaction, so a placement that fails leaves no order, and an order that exists is complete and final. It is never edited, cancelled, or deleted. There is no payment status because payment is outside the current system boundary.

Additional `orders` fields:

- `created_at` is the time the order was placed and completed.
- `idempotency_key` is optional and unique per user (`UK(user_id, idempotency_key)`), so a repeated submission returns the original order.
- `remark` is optional free text.

#### Order attempts

An order is the record of a placement that succeeded. The attempts that did not succeed leave no order, but they are not lost: every attempt is recorded in `order_attempts`, so that the log of a product shows who tried to order what, when, and what came of it, whether or not stock was deducted.

- **One row for each product line of an attempt:** the order number (shared by the lines of one attempt), the user, the product, the name of the product at the time of the attempt (`product_name`, read from the product row; the price is not kept, because a completed attempt has its order items and a refused one sold nothing), the quantity, the result, the stock deducted, and the time. `order_id` is set only when the attempt completed. The product page orders one product at a time, so an attempt normally has one row.
- **The result** is `COMPLETED`, `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, `OUT_OF_STOCK`, `INVALID_QUANTITY`, or `BUSY`. Every line of a refused attempt carries the result of the whole attempt. A refusal of the permissions is not recorded, because the program refuses it before it looks at the order, nor is a request whose shape is wrong (no lines, a product named twice, a missing name or price), which is no attempt, and a repeated submission with the same idempotency key returns the original order and adds no row.
- **The stock deducted** is the quantity for a completed row and 0 for a refused one. The database checks that a completed row has its order and deducted exactly what it ordered, and that a refused row has no order and deducted nothing (`chk_order_attempts_outcome`).
- **The quantity** is stored as it was sent when it is a whole number that fits the column, and as 0 otherwise, so an `INVALID_QUANTITY` row may hold 0, a negative number, or a number above the limit.
- **When it is written.** A completed row is written in the transaction of the order, so a completed attempt is never missing from the log and never recorded without its order. A refused row is written after the order transaction has rolled back, in a short transaction of its own that holds no lock of the order, so a refusal leaves a record and changes nothing else. `BUSY` is written after the last retry. If the write of a refused row fails, the result is still reported to the user and the failure goes to the application log: the record of a refusal is best effort, while the record of a success is not. A line whose product does not exist cannot be recorded, because there is no product to point at.
- **Append-only.** The application account has `SELECT` and `INSERT` only. The index `(product_id, created_at)` serves the product log (see Logs).
- **The ticket of an order exception is always opened by a person.** The program opens no ticket by itself. The log lets the person see what happened, and a ticket names the number of an order attempt, a placed order or a refused one (see Creating a ticket on items).

### Tickets

| Table | Responsibility |
| --- | --- |
| `tickets` | Stores tickets: the fixed creator, the current assignee, the resolver, the status, the ticket it follows, and who closed or cancelled it |
| `ticket_assignment_history` | Records every change of assignee: taking, assigning, reassigning, and releasing |
| `ticket_inventory` | Links a ticket to the inventory records it is about, written when the ticket is created, so one ticket can cover a batch of products. Append-only. |

The ticket creator is fixed in `created_by`. `assignee_id` contains only the current assignee, and a ticket has at most one current assignee at a time. Each change of assignee creates a new `ticket_assignment_history` record instead of overwriting the history. A record holds the previous assignee (`from_user_id`, empty when the ticket came from the pool), the new assignee (`to_user_id`, empty when the ticket goes back to the pool), the acting user (`changed_by`), the reason, and the time.

`tickets.type` uses `ORDER_EXCEPTION`, `INVENTORY_EXCEPTION`, or `OTHER`. It is required and has no database default, so a missing value fails instead of silently producing a wrong one. The program must set the type when it opens a ticket by itself. `tickets.status` uses `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, or `CANCELLED`, and defaults to `OPEN`. `tickets.source` is `SYSTEM` or `MANUAL` (see Where a ticket came from). `order_no` is optional, except that an `ORDER_EXCEPTION` ticket must name an order number. It is the number of an order attempt, a placed order or a refused one, so that a person can open a ticket about an attempt that failed. The database enforces that a number is present with a `CHECK` (`chk_tickets_order_required`), because it only needs the same row. A ticket of any other type may name an order too.

#### When to open a ticket

A ticket is for a problem that the person who found it cannot, or may not, solve alone, and for the exceptions the system finds by itself. A problem that one person can finish directly is not a ticket. A wrong order is a ticket, because an operator cannot undo an order alone (see Correcting a wrong order).

- **Automatic tickets** are not used today (see Exceptions).
- **Manual tickets** are opened by a person for a problem that needs someone else, such as a stock count that disagrees with the records when the finder cannot set the quantity. This is a usage rule. The database and the program do not enforce it, so it is kept in this document and in the screen text.
- `OTHER` is for a problem that needs someone else but is neither an order nor a stock problem. It is not a place for notes.

#### Creating a ticket on items

A person with the right to create tickets (see the Permission Matrix) opens the ticket pool screen and presses the Create ticket button. The form works in this order:

1. **Choose the items, if any.** The person picks zero or more warehouse items from a list that works like the catalog for listing: it shows the items that are in the warehouse, listed or not, and it can be searched by name and by nothing else. Several items can be chosen, and none is also allowed. A ticket that is not about stock, such as one of type `OTHER`, needs no item.
2. **Fill in the details**, the type, the title, and the description, below the chosen items. There is also an optional field for the number of an earlier ticket. When it is filled in, the new ticket is a follow-up to that ticket (see Follow-up tickets). Leaving it empty makes an ordinary ticket. A second optional field takes an order number, which is required when the type is `ORDER_EXCEPTION`. A person reads the number in the result shown when the order was placed or refused, or in the order records (see Logs), because the program has no order list. The number of a refused attempt is accepted too, so a person can report a refusal that looks wrong.
3. **Confirm.** The program creates the ticket in one transaction. It locks the creator's user row, counts the open tickets the creator holds against the limit, finds the earlier ticket by the number the person typed, if there is one, finds the order by the number typed, if there is one (an order is never changed or deleted, so no lock is needed), inserts the ticket, then locks each chosen item with `SELECT ... FOR SHARE` in ascending `inventory` id, checks that it still exists and is not deleted, and inserts the links. If any chosen item is gone, the whole request is refused: no ticket and no link is written, and the screen shows which items changed, for example "Widget no longer exists". The details the person typed are kept, and the item list is reloaded.

| Result | Shown to the person | When |
| --- | --- | --- |
| `CREATED` | The ticket was opened, with its number | The ticket and its links were written |
| `FOLLOW_NOT_FOUND` | No ticket has that number | The number in the follow-up field belongs to no ticket |
| `FOLLOW_NOT_ALLOWED` | That ticket cannot be followed yet: only a resolved or archived ticket can | The ticket exists but is `OPEN`, `IN_PROGRESS`, or `CANCELLED` |
| `ORDER_NOT_FOUND` | No order has that number | The number in the order field belongs to no order attempt, or the type is `ORDER_EXCEPTION` and the field is empty |
| `ITEM_CHANGED` | The named item no longer exists, please choose again | A chosen item was deleted after the list was loaded |
| `LIMIT_REACHED` | You already have 99 open tickets | The open limit of the creator is reached |
| `NOT_ALLOWED` | You may not create tickets | The account is not usable or its role does not allow it |
| `BUSY` | The system is busy, please try again | The retries on a deadlock or a lock wait were used up. Nothing was created. |

The shared lock on each item is what keeps a ticket from being linked to an item that is being deleted: a deletion needs the item exclusively and cancels the tickets linked to it after it holds the lock, so a ticket that committed first is cancelled, and a ticket that comes later is refused (see Concurrency by Module).

#### Where a ticket came from

There is no priority. A ticket is either opened by the system or opened by a person, and `tickets.source` says which: `SYSTEM` for a ticket the program opens, `MANUAL` for a ticket a person opens. It is set when the ticket is created and never changes. A `SYSTEM` ticket has the SYSTEM identity (see The SYSTEM identity) as `created_by`, and a `MANUAL` ticket has a real user. The two fields say the same thing on purpose, so that a screen can show or filter by the class without looking up the creator. The database cannot check the two against each other, because a `CHECK` cannot look at another table, so the service layer keeps them in step and the tests cover it (T-33). The class differs only in how the screen may show it, for example a different background colour. How lists are ordered by it is decided (see Ticket list order).

#### Ticket screens and who sees what

Tickets are shown on one screen for each stage. Which tickets a screen lists depends on the role.

| Screen | Tickets | `ADMIN`, `MANAGER`, `VIEWER` | `OPERATOR` | Status label |
| --- | --- | --- | --- | --- |
| Pool | `OPEN` | All | All | Waiting to be taken |
| In progress | `IN_PROGRESS` | Everyone's | Only those assigned to them | In progress |
| Resolved | `RESOLVED` | All | Only those they resolved (`resolved_by` is them) | Resolved |
| Archive | `CLOSED` | All | No archive screen | Archived |
| Cancelled | `CANCELLED` | All | Only those they created or last held | Cancelled |

- **A list row** shows the ticket number, the type, the title, the creator, the creation time, and the status label. Everything else is in the detail view, which opens when a row is clicked: the description, the order, the linked items, the follow-up links, the assignment history, the resolution, and the cancel reason. The detail opens only for a ticket the role can see on a list. A follow-up link to a ticket the role cannot see shows the number only.
- **Who sees what is enforced by the service layer**, in the list and detail endpoints, by filtering on the user id for an `OPERATOR`. Hiding a screen in the frontend is not enough.
- An `OPERATOR` who opens a ticket sees it in the pool while it is `OPEN`. When someone else takes it, it leaves the operator's lists, because the operator neither holds nor resolved it. A manager, an administrator, or a viewer still sees it.
- A ticket the operator resolved leaves the operator's Resolved screen when it is archived, because an operator has no archive screen.
- The follow-up field looks up any ticket number, whether or not the creator can see that ticket. Only its status decides (see Follow-up tickets).
- The lists use the indexes in Initial Constraints. The operator's In progress screen uses `tickets(assignee_id, status)`, and the Resolved screen uses the index on the `resolved_by` foreign key.

#### Ticket list order

Every role that can view tickets can choose the order of a ticket list. The default is newest first, and the user can switch to oldest first. Whichever direction is chosen, tickets with `source` `SYSTEM` are always shown before tickets with `source` `MANUAL`: the oldest system ticket comes before the newest manual ticket, and the same holds in the other direction. Inside each of the two groups the chosen time order applies. This gives system tickets a higher place on every list without a priority.

The time that orders a list is the one the list is about: the creation time for the pool and the other work lists, the archive time on the archive screen, and the cancellation time in the cancelled area. The choice is only a screen setting and is not stored in the database. The rule needs no extra field, because the order is `source` first and then the time.

#### Fields fixed after creation

A ticket is fixed once it is created. Its `title`, `description`, `type`, `source`, `order_no`, `follows_ticket_id`, `created_by`, and its inventory links never change. Only the fields that the transitions set change: the status, the assignee, `resolution`, `resolved_by`, `resolved_at`, `closed_by`, `closed_at`, `cancel_reason`, `cancelled_by`, and `cancelled_at`. Something that was missed or opened wrongly is handled by cancelling the ticket and opening a new one, so no edit history has to be kept. The service layer enforces this, and the database does not (T-33).

#### Ticket status

| Status | Meaning |
| --- | --- |
| `OPEN` | The ticket is in the pool: nobody holds it. It never has an assignee. |
| `IN_PROGRESS` | Someone holds it and is working on it. It always has an assignee. A ticket enters this status the moment someone takes it or is assigned it, like accepting a quest. |
| `RESOLVED` | The assignee says the problem is solved and has written the resolution. It waits to be archived. |
| `CLOSED` | The problem is solved and the ticket is archived. `CLOSED` does not mean "given up". |
| `CANCELLED` | The ticket was withdrawn before it was resolved, for example because it was opened by mistake or duplicates another one. A reason is required. It leaves the pool and every work list and is shown only in its own cancelled area (see Ticket screens and who sees what). Nobody can take, assign, or revive it. |

`CLOSED` and `CANCELLED` are final. A ticket in either status is frozen: its status, assignee, and links no longer change. A ticket that leaves `IN_PROGRESS` for `RESOLVED`, `CLOSED`, or `CANCELLED` keeps its last assignee, as a record of who held it.

#### Status transitions

A ticket is never reopened. If the problem comes back or continues, a new ticket is opened that follows the old one (see Follow-up tickets). Only the transitions below are allowed. The service layer enforces them, and the database does not (see Decisions in database-v1.6.md).

| From | To | Who | Must have | Also sets |
| --- | --- | --- | --- | --- |
| `OPEN` | `IN_PROGRESS` | **Take**: any eligible assignee, for themselves. **Assign**: `MANAGER` or `ADMIN`, to an eligible assignee. Only an `ADMIN` may assign to a user who holds `ADMIN`. | The ticket is still `OPEN`, and the new assignee is below the in-progress limit | `assignee_id`, and a history row from empty to the assignee |
| `IN_PROGRESS` | `OPEN` | **Release**: the assignee, for their own ticket, or a `MANAGER` or an `ADMIN` removing the assignee. The system does it when the assignee's account is no longer usable. | A non-blank reason. The system writes a fixed reason. | Clears `assignee_id`, and a history row from the assignee to empty |
| `IN_PROGRESS` | `RESOLVED` | The assignee, `MANAGER`, `ADMIN` | A non-blank `resolution` | `resolved_by`, `resolved_at` |
| `RESOLVED` | `CLOSED` | A `MANAGER` or an `ADMIN` at any time, or the system 7 days after `resolved_at` | | `closed_by` (the user, or the SYSTEM identity), `closed_at` |
| `OPEN` or `IN_PROGRESS` | `CANCELLED` | `MANAGER` and `ADMIN` for any ticket. `OPERATOR` only for their own: a ticket assigned to them, or one they created while it is still `OPEN`. `VIEWER` never. The system also cancels the tickets of a product when the product is delisted (see Listing and delisting). | A non-blank reason | `cancel_reason`, `cancelled_by`, `cancelled_at` |

- **Releasing is not cancelling.** A released ticket goes back to the pool for someone else to take, and the problem is still open. A cancelled ticket is withdrawn for good, because it was a mistake or a duplicate.
- A `RESOLVED` ticket cannot be cancelled. It can only be archived.
- **Cancelled tickets are set apart.** A `CANCELLED` ticket leaves the pool and every work list, and appears only in its own cancelled area, which every role that can view tickets can open (an `OPERATOR` sees only the cancelled tickets that concern them). Nobody can take, assign, or bring it back. It is kept and not deleted, because the logs, the follow-up links, and the history refer to it.
- "Own" means the ticket's current assignee, or its creator while the ticket is still `OPEN`. Once someone else takes a ticket it is no longer the creator's for cancelling. To withdraw a ticket someone else holds, an operator asks a `MANAGER` or an `ADMIN`.
- Reassigning an `IN_PROGRESS` ticket to another person is not a status change; see Reassigning a ticket.
- A ticket the program may open in the future starts as `OPEN`, in the pool, with the SYSTEM identity as its creator.
- Every transition is made with a conditional update, `WHERE id = ? AND status = ?`, so only one person can make it. This is the status gate. See Concurrency by Module.

#### Who may take a ticket

An **eligible assignee** is a usable account (`status` is `ACTIVE` and `deleted` is false) that holds at least one of `OPERATOR`, `MANAGER`, or `ADMIN`. A `VIEWER`, a user with no role, a disabled user, a soft-deleted user, and the SYSTEM identity are not eligible. The check applies every time a ticket gets an assignee, and it is made inside the transaction that changes the assignee:

- a person takes a ticket from the pool;
- a `MANAGER` or an `ADMIN` assigns a ticket from the pool, or reassigns an `IN_PROGRESS` ticket, to someone.

**A manager cannot give a ticket to an administrator.** An actor who does not hold `ADMIN` may assign or reassign a ticket only to an eligible assignee who does not hold `ADMIN`: an operator, or a manager, another manager included. An actor who holds `ADMIN` may choose any eligible assignee. An administrator may still take a ticket from the pool. The roles of the new assignee are read under the same lock on their user row as the eligibility check.

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

Whoever holds a ticket controls it. Only the assignee, and a `MANAGER` or an `ADMIN`, can move it forward, and an `OPERATOR` can cancel only their own. So only a `MANAGER` or an `ADMIN` can take a ticket away from its assignee. An `OPERATOR` cannot take, or ask for, a ticket that someone else holds. There is no request process: the manager or administrator decides and acts directly. The ticket keeps its number, status, and links. Only the assignee changes, and the ticket continues in the normal flow. From that moment the previous assignee can no longer move or cancel it.

A reassignment is valid only when all of these hold, checked inside one transaction:

- the actor is a usable `MANAGER` or `ADMIN`;
- the ticket is `IN_PROGRESS`. A ticket in the pool is assigned, not reassigned, and a `RESOLVED`, `CLOSED`, or `CANCELLED` ticket cannot change hands;
- the new assignee is an eligible assignee, does not hold `ADMIN` unless the actor does (see Who may take a ticket), is not the current assignee, and has fewer than 99 tickets in progress (a ticket moved in counts as in progress);
- the actor saw the current assignee. The request carries the assignee shown on the screen, and under the ticket row lock it is compared with the real one. If someone changed it in the meantime, the request is refused and the screen is reloaded;
- a reason is given. The service layer requires it to be non-blank, so that every change of hands can be explained later.

A release is valid when the ticket is `IN_PROGRESS`, the actor is its assignee or a usable `MANAGER` or `ADMIN`, and a non-blank reason is given. The ticket goes back to the pool. The assignee cannot be taken away silently: every reassignment and every release writes one `ticket_assignment_history` row and one `operation_logs` row, in the same transaction.

**When an assignee's account stops being usable.** When a user is disabled or soft-deleted, every `IN_PROGRESS` ticket assigned to them goes back to `OPEN` automatically, in the same transaction as the disabling or the soft deletion. Each one gets a history row with `changed_by` the SYSTEM identity and a fixed reason, and an `operation_logs` row. Tickets that are already `RESOLVED` keep their assignee. If the user is enabled again, the tickets do not come back, because someone else may have taken them. The `OPEN` tickets that the user created are left as they are: the creator raised the problem but is not the one who handles it, so the problem is not ignored while it waits in the pool.

#### Linked inventory

`ticket_inventory` records which stock the ticket is about. One ticket can link to many inventory records, and an inventory record can appear on many tickets. A ticket with no link is valid, for example an order exception or a general problem.

- A link is unique per ticket and inventory record (`UK(ticket_id, inventory_id)`). An item that is deleted cannot be linked to a new ticket. A ticket already linked to an item is cancelled when the item is deleted, if it is still `OPEN` or `IN_PROGRESS`.
- **Links are written only when the ticket is created**, in the same transaction as the ticket, so one ticket can cover a batch of products from the start. They are never added later. A new problem is a new ticket. If the new problem is unrelated, it is a plain new ticket, and it does not name the earlier product. If it relates to an earlier ticket, it is a follow-up ticket (see Follow-up tickets). The table is append-only, so the application account has only `SELECT` and `INSERT` on it.
- The service layer should ask for at least one link on an `INVENTORY_EXCEPTION` ticket, because that is the point of the link, and may allow any ticket to have links. The database does not require it, because a `CHECK` cannot look at another table.
- The inventory record is the target, as in `inventory_transactions`, and the product is reached through it.
- Because links come only with the ticket, a ticket that is `CLOSED` or `CANCELLED` has the links it was created with, and nothing else about links can change.

#### Follow-up tickets

`tickets.follows_ticket_id` is empty for an ordinary ticket. When a problem continues after a ticket was resolved, the person opens a new ticket and types the number of the earlier one in the optional follow-up field of the form, and the new ticket records it. The program looks the number up in `tickets.ticket_no`, inside the transaction that creates the ticket, and the ticket is refused if no ticket has that number or if that ticket cannot be followed. This keeps the history of one problem connected across tickets without reopening any of them.

- The earlier ticket must be `RESOLVED` or `CLOSED`. A ticket that is still `OPEN` or `IN_PROGRESS` cannot be followed: an `OPEN` ticket waits in the pool, and an `IN_PROGRESS` ticket stays with its assignee, and only a `MANAGER` or an `ADMIN` can hand it to someone else (see Reassigning and releasing a ticket). A `CANCELLED` ticket was withdrawn, so nothing follows it.
- The link is set when the new ticket is created and never changes. A ticket follows at most one earlier ticket, and an earlier ticket can have many follow-ups. A follow-up can itself be followed, so a chain forms.
- The link is visible to everyone who can view tickets, in both directions: a follow-up shows the ticket it follows, and an earlier ticket lists its follow-ups. It is visible while the new ticket waits in the pool, while it is worked on, and after it ends.
- The database rejects a link to a ticket that does not exist, and a ticket that is followed cannot be deleted. A ticket cannot follow itself, because the link is written when the ticket is created and the ticket does not exist yet.
- The follow-up is an ordinary ticket in every other respect: its own number, assignee, status, and resolution.

#### Archive

`CLOSED` is the archive. A ticket is archived in three ways. A `MANAGER` or an `ADMIN` can archive a `RESOLVED` ticket at any time, one by one or in a batch. The system archives a `RESOLVED` ticket 7 days after it was resolved. The system also archives when 99 resolved tickets are waiting in the whole system: when the number of `RESOLVED` tickets reaches 99, it archives all of the `RESOLVED` tickets. The two numbers are operating parameters. The system archives through the same status gate, ticket by ticket, with the SYSTEM identity as `closed_by`. The archive has its own screen, which lists `CLOSED` tickets and is open to `ADMIN`, `MANAGER`, and `VIEWER`. An `OPERATOR` has no archive screen (see Ticket screens and who sees what).

A batch archive and the system job move each ticket in its own transaction through the status gate, so a ticket changed by someone else in the meantime is skipped and the rest still succeed. The system job runs on a schedule and checks both the 7-day rule and the count of 99. A separate approval module for managers and administrators is a later idea (see [future-ideas.md](../future-ideas.md)), and nothing here depends on it.

#### No comments

A ticket has no comments in the first version. Nobody can write a note on a ticket, so there is no comment table and no rule about editing or correcting notes. The only text a handler writes is the `resolution` that `RESOLVED` requires. Anything that must be said before that is said outside the program, or in a new ticket. Comments are an idea for later (see [future-ideas.md](../future-ideas.md)); they would come back with a new migration.

### Auditing

| Table | Responsibility |
| --- | --- |
| `operation_logs` | Records important business actions, actors, target objects, and relevant state changes, each filed under one category for the audit page |

The initial version uses a generic target reference. `target_type` and `target_id` identify an order, inventory record, ticket, or another business object. The application maintains this polymorphic relationship because it cannot be represented by one conventional foreign-key constraint. `before_data` and `after_data` store the changed state as JSON. `operator_id` is empty for system actions.

#### Categories

Every row of `operation_logs` has a `category`, the area the audit page files it under. The application sets it when it writes the row, from one table of actions kept in the code, so a row cannot be written without one: a new action must be added to the table, and a test fails when an action has no category. The database requires the value to be one of the six below (`chk_operation_logs_category`) and not empty.

| Category | `category` | Actions |
| --- | --- | --- |
| Accounts | `ACCOUNT` | `USER_REGISTERED`, `USER_STATUS_CHANGED`, `USER_DELETED`, `PASSWORD_RESET`, `LOGIN_LOCKED` |
| Roles and permissions | `ROLE` | `ROLE_GRANTED`, `ROLE_REVOKED` |
| Tickets | `TICKET` | `TICKET_CREATED`, `TICKET_ASSIGNED`, `TICKET_REASSIGNED`, `TICKET_RELEASED`, `TICKET_RESOLVED`, `TICKET_CLOSED`, `TICKET_CANCELLED` |
| Stock | `STOCK` | `STOCK_RESTOCK`, `STOCK_WRITE_OFF`, `STOCK_ADJUSTED` |
| Warehouse items | `ITEM` | `ITEM_CREATED`, `ITEM_UPDATED`, `ITEM_DELETED` |
| Products | `PRODUCT` | `PRODUCT_LISTED`, `PRODUCT_DELISTED` |

Each row has exactly one category. An action that causes others writes each row under its own category: deleting an item writes `ITEM_DELETED`, a `PRODUCT_DELISTED` row if it had a listed product, and a `TICKET_CANCELLED` row for each ticket it cancels, and the reason in `remark` ties them together. Orders are the seventh area of the audit page but have no category: their record is `order_attempts`, and the audit page reads it directly (see Audit log page). `category` is separate from `target_type`: `target_type` says what kind of object the row points at, and `category` says where the row is filed.

#### Labels in audit rows

An audit row must read on its own, without a join, and must still read after the object is renamed or deleted. So every row carries a **label** in its JSON, written even when the label did not change: in `after_data`, or in `before_data` when the row has no `after_data` (a deletion). The label of each category:

| Category | Label fields | Notes |
| --- | --- | --- |
| Accounts | `username` | On every row, not only on sign-up |
| Roles and permissions | `roleCode`, and `username` of the user for a grant or a revocation | A role row about a user names both |
| Tickets | `ticketNo` | On every ticket row. A row that names another user (the assignee, the previous assignee, the one who resolved or cancelled) also carries that user's `username`, for example `assigneeUsername` |
| Stock | `sku` and `name` | Already on every stock row |
| Warehouse items | `sku` and `name` | On every item row, including an update and a deletion |
| Products | `name`, and the `sku` of its item | On every product row |

`ticketNo`, `username`, and the other labels are copies taken at the time of the action, which is the point: the row shows what the object was called then. The labels are part of the formats below; the examples in the tables show the fields that changed and leave out a label where it would only repeat the target. The assignment history of tickets (taking, assigning, reassigning, releasing) is shown in the Tickets area of the audit page through the `TICKET_ASSIGNED`, `TICKET_REASSIGNED`, and `TICKET_RELEASED` rows, and on the ticket detail from `ticket_assignment_history`.

#### Role management

**The roles are fixed.** OpsFlow has exactly the four seeded roles, `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`. The program has no way to create a role, to change the `code`, `name`, or `description` of one, or to delete one: no screen, no endpoint, and no console command. A new role is a backend change: a migration that inserts the `roles` row, together with the code that gives the role its permissions (see Permission Matrix). The application account therefore never inserts, updates, or deletes a row of `roles`, and has neither `INSERT` nor `DELETE` there. It keeps the `UPDATE` privilege only because MySQL allows `SELECT ... FOR UPDATE` to an account that may change the table, and Administrator protection locks the `ADMIN` row.

What the program manages is **which of the four roles a user holds**. A user may hold several roles, or none. The one operation is subject to Administrator protection and to Authority over roles and accounts, and it writes its `operation_logs` rows in the same transaction as the change, so that it rolls back if a log insert fails. The formats are in Role grant and revocation logs.

| Operation | Rule |
| --- | --- |
| Replace a user's roles | Sets the user's roles to a given list in one transaction, following Steps of a change to a user. The request carries the roles the actor saw, and is refused when they are no longer the user's roles. Adding a role needs a usable account, and every role that is added must exist. A list that only removes roles is also accepted for a disabled user. Removing `ADMIN` from a user is subject to Administrator protection. Roles the user already holds and that stay in the list are left alone and not logged. Each role that is removed writes `ROLE_REVOKED`, and each role that is added writes `ROLE_GRANTED`. The rows of one replacement carry `replace roles` in `remark`. A list equal to the current roles changes nothing and writes no log. An empty list leaves the user with no role. |

#### Authority over roles and accounts

Four roles are seeded: `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`, and they are the only roles (see Role management). `ADMIN` and `MANAGER` are **protected roles**: only an administrator may give them to a user or take them away. `OPERATOR` and `VIEWER` are **below manager**, and so is any role that a later backend change adds, unless that change says otherwise.

`ADMIN` is the highest authority and is meant for major changes only, for the team that builds and maintains the system. `MANAGER` is the role for the customer's own staff: it manages employees and does the daily work. That is a working convention, not a restriction: an `ADMIN` account can do everything a `MANAGER` can.

| Action | ADMIN | MANAGER |
| --- | --- | --- |
| Create, update, or delete a role | No one | No one |
| Grant, revoke, or replace `ADMIN` or `MANAGER` on another user | Yes | No |
| Grant, revoke, or replace roles below manager on another user | Yes | Yes |
| Change the status of, or soft-delete, another user who holds `ADMIN` or `MANAGER` | Yes | No |
| Change the status of, or soft-delete, any other user | Yes | Yes |
| Disable or soft-delete their own account, or change their own roles | No | No |
| Products, inventory, orders, and tickets | Yes | Yes. See Permission Matrix |
| Delete or edit audit logs | No one | No one |

How the rules are applied:

- The service layer checks the acting user's roles and the target user's roles in the same transaction as the change, with both user rows locked (see Steps of a change to a user). The check counts a role only when the acting account is a usable account.
- **Nobody changes their own account.** No user, an administrator included, may disable or soft-delete their own account, or grant, revoke, or replace their own roles. The service layer refuses a request whose target is the acting user. Another administrator must make the change. This stops an administrator from locking themselves out by a slip, and a manager from removing their own limits.
- A manager cannot change a user who holds `ADMIN` or `MANAGER`. Without this, a manager could disable an administrator or another manager.
- When a manager replaces a user's roles, the list may not add or remove `ADMIN` or `MANAGER`.
- Only `ADMIN` can appoint or dismiss a manager or an administrator. Administrator protection still applies to every operation on `ADMIN`.
- In the code these rules are two permissions and a mark on the roles, so that a later role fits in without a change to the rules (see [Permissions](../backend/backend-design.md#permissions)): `ROLE_ASSIGN` opens the granting and revoking of roles, and `ROLE_ASSIGN_PROTECTED` is needed to give or take away a protected role and to change the roles of a user who holds one. `USER_MANAGE` opens disabling, enabling, and soft-deleting, and `USER_MANAGE_PROTECTED` is needed when the user holds a protected role. Each role says whether it is protected.
- Nobody can delete or edit audit logs, `ADMIN` included, because the application account has only `SELECT` and `INSERT` on `operation_logs`. The database enforces this, not the service layer.

#### Users without roles

A user with no `user_roles` row is a valid state, and the database does not require at least one role. OpsFlow is an internal program: apart from the first administrator, accounts are created through the normal sign-up flow of a web application. The user sets their own password, is logged in by the sign-up itself, and starts with `status` `ACTIVE` and no role, so the first page the user sees is the pending page. Nothing is allowed until an administrator or a manager grants a role. A user can also end up with no role when the last role is revoked, when a replacement list is empty, or when a role is deleted. A permission check returns no permissions for such a user.

A user with no role may log in and gets a token. After every login the user first passes a pending page that checks the account:

| Check | What the page shows |
| --- | --- |
| The user has no role | A message that the account is waiting for an administrator to assign permissions |
| The user has at least one role | Nothing: the page is skipped and the application opens normally |

The check runs again on refresh and on the next login, so a user whose role was granted in the meantime, or before logging in, goes straight in. A protected endpoint answers a user with no role with `ACCOUNT_NO_ROLE`. This is a backend and frontend behavior, not a database rule.

A disabled account never reaches the pending page: it gets no token, and its message is shown on the login page (see Disabled accounts under Login security and account recovery).

#### First administrator

A new user has no role and only an administrator or a manager can grant one, and the first manager is appointed by an administrator, so the first administrator cannot be made through the program's screens. It is made on the server, with a console command of the backend application, so that no page and no endpoint has to exist for it.

- **How it runs.** The person who sets up the server runs the command from the console (`java -jar opsflow-backend.jar create-admin`, with the name of the built jar; see Running in the backend design). The command starts the application without the web server and asks for the username, the email, and the password, and asks for the password twice with the typed characters hidden. It refuses to run without a real terminal, for example when its input is a pipe or a file, because the password could then not be typed hidden. It checks the values with the same rules as sign-up, hashes the password, and in one transaction locks the `ADMIN` row in `roles`, creates the user with `status` `ACTIVE`, grants `ADMIN`, and writes a `USER_REGISTERED` row and a `ROLE_GRANTED` row, both with an empty `operator_id` and the `remark` `console`, because the person at the console is not a logged-in user. The `ADMIN` role must already exist, so it runs after the roles are seeded. It fails with a clear message when a value is invalid, the username or the email is already used, or the roles are missing.
- **The password is typed once and kept nowhere.** It is not an environment variable, not a file, and not a log line, so it cannot remain in a shell history or a configuration, and no forced change of password is needed. The earlier idea of a flag for it was dropped, and `users` has no `must_change_password` column.
- **It can run again at any time.** Whoever can run the command already controls the server, so it needs no check beyond the rules above. It is the way to make another administrator, for example when every administrator has lost access. It is not reachable over HTTP.
- **Later accounts** sign up through the normal flow, set their own password, and wait for a role.
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

**Login steps.** The password check is slow on purpose, so it stays outside every transaction (see No slow work inside a transaction in the backend design). A password reset can therefore commit between the check and the moment the result is recorded. The steps close that gap:

1. With no transaction, read the user by username: the id, `password_hash`, `status`, `deleted`, `is_system`, and the lock fields. An unknown username and a soft-deleted account get the general login error (`UNAUTHENTICATED`, 401). The SYSTEM identity is refused here. In these cases the password is still checked, against a decoy hash, so that the time of the answer does not show whether the username exists. An account whose `locked_until` is in the future gets `LOGIN_LOCKED`, and the password is not checked.
2. Still with no transaction, check the password against the hash that was read.
3. Open a short transaction and lock the user row with `SELECT ... FOR UPDATE`. Read `password_hash`, `status`, `deleted`, and the lock fields again under the lock, and decide in this order:

| What the locked row shows | Result | What is recorded |
| --- | --- | --- |
| `password_hash` is not the hash that was checked in step 2 (a reset committed in between), or the account is now soft-deleted | The general login error, `UNAUTHENTICATED`. No token, whatever step 2 found | Nothing. The check was made against a password that is no longer the current one, so it is neither a success nor a failure, and the count that the reset has just cleared stays cleared |
| `locked_until` is in the future (other attempts locked the account in between) | `LOGIN_LOCKED` | Nothing |
| The password was wrong | The general login error, `UNAUTHENTICATED` | The failure: the count, `last_failed_login_at`, and `locked_until` at a threshold, with `LOGIN_LOCKED` logged when the account becomes locked |
| The password was right and `status` is `DISABLED` | `ACCOUNT_DISABLED` (403). No token | Nothing. It is not a successful login, so the count and `last_login_at` stay as they are |
| The password was right and the account is usable | A token, whose issue time is taken inside this transaction and is always later than `password_changed_at`: a login in the same millisecond as the reset before it gets the next millisecond. The token carries the new `session_id` | The success: the count and `locked_until` cleared, `last_login_at` set, and a new random `session_id`, which ends every earlier login of the account (see One signed-in device) |

A password reset updates the same user row, so it holds the same lock, and one of the two always waits for the other. If the reset commits first, the login sees a different hash and is refused. If the login commits first, the reset sets `password_changed_at` at or after the issue time of the token, and the token is refused on its next request, because a token is accepted only when it was issued after `password_changed_at`. In both cases nobody stays logged in with the old password. The hash is compared, and not `password_changed_at`, because the hash is exactly what step 2 relied on.

**Disabled accounts.** An account with `status` `DISABLED` is handled the same way at login and afterwards:

| Case | What happens |
| --- | --- |
| The account is already disabled and its owner logs in | With the right password, the login answers `ACCOUNT_DISABLED` (403) and issues no token. The frontend stays on the login page and shows "This account has been disabled. Please contact an administrator." With a wrong password, the answer is the general login error and the failure counts as on any account, so only a person who knows the password learns that the account is disabled |
| The account is disabled while its owner is logged in | The next request finds the account disabled in the token check and answers `ACCOUNT_DISABLED` (403). The frontend clears its login state, goes back to the login page, and shows the same message |
| The account is enabled again | The user logs in again. The frontend dropped the token when it saw `ACCOUNT_DISABLED`. The server keeps no list of tokens, so a token that somebody kept would be accepted again until its 12 hours end, unless the account logs in again in between; a new login or a password reset ends it at once |
| The account is usable and has no role | The login succeeds, and the pending page shows that the account is waiting for an administrator to assign permissions (see Users without roles) |

**Password rule.** A password is 8 to 24 characters and contains at least one letter and at least one digit. The same rule applies to sign-up and to the new password of a recovery. A new password may not equal the current one: the service layer compares it with the stored hash, so no password history is kept. Earlier passwords may be used again, and common weak passwords are not blocked. The API limit of 24 is checked before hashing, so the column length of `password_hash` does not matter.

**Username rule.** A username is 3 to 64 characters from letters (`A-Z`, `a-z`) and digits. A space or any other character is refused. Two usernames that differ only in case are the same username (see Strings), and `system` is taken by the SYSTEM identity. The rule is checked at sign-up, and by the console command that creates an administrator.

**Email codes.** Sign-up and password recovery both use a one-time code sent to the email address, stored in `email_verification_codes`.

- A code has 6 digits. It is valid for 10 minutes, accepts at most 5 wrong entries, and a new code for the same email and purpose cannot be requested within 60 seconds. Requesting a new code marks the earlier unused ones consumed, so only the latest code works. A correct entry sets `consumed_at`.
- **A code is stored as a keyed hash**, never as the code itself: `code_hash` holds an HMAC-SHA256 of the purpose, the address, and the code, made with a key that is not in the database (the token signing key). A plain hash of 6 digits could be reversed by trying the million codes, so reading the table must not be enough. The purpose and the address are part of the hashed text, so a code works for them only.
- **Sign-up says when the address already has an account.** A request for a `REGISTER` code for an address that is in `users` is refused with `EMAIL_TAKEN` (409), and the page says that the address is registered. A soft-deleted account and the SYSTEM identity count, because their addresses stay reserved. Nothing is mailed, stored, or counted. The owner chose the clear message: a person who forgot an old account learns it at once. The price is that anyone can test whether an address has an account, and such a test is not counted against the limits below, because a refused request is not counted.
- **A request for a recovery code is answered the same way whether or not a mail is sent**, with `204` and no body, so that Forgot password does not show which addresses are registered. A `RESET_PASSWORD` code is mailed when the address belongs to an account that is not soft-deleted and is not the SYSTEM identity; a disabled account may set a new password, and stays disabled. When the address belongs to no account, to a soft-deleted account, or to the SYSTEM identity, no mail is sent, and a row is stored all the same, whose `code_hash` holds the marker `!NOT-SENT`, which is not the hash of any code. The 60-second rule, the limits below, and a later wrong entry therefore behave exactly as for a mailed code.
- **Requests are limited**, because each accepted request can send a mail and the 60-second rule limits one address only. Without more, one client could request codes for many addresses and use the mail service for spam (the code-request part of Q-07):

| Limit | Value | Counted in `email_code_request_counts` |
| --- | --- | --- |
| Per client address | 10 accepted requests in an hour | `scope` is `IP:` followed by the address, `window_start` is the start of the hour |
| The whole system | 200 accepted requests in a day. The day starts at midnight in Sydney time, like the reset of the login failures | `scope` is `ALL`, `window_start` is that midnight, stored in UTC |

  A request adds 1 to both counts in the transaction that stores the code, and is refused with `TOO_MANY_REQUESTS` (429) when a count passes its limit or the 60-second rule applies. The three refusals have the same answer. A refused request rolls back, so it is not counted. Every accepted request counts, also a recovery request that sends no mail, so the answer stays the same for a registered and an unregistered address. The limit of the whole system is the last guard: even from many client addresses, at most 200 mails leave in a day. When it is reached, nobody gets a code until the next day. The client address is the address of the connection; behind a reverse proxy it would be the address of the proxy, which must be solved when the application is deployed behind one.
- **The mail is sent after the transaction has committed**, in the background, so a code is never sent for a row that was rolled back, and the time of the answer does not show whether a mail was sent. A mail that fails is logged without the address and the code, and the person requests a new one. The mail goes out by SMTP, so the provider is configuration only (see the backend design).
- **Sign-up**: the user requests a code for an email address, and then sends the username, the email, the password, and the code. The steps follow the login steps:
  1. With no transaction, check the username rule and the password rule, and then the code against the latest unused code of the address. A wrong entry on a valid code adds 1 to `attempts` in a short transaction of its own and is answered with `CODE_INVALID` (400). The same answer is given when there is no code, when it has expired, and when it has had 5 wrong entries, so the answer does not show whether a code exists.
  2. Still with no transaction, hash the password. This happens only after a right code, so a request with a wrong code costs no hashing.
  3. In one transaction: refuse a username (`USERNAME_TAKEN`, 409) or an address (`EMAIL_TAKEN`, 409) that is taken; use the code up with the conditional update of Concurrency by Module, and answer `CODE_INVALID` when it can no longer be used; insert the user with `status` `ACTIVE`, no role, `last_login_at` set to this moment, and a new `session_id`; and write the `USER_REGISTERED` row. A refusal rolls the transaction back, so the code is still unused: a person whose username is taken picks another one and sends the same code again.
  4. After the commit, issue a login token whose issue time is the moment taken in step 3 and which carries the `session_id` of step 3, and return it with the new account. The new user is logged in without a second request and goes straight to the pending page, because the account has no role yet (see Users without roles). The token is an ordinary one: every request checks the account, and a later password reset ends it, also in the same millisecond, because the reset is never earlier than `last_login_at`.
- **Recovery**: the user requests a code and then sends the email, the code, and the new password.
  1. With no transaction, check the password rule and then the code, as in sign-up. An address with no account has a row that no code matches, so it gets the same `CODE_INVALID` and its wrong entry is counted the same way.
  2. Still with no transaction, read the account, compare the new password with the current hash (a new password may not equal the current one; the refusal is `VALIDATION_FAILED` on the field, and the code is not used up), and hash the new password.
  3. In one transaction that locks the user row with `FOR UPDATE`: refuse with `CODE_INVALID` when the account is now soft-deleted or its `password_hash` is no longer the one that was compared in step 2; use the code up; set `password_hash` and `password_changed_at`, and clear `failed_login_count`, `last_failed_login_at`, and `locked_until`; and write the `PASSWORD_RESET` row. A login in progress sees either the old row or the new one (see Login steps).
  4. `password_changed_at` is the time of the reset, and never earlier than the issue time of a token of the old password: it is at least `last_login_at`, and later than the previous `password_changed_at`. So a login and a reset in the same millisecond still end the old token (see Token checks).
- **Guessing a code** is limited by the 5 wrong entries of a code and by the limits on requests: a code is one of a million, and at most 200 codes exist in a day.
- **Cleanup.** A code is useless once it has expired, so a job of the application deletes every row whose `expires_at` has passed, once a minute (see Operating Parameters). A code is therefore kept for 10 minutes, plus at most one minute until the next run, and is not kept for a later support question. The job deletes by `expires_at` only, and never touches a code that is still valid. The same job deletes the rows of `email_code_request_counts` whose window started more than two days ago, long after any window has ended. These are the only two tables whose rows the application deletes in its normal work, so the application account has `DELETE` on them, and on no other table until a feature needs it (see the role tables in the privilege script). Every other record in the database is kept for ever.

**Token checks.** A login token carries only the user id, its issue time, and the id of its login (`session_id`). On every request the service layer loads the user and the user's roles from the database. It refuses the request with `UNAUTHENTICATED` (401) when the user does not exist or is soft-deleted, when the token was not issued after `users.password_changed_at`, or when it carries no login id (a token issued before logins were kept); with `ACCOUNT_DISABLED` (403) when `status` is `DISABLED`; and with `SESSION_REPLACED` (401) when its login id is not `users.session_id`, because the account has logged in again since. Disabled is checked first, so a disabled account is told so. The token carries its issue time to the millisecond, as `password_changed_at` is stored, so a reset and a login in the same second are still told apart. So disabling an account, changing its roles, and resetting its password all take effect on the next request, and a stolen token stops working after a password reset. The token expires 12 hours after it was issued, and there is no refresh token: the user logs in again. A long expiry is acceptable because every request checks the account.

**One signed-in device.** An account is signed in on one device at a time, and the newest login wins (the owner's decision, 2026-11-15). A person who forgot to sign out at work signs in at home, and the work session ends; a person whose password was stolen signs in again to end the stranger's session, and then resets the password.

- Each successful login, and the sign-up that logs the new user in, writes a new random `users.session_id` in the transaction that holds the user row, and the token carries the same id. The token check accepts only the token whose id is the current one, so every earlier login is refused on its next request with `SESSION_REPLACED`, and the page says that the account signed in on another device. A password reset and signing out do not change `session_id`.
- **The earlier device is told at once.** Every signed-in page keeps an event stream open (`GET /auth/session-events`). After a login commits, the server sends `session-replaced` on the streams of the same account that belong to another login, and closes them. The token check above is what enforces the rule; the stream only tells the page sooner. A page that missed the event, because its stream was down, is refused on its next request, and it also asks the server when the person comes back to the tab.
- The open streams are held in the memory of the server. With one server that is enough. With several, a login on one server must reach the streams on the others, for example through Redis publish and subscribe; that is to be built when a second server is added.
- A stream lasts at most 15 minutes and is then opened again, which checks the token again, so a stream does not outlive a disabled account or an expired token by more than that.

#### Administrator protection

The system must always have at least one usable account that holds the `ADMIN` role. This rule is fixed and has no override: no operation, by anyone including an administrator, may leave the system without one. A new administrator must be made before the only existing one can be changed.

- **Covered operations**: disabling a user, soft-deleting a user, revoking `ADMIN` from a user, and replacing a user's roles so that `ADMIN` is removed. Because nobody changes their own account, each of these is done by one administrator to another.
- **How it is checked**: in the same transaction as the change, the service layer applies the change, counts the usable accounts that hold `ADMIN`, and rolls back with a clear message if the count is zero.
- **Concurrency**: two things protect the rule. First, the steps of a change to a user (below) lock the user rows of the actor and of the target, and only an administrator who is not the target can take `ADMIN` away, so the actor is still a usable administrator when the change commits. Second, as a line of defence that does not depend on the first, a change to a user who holds `ADMIN` locks the `ADMIN` row in `roles` with `SELECT ... FOR UPDATE` before it changes anything and counts afterwards. Without any lock, two administrators could remove each other at the same moment, each seeing the other still there, and both would pass.
- **Limit**: the rule lives in the service layer. Direct SQL run as `root` or the migration account does not pass through it.

#### Steps of a change to a user

One user changes another in four ways: replacing the roles, disabling, enabling, and soft-deleting. All four follow the same steps, in one transaction, under the deadlock and lock-wait retry of every module.

1. **Refuse at once** when the target is the actor (see Authority over roles and accounts). This compares two ids and needs no lock.
2. **Lock the user rows of the actor and of the target** with `SELECT ... FOR UPDATE`, in ascending id.
3. **Read the status and the roles of both under those locks.** The roles and the status of a user change only in a transaction that holds that user's row, so what is read here cannot change before the commit. Check that the actor is a usable account, that the target exists, is not soft-deleted, and is not the SYSTEM identity, and that the actor has authority over the target as it is now and over the change that is asked for.
4. **Compare with what the actor saw.** A replacement of roles carries the list of roles the actor saw on the screen. If the roles under the lock differ from it, someone else changed them in the meantime, and the request is refused with "user changed, please reload", like a stock correction or a reassignment. A status change needs no such list: disabling expects `ACTIVE`, enabling expects `DISABLED`, and a request that finds another status reports the current one and changes nothing.
5. **If the target holds `ADMIN`**, lock the `ADMIN` row in `roles` with `FOR UPDATE`.
6. **Apply the change.** A replacement deletes the `user_roles` rows of the roles removed and inserts the rows of the roles added. Adding a role needs a usable target; a replacement that only removes roles is also allowed on a disabled user. Disabling sets `status`, and soft-deleting sets `deleted` and `status`; both then return the `IN_PROGRESS` tickets of the user to the pool (see Concurrency by Module, Tickets). Enabling sets `status` back to `ACTIVE`, and the roles the user still holds take effect again.
7. **If step 5 applied**, count the usable accounts that hold `ADMIN`, and roll back if there is none.
8. **Write the log rows** (see Role grant and revocation logs, and User and account logs), and commit.

Lock order: `users` (ascending id), then `roles`, then `tickets`, as everywhere.

The four changes are built except for one part of step 6: returning the `IN_PROGRESS` tickets of a disabled or soft-deleted user to the pool. It follows the ticket rules (the assignment history, the ticket logs, and the lock on the ticket rows), so it is added when the ticket module is built (T-21). Until then no ticket can be in progress, because nothing creates one.

The result of each refusal, in the order of the steps (the codes are in [api-design.md](../api/api-design.md#result-codes-and-status-codes)):

| Step | Refusal | Result |
| --- | --- | --- |
| 1 | The target is the actor | `FORBIDDEN` |
| 3 | The actor was soft-deleted since the request arrived | `UNAUTHENTICATED` |
| 3 | The actor was disabled since the request arrived | `ACCOUNT_DISABLED` |
| 3 | The target does not exist, is soft-deleted, or is the SYSTEM identity | `NOT_FOUND` |
| 3 | The actor holds no role any more | `ACCOUNT_NO_ROLE` |
| 3 | The roles the actor holds now do not allow this change to this user | `FORBIDDEN` |
| 4 | The roles of the target are not the ones the actor saw | `USER_CHANGED` |
| 4 | A disabling finds the user disabled, or an enabling finds the user active | `USER_CHANGED`, with the current status in `userStatus` |
| 6 | A role is to be added to a disabled user | `USER_NOT_ACTIVE` |
| 7 | No usable administrator would be left | `LAST_ADMIN` |

What the steps give:

- Two changes to the same user run one after the other, and the second sees the result of the first.
- An actor who is demoted or disabled while acting waits for that change, or it waits for the actor. Whichever commits second sees the first: a manager whose role was just taken away is refused, and does not finish a change with an authority they no longer have.
- Two administrators who remove each other at the same moment lock the same two rows in the same order. The second finds that it is no longer an administrator and is refused.
- A take, an assignment, or a login for the target needs the same user row, so it waits and then sees the new state.

**Requests already on their way.** Every request reads the roles of its user once, when it arrives. Changing a user locks the actor's row and reads the roles again, as the steps above say, and so do the ticket paths that lock a user row. Any other request that has passed its permission check when a revocation commits is allowed to finish: an operator whose role is taken away while a stock change of theirs is running completes that one change, and the next request is refused. Locking the user row in every write would make every order wait for every other action of the same person, for a window of one request.

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

The log stores `roleCode` as well as `roleId`, so a row reads without a join. Replacing a user's roles writes these rows, one for each role added or removed. The roles themselves are fixed (see Role management), so there is no log of a role being created, updated, or deleted.

#### Ticket logs

Every ticket action writes one `operation_logs` row in the same transaction as the change. `target_type` is `TICKET`, `target_id` is `tickets.id`, and `operator_id` is the acting user, or empty when the system acts, as for every system action. The reason goes in `remark`. `before_data` and `after_data` hold the fields that changed and the labels of Labels in audit rows (`ticketNo`, and the `username` of any user the row names). Every change of assignee also writes a `ticket_assignment_history` row.
| Action | `action` | `before_data` | `after_data` |
| --- | --- | --- | --- |
| Open a ticket | `TICKET_CREATED` | Empty | `{"type": "INVENTORY_EXCEPTION", "source": "MANUAL", "createdBy": 7, "followsTicketId": null, "orderNo": null, "inventoryIds": [3, 5]}`. The links are part of this row, because they are written with the ticket. |
| Take, or assign from the pool | `TICKET_ASSIGNED` | `{"status": "OPEN"}` | `{"status": "IN_PROGRESS", "assigneeId": 9}`. `remark` says `taken` or `assigned`. |
| Reassign | `TICKET_REASSIGNED` | `{"assigneeId": 9}` | `{"assigneeId": 12}`. `remark` is the reason. |
| Release, remove the assignee, or the system returns the ticket | `TICKET_RELEASED` | `{"status": "IN_PROGRESS", "assigneeId": 9}` | `{"status": "OPEN"}`. `remark` is the reason, or the fixed reason of the system. |
| Resolve | `TICKET_RESOLVED` | `{"status": "IN_PROGRESS"}` | `{"status": "RESOLVED", "resolvedBy": 9}`. The resolution text stays in `tickets.resolution`. |
| Archive | `TICKET_CLOSED` | `{"status": "RESOLVED"}` | `{"status": "CLOSED", "ticketNo": "K7Q2M9XA", "closedBy": 4, "batchId": "…"}`. Each archived ticket has its own row with its ticket number, so the logs name every ticket archived and who archived it (`operator_id` is empty for the archive job). `batchId` is one random value shared by the rows of a batch archive or of one run of the job, and is absent for a single archive. `remark` is `manual`, `batch`, `auto archive after 7 days`, or `auto archive at 99 resolved`. |
| Cancel | `TICKET_CANCELLED` | `{"status": "IN_PROGRESS"}` | `{"status": "CANCELLED", "cancelledBy": 9}`. `remark` is the reason. When the system cancels the tickets of a delisted product, `operator_id` is empty and `remark` is `PRODUCT_DELISTED:` and the product id. |

These formats can change in detail when the audit screen is designed (Q-34). Opening a ticket on an order attempt is part of `TICKET_CREATED` (`orderNo`); a ticket that fails to open writes nothing, so a refused link leaves no log.

#### Product logs

Every action on a warehouse item or a product writes one `operation_logs` row in the same transaction as the change. Listing and delisting also write a `product_listing_events` row, and adding or deleting an item also writes an `inventory_item_events` row. `operator_id` is the acting user.

| Action | `action` | `target_type` | `before_data` | `after_data` |
| --- | --- | --- | --- | --- |
| Add a warehouse item | `ITEM_CREATED` | `ITEM`, the `inventory` id | Empty | `{"sku": "K7Q2M9XA", "name": "A4 paper", "imageUrl": null, "quantity": 25}` |
| Edit the name or image of an item | `ITEM_UPDATED` | `ITEM` | The changed fields before | The changed fields after |
| Delete a warehouse item | `ITEM_DELETED` | `ITEM` | `{"name": "A4 paper", "availableQuantity": 10}` | `{"deleted": true, "cancelledTicketIds": [12, 15]}`. The stock is not touched, so it is not repeated here. |
| List a product | `PRODUCT_LISTED` | `PRODUCT`, the `products` id | Empty on the first listing, `{"status": "UNLISTED"}` afterwards | `{"status": "LISTED", "inventoryId": 3, "name": "A4 paper", "price": "4.50"}` |
| Delist a product | `PRODUCT_DELISTED` | `PRODUCT` | `{"status": "LISTED"}` | `{"status": "UNLISTED", "cancelledTicketIds": [12, 15]}` |

Each ticket cancelled by a delisting also writes its own `TICKET_CANCELLED` row, with an empty `operator_id` because the system acts, and `PRODUCT_DELISTED:` and the product id in `remark`. A deletion of an item does the same with `ITEM_DELETED:` and the `inventory` id, and when the item had a listed product it also writes a `PRODUCT_DELISTED` row, because the product is delisted in the same transaction.

#### Stock logs

Every manual stock change writes one `operation_logs` row in the same transaction as the change, besides its `inventory_transactions` row. `target_type` is `ITEM`, `target_id` is the `inventory` id, and `operator_id` is the acting user. The log answers who changed the stock, when, by how much, and why; `inventory_transactions` keeps the exact snapshots.

| Operation | `action` | `before_data` | `after_data` |
| --- | --- | --- | --- |
| Stock in | `STOCK_RESTOCK` | `{"availableQuantity": 10}` | `{"availableQuantity": 15, "quantity": 5, "sku": "K7Q2M9XA", "name": "A4 paper", "transactionId": 88}` |
| Write-off | `STOCK_WRITE_OFF` | `{"availableQuantity": 15}` | `{"availableQuantity": 12, "quantity": 3, "sku": "K7Q2M9XA", "name": "A4 paper", "transactionId": 89}` |
| Correction | `STOCK_ADJUSTED` | `{"availableQuantity": 12}` | `{"availableQuantity": 20, "quantity": 8, "direction": "IN", "sku": "K7Q2M9XA", "name": "A4 paper", "transactionId": 90}` |

`remark` holds the required reason. The SKU and the name are repeated so the row stays readable on its own. The starting quantity of a new item is part of `ITEM_CREATED` and also has its `RESTOCK` transaction, as before. A sale writes no stock log row: it is an order, and the order record is `order_attempts`.

#### Order records

An order needs no row in `operation_logs`. `order_attempts` is its record, with one row for each product line of every attempt, a refused attempt included: the ordering user (`user_id`), the time (`created_at`), the product (`product_id`), the quantity, the result, the stock deducted, and the order number, which is generated before the attempt starts so that a refused attempt can be reported in a ticket. A completed attempt also points to its order, whose `order_items` freeze the name and the price. Writing the same facts in `operation_logs` would only create two records that can disagree. They are read through the product log (see Logs).

#### User and account logs

Only an action that was carried out is logged. An action that is refused (not enough authority, an invalid value, a locked account) writes nothing. `target_type` is `USER` and `target_id` is the `users.id` the action is about, and `operator_id` is the user who did it, which is the user themself for sign-up and the password reset. There is no password change for a logged-in user, because there is no user page: the only way to change a password is Forgot password on the login page, which is the reset below. The `ip_address` of the request is stored when the web layer knows it. A password, a code, or a hash is never written to a log.

| Operation | `action` | `before_data` | `after_data` |
| --- | --- | --- | --- |
| Sign-up completed | `USER_REGISTERED` | Empty | `{"username": "alice", "email": "a@example.com"}`; the new user is the operator |
| Administrator created at the console | `USER_REGISTERED` | Empty | The same, with `operator_id` empty and `remark` `console` (the `ROLE_GRANTED` row follows, see First administrator) |
| Disable or enable an account | `USER_STATUS_CHANGED` | `{"status": "ACTIVE"}` | `{"status": "DISABLED"}`. A disabling that returns the tickets of the user also writes the ticket rows. |
| Soft-delete an account | `USER_DELETED` | `{"status": "ACTIVE", "deleted": false}` | `{"status": "DISABLED", "deleted": true}` |
| Reset a password with an email code | `PASSWORD_RESET` | Empty | `{"passwordChangedAt": "…", "lockCleared": true}`; the operator is the user |
| An account gets locked by failed logins | `LOGIN_LOCKED` | Empty | `{"failedAttempts": 10, "lockedUntil": "…"}`; `operator_id` is empty because nobody is logged in |

A successful login and a single wrong password write no log row: they are frequent, and the failure count and the lock time are already kept on the user row. Role changes are logged under Role grant and revocation logs, and a changed username or email cannot happen. A user created by sign-up or at the console is the only way a `users` row appears, so these rows complete the history of the account.

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
| `users` | 1:N | `order_attempts` | The user who tried to place an order |
| `products` | 1:N | `order_attempts` | The product an attempt was about, one row for each product line |
| `orders` | 0..1:N | `order_attempts` | A completed attempt names its order. A refused attempt has none |
| `users` | 1:N | `inventory_transactions` | The user who caused the movement: a manual change or a sale |
| `order_attempts` | 0..N:N | `tickets` | A ticket names the number of an order attempt in `tickets.order_no`, a plain column and not a foreign key, because the number is shared by the product lines of an attempt. The service layer checks it when the ticket is created |
| `users` | 1:N | `tickets` | A user creates tickets |
| `users` | 0..1:N | `tickets` | A user is the current assignee of, or resolves, multiple tickets |
| `tickets` | 0..1:N | `tickets` | A ticket may follow one earlier ticket (`follows_ticket_id`); an earlier ticket can have many follow-ups |
| `users` | 0..1:N | `tickets` | A user may be recorded as the one who closed (`closed_by`) or cancelled (`cancelled_by`) a ticket |
| `tickets` | 1:N | `ticket_assignment_history` | A ticket retains its complete assignment history |
| `users` | 1:N | `ticket_assignment_history` | A user appears as the previous assignee, new assignee, or acting user |
| `tickets` | M:N | `inventory` | Implemented through `ticket_inventory`; a ticket can cover a batch of stock records |
| `users` | 0..1:N | `operation_logs` | A user performs multiple audited operations; system actions have no user |

## Order and Inventory Flow

### Create order

1. An authenticated user submits an order containing one or more products (at most the lines in Operating Parameters) and optionally an idempotency key. For each product the request carries the quantity, and the name and price the user saw on the screen. The product page sends one product at a time, and the flow itself accepts several. The quantity of each line is checked before the transaction, because it does not depend on the data: a bad quantity fails the order with `INVALID_QUANTITY` without a lock.
2. If the key already exists for this user, the system returns the original order without changing anything. If two identical submissions race, the second insert violates `UK(user_id, idempotency_key)`; it rolls back and returns the existing order.
3. Within one database transaction, the system:
   1. reads the row of every product with `SELECT ... FOR SHARE`, in ascending product id, before it writes anything. It checks that each product exists and is `LISTED`, and that its name and price are the ones the user saw. A product that is not listed fails the order with `PRODUCT_DELISTED`, and a product whose name or price changed fails it with `PRODUCT_CHANGED`. The shared lock lets orders run side by side, while a delisting waits for them or comes first;
   2. creates `orders`, with the time taken now, after the product locks are held;
   3. creates the related `order_items`, copying the SKU from the warehouse item, and the name and price from the product. The SKU is read with a plain read, never with a locking read and never with `INSERT ... SELECT` from `inventory`. A shared lock on an `inventory` row taken here would later have to be upgraded to the exclusive lock of step 4, and two orders that list the same items in opposite order would each hold shared locks the other needs, which is a deadlock (the verification found this);
   4. locks the `inventory` row of every item, reached through `products.inventory_id`, with `SELECT ... FOR UPDATE`, in ascending inventory id, and reads the current quantities;
   5. checks that `available_quantity >= q` for every item. If any item is short, the whole transaction rolls back and the caller receives an out-of-stock result. No order is created and no stock changes, even when other items in the same order had enough stock;
   6. for each item, decreases `available_quantity` and increases `sold_quantity` by `q`, and inserts a `SALE` row into `inventory_transactions` whose snapshots come from the values read in step 4 and whose operator is the ordering user. The time of the movements and of the attempt is taken now, after the stock locks are held, so a stock change that committed first is never dated later;
   7. inserts one `order_attempts` row for each product line, with the product name from the order item, the result `COMPLETED`, the order, and the stock deducted;
   8. writes the required `operation_logs`.
4. The transaction commits only when every step succeeds. The order is complete the moment it commits: nobody confirms it, and no timer runs over it.
5. When the attempt is refused (step 1, 3.1, or 3.5), the transaction rolls back and, after the rollback, a short transaction of its own inserts one `order_attempts` row for each product line with the current name of the product, the reason, and nothing deducted. After a deadlock or a lock wait the whole attempt is retried, and the row is written once, with `BUSY`, only if the last retry fails too. See Order attempts.

#### The order dialog

On the product page an `OPERATOR`, `MANAGER`, or `ADMIN` sees an Order button on each product, and nothing else to do with stock or prices. The button opens a dialog for the quantity, which starts at 1 and is changed by typing a number or with the plus and minus buttons. Confirming sends the order, and the screen shows the result. Every result carries the order number of the attempt, a placed order or a refused one, and the person can find it again in the order records of the product log (see Logs):

| Result | Shown to the user | When |
| --- | --- | --- |
| `COMPLETED` | The order was placed, with its order number | The order was created and its stock deducted. A repeated submission with the same key shows the original order in the same way. |
| `PRODUCT_DELISTED` | This product has been delisted, and the page reloads | The product is no longer `LISTED` when the order is checked |
| `PRODUCT_CHANGED` | This product has changed, please reload | The name or price differs from what the user saw, for example after a delisting and a new listing |
| `OUT_OF_STOCK` | Not enough stock | Some item is short |
| `INVALID_QUANTITY` | The quantity must be a whole number from 1 to 9999 | The quantity is not valid |
| `FORBIDDEN` | You may not place orders | The roles of the account do not allow it. The permission check refuses it before the order is looked at, as on every endpoint; a disabled account gets `ACCOUNT_DISABLED` |
| `BUSY` | The system is busy, please try again | The retries on a deadlock or a lock wait were used up. Nothing was created. |

The screen does not show the stock. A user learns that stock is short only when the order fails. When the page was already open while the product was delisted, the user still sees the product until the order is sent, and then gets `PRODUCT_DELISTED`.

### Correcting a wrong order

An order cannot be cancelled. When an operator placed the wrong quantity or the wrong product, the correction goes through a ticket, so that it is explained, recorded, and done by a person who is allowed to change stock:

1. The operator opens a ticket (see Creating a ticket on items). The `ORDER_EXCEPTION` type names the number of the order, which the operator reads in the result of the order or in the log of the product, and the items that were wrong can be linked.
2. The person who handles the ticket, the operator or someone else, puts the stock back to what it was before the order with a manual stock change: a stock-in (`RESTOCK`) or a correction. The reason names the ticket, as in the stock-change rules.
3. The handler marks the ticket resolved.
4. The operator places the order again, with a new idempotency key.

The wrong order stays in the history unchanged, and so does its `SALE`. `sold_quantity` keeps counting what orders took, and the manual change restores `available_quantity` only.

If the item of the wrong order has been deleted, step 2 does not apply: the stock left on a deleted item no longer matters, and every manual change of it is refused. The ticket cannot be linked to the deleted item either, so it is opened without a link and names the order number. When the item is added again, a person counts the stock and enters it as the starting quantity.

### Concurrency and duplicate protection

- **Strategy: pessimistic row locking.** Every stock change locks the affected `inventory` rows with `SELECT ... FOR UPDATE`, reads the quantities, validates, updates, and writes the `inventory_transactions` rows, all in one transaction. A plain `SELECT` must not be used to read quantities for a later update, because another transaction can change them in between and the snapshots would be wrong. There is no version counter on `inventory`, and optimistic locking is not used, so the two strategies are never mixed.
- **Lock order.** Order creation reads the `products` rows first (shared), then inserts the `orders` row, then locks the `inventory` rows in ascending inventory id. A fixed order lowers the chance of deadlock but does not remove it.
- **Deadlock and lock-wait retry.** If the database reports a deadlock or a lock wait timeout, the transaction is rolled back completely and retried in a new transaction, up to the attempt limit in Operating Parameters. MySQL rolls back the whole transaction on a deadlock, but a lock wait timeout may roll back only the failed statement, so the application must explicitly roll back before it retries. Retries are safe because the unique constraints below prevent a repeated operation from taking effect twice, and because a placement that failed left nothing behind.
- **Constraint backstop.** `CHECK` constraints keep stock quantities non-negative, so a logic error fails instead of corrupting stock.
- **Manual stock changes** use the same row lock and transaction pattern as orders (see Manual stock changes). A manual change and an order on the same product wait for each other on the `inventory` row, so neither can overwrite the other's quantity. A correction also checks the value the user saw, so a stale screen cannot silently undo a sale. This is a check made while the row is locked, not an optimistic version counter.
- **Duplicate inventory transactions.** `UK(order_id, inventory_id, type)` on `inventory_transactions` is a second line of defense. A duplicate `SALE` for the same order and inventory record violates the constraint and rolls back. Stock-level types have no order, and the constraint does not apply to them, so repeated restocks are allowed.
- **Duplicate orders.** `UK(user_id, idempotency_key)` prevents duplicate order creation from repeated submissions. It is a composite key, not a unique key on `idempotency_key` alone.

### Exceptions

The program opens no ticket by itself today. An order is placed in one transaction, so every failure (not enough stock, the retries used up, a database error) rolls everything back and leaves no order, no stock change, and nothing to put back. The user sees the result (`OUT_OF_STOCK`, `BUSY`) and can try again. The failed attempt is not lost: it is recorded in `order_attempts` and appears in the log of the product (see Order attempts).

A person who finds a problem with an order or with stock opens a ticket (see When to open a ticket). Tickets are always opened by a person. An `ORDER_EXCEPTION` ticket names the number of an order attempt, placed or refused. The ticket fields for tickets the program opens (`tickets.source` `SYSTEM`, the SYSTEM identity as creator) stay in the design for the day an automatic case is added. It would be added with its own rule here, and it must stop the thing it reports in the same transaction that opens the ticket.

## Screens and Lists

This section records the rules of the screens that reach into the database or the service layer: what a list reads, how it is filtered, and who may see which rows. How the screens look is in [page-design.md](../frontend/page-design.md), which depends on this section and not the other way round. The ticket screens are specified under Ticket screens and who sees what.

### Home page and paging

- **The first page** after login is the product page, for every role that has one, even when no product is listed (the page then says so). A user with no role sees only the pending page (see Users without roles), and a disabled account gets no further than the login page (see Login security and account recovery).
- **Lists are paged with two buttons**, previous and next, with the number of the page shown between them ("Page 3"), centred at the foot of the list. The page counts the number itself as the person goes forward and back, and a new search or filter starts at page 1. There is no total count and no jump to a page, so the program never counts the rows of a list. The page size is 10, and it is also the most a page can hold. On the product page the person chooses 8, 16, or 24 cards a page, 16 by default, and a new choice starts at page 1; the cards are laid out in a number of columns that divides the page, so a full page fills its rows (the owner's request, 2026-11-16). The sizes are operating parameters.
- **A page is read by key, not by offset.** The next page asks for the rows after the last row of the page shown, in the sort key of the list and then the `id`, and the previous page asks for the rows before the first one. The sort keys are the ones the indexes already have: the creation, archive, or cancellation time with `source` first for tickets (see Ticket list order), the creation time for the logs and the audit log, and the name for the product page. A fuller paging system is an idea for later (see [future-ideas.md](../future-ideas.md)).

### The time filter

Every list that is filtered by time uses one template, so that a person learns it once. It applies to the product log and the warehouse log in all three ways of reading them (the own log of one product or item included), to the creation days of the ticket search, and to the audit log page. A list that gets a time filter later uses it too.

- **Two forms.** A range from a start day to an end day, both included, where the end day is today at the latest. Or from a start day until now, with no end day. With no filter set, the list is not narrowed by time.
- **Days, not times.** The person picks days. A day is a day in the display time zone (`Australia/Sydney`). The service layer turns the choice into a UTC range: from the start of the first day to the start of the day after the last, or with no upper end for "until now". A start day after the end day, or an end day after today, is refused.
- **The direction.** The person chooses newest first or oldest first. Newest first is the default. Both directions read the same index, and the paging by key works in either. On the ticket screens the tickets opened by the system still come first in either direction (see Ticket list order).
- The choice is a setting of the screen and is not stored.

### Stock page

The stock page shows one row for each warehouse item that is not deleted: the image, the name, the stock quantity (`available_quantity`), the button for changing the quantity, and the View button for the records of that item. A **View records** button at the top opens the records of all items. A `MANAGER` or an `ADMIN` also adds an item and deletes an item from this page. Every role reads it, and the change controls follow Manual stock changes. The page has a search box for the name, like the product page: it narrows the rows to the items whose name matches the text typed, and it never finds a deleted item. The rows are ordered by name and then `id`. The search is a match on the text and not an index search, because the warehouse table is small.

### Logs

The program keeps two logs. The **product log** belongs to the product page and the **warehouse log** belongs to the stock page. Each has two kinds of record, and each can be read in three ways: all records of one kind, the records of one chosen product or item, and the own log of one product or item. Every role that can read the page can read its log. Nothing in a log can be edited or deleted.

| Log | Page | Kind of record | Source |
| --- | --- | --- | --- |
| Product log | Product page | Listing records: a product was listed or delisted | `product_listing_events` |
| Product log | Product page | Order records: every attempt to order, placed or refused | `order_attempts` |
| Warehouse log | Stock page | Item records: an item was added or deleted | `inventory_item_events` |
| Warehouse log | Stock page | Stock movements: orders, stock in, write-offs, and manual corrections | `inventory_transactions` |

**The three ways in.**

1. **All records.** The **View records** button of the page asks which kind of record to read (the product page: listing records or order records; the stock page: item records or stock movements) and then shows that kind for all products or all items, newest first.
2. **One chosen.** Inside that view a product (or item) picker narrows the records to one name (see The searchable picker). The time filter narrows them by time (see The time filter). Both filters can be used together.
3. **The own log of one.** The View button on a row of the product page (or the stock page) opens the log of that single product (or item), with both kinds of record in two parts, newest first by default. It has the time filter and the choice of direction, and no other filter. A product that is delisted is no longer a row on the product page, and an item that is deleted is no longer a row on the stock page, so their records are read through the first two ways, and the records of a deleted item also through the Deleted items module.

**Deleted items.** The stock page has a third choice under View records, **Deleted items**, a module made to find an item that is gone from the warehouse. It lists the deleted items, newest deletion first, with the name, the SKU, the time of the deletion, and who deleted it, 10 to a page, with one text box that narrows the list by name or SKU. A row opens the own log of that item in four parts: its item records, its stock movements, and, if it ever had a product, the listing records and the order records of that product. This is the only place where a deleted item can be searched for, and the item and product pickers still do not offer it (see The searchable picker). The list reads the `inventory` rows whose `deleted_at` is set, ordered by `deleted_at` and then `id`, paged by key; the warehouse table is small and has no index for this list, and one is added only if it grows enough to need it. The module is read-only.

**Finding by order number.** An order number is shown in the order records, in the stock movements of an order, and on a ticket, and each of these can be found by it. One text box takes one whole order number and finds the exact match, with no other filter. The number is written as the numbers are made, whatever case is typed: `ORD-` in upper case and the rest in lower case, because the column compares exactly:

- **Order records** (the product log and the Orders area of the audit page): every line of the attempt, through the unique key `(order_no, product_id)`.
- **Stock movements** (the warehouse log): the `SALE` rows of the order, through the order (`orders.order_no`) and `inventory_transactions.order_id`. A refused attempt has no order, so it finds nothing here.
- **Tickets** (the ticket search): the tickets that name the number, through `idx_tickets_order_status`.

No new table, column, or index is needed.

**What the records show.**

| Record | Columns |
| --- | --- |
| Listing record | The time, the product, listed or delisted, the name and price at that moment, and who did it |
| Order record | The time, the product, the order number, who ordered, the quantity, the result (placed, delisted, changed, out of stock, invalid quantity, or busy), and the stock deducted. It lists every attempt, whether or not it succeeded (see Order attempts), so a person finds the number of a refused order here and can put it in a ticket |
| Item record | The time, the item, added or deleted, the name and SKU at that moment, the quantity (the starting stock, or the stock shown when it was deleted), and who did it |
| Stock movement | The time, the item, the type (order, stock in, write-off, or manual correction), the order number for an order, the reason, the amount (`+` or `-`), and who did it |

For a stock movement the amount is `+quantity` for `RESTOCK` and `ADJUST_IN` and `-quantity` for `SALE`, `WRITE_OFF`, and `ADJUST_OUT`. The reason is the one typed with a manual change, and is empty for an order. Who did it is the user who placed the order or made the change. The order number of a stock movement is read through `inventory_transactions.order_id`. The own log of a product shows its listing records and its order records as two parts, and the own log of an item shows its item records and its stock movements as two parts.

**Rules of reading.**

- **Only what was written is shown.** A stock movement exists only if its transaction committed. A refused order is shown because it is recorded on purpose as an attempt. A failure of the program itself, such as a database that is down, may leave nothing to show.
- **Paging and the day range.** Every list is paged (two buttons, 10 rows). The only filters are the picker and the time filter, with its two forms and its choice of direction (see The time filter).
- **The indexes.** With a product or item chosen, the lists read `idx_product_events_product_created`, `idx_order_attempts_product_created`, `idx_item_events_inventory_created`, and `idx_inventory_tx_inventory_created`. Without one, they read the time indexes `idx_product_events_created`, `idx_order_attempts_created`, `idx_item_events_created`, and `idx_inventory_tx_created`, by time and then `id`.

### The searchable picker

Wherever a screen asks for a product or an item, it uses one shared control: a dropdown list that scrolls when it is long, sorted alphabetically by name, and a text box in it that narrows the list as the person types. It is the same for the product picker and the item picker, and it is the control that the ticket form and the filters reuse when they need to pick by name.

- **The product picker** offers every product that was ever listed, delisted ones included, by its current name, except the products of deleted items. When two products have the same name, the SKU of the item is shown beside the name.
- **The item picker** offers every warehouse item that is not deleted, by its name and SKU. It reads the same rows as the stock page. A deleted item is gone from the warehouse and is offered nowhere, so that the backend search does not return options the screen cannot show. The records of a deleted item stay in the database and in the lists of all records (the first reading of each log), and it is found by name only in the Deleted items module of the warehouse log.
- The name search is a match on the text typed and is not an index search; the lists are small enough. The picker returns the id, and the log is read by that id.

### Ticket search

The ticket screens of `ADMIN`, `MANAGER`, and `VIEWER` can be searched. An `OPERATOR` has no search, because an operator sees only their own few tickets (see Ticket screens and who sees what).

- **Each screen searches only its own status.** A search on the pool finds only `OPEN` tickets, on the archive only `CLOSED` tickets, and so on. The statuses are never searched together.
- **The filters** are the kind (`source`, `SYSTEM` or `MANUAL`), the creator, the assignee, and the creation days (see The time filter). They can be combined. The creator and the assignee are picked by username (see The user picker).
- **The other ways to search are by one ticket number, or by one order number** (the tickets that name it), and nothing else. There is no search by title or description.
- The filters use the indexes of Initial Constraints: `(status, source, created_at)` for the kind and the time, `tickets(created_by, status)` for the creator, `tickets(assignee_id, status)` for the assignee, and the unique key for the number. The sort order of Ticket list order always applies.

### The user picker

Three places need a person chosen by username: the creator and assignee filters of the ticket search, the user filter of the audit log page, and the choice of an assignee when a ticket is assigned or reassigned. None of them can use the list of the user page, which shows only the users the viewer can manage and is closed to `VIEWER`. A separate read serves them. It returns only the `id` and the `username` of each user, and never the email, the status, or the roles. It never returns the SYSTEM identity.

| Use | Who may read it | Which users it returns |
| --- | --- | --- |
| The creator and assignee filters of the ticket search | `ADMIN`, `MANAGER`, `VIEWER` | Every user, disabled and soft-deleted users included, because their tickets remain. For example, a manager finds the tickets that a person who has left once created |
| The user filter of the audit log page | `ADMIN`, `MANAGER` | The same users, because their records remain. The page adds one more choice, System, which finds the records of system actions (`operator_id` is empty). It is a choice of the page and not a user of the picker |
| The assignee of an assignment or a reassignment | `ADMIN`, `MANAGER` | The eligible assignees the reader may assign to (see Who may take a ticket): all of them for a reader who holds `ADMIN`, and those who do not hold `ADMIN` for any other reader |

An `OPERATOR` and a user with no role cannot read it. The list of eligible assignees is a help for the person choosing: the check under the lock on the assignee's user row still decides (see Concurrency by Module).

### User page

Only `ADMIN` and `MANAGER` have a user page. It lists only the users the viewer is allowed to manage, so the list is a view of Authority over roles and accounts:

- an `ADMIN` sees every user except the SYSTEM identity, managers and administrators included. Their own account is in the list, with no control to disable it, delete it, or change its roles;
- a `MANAGER` sees only the users who hold neither `ADMIN` nor `MANAGER`, and never their own account;
- both see users with no role (they are waiting for one) and disabled users, and neither sees soft-deleted users.

The list can be narrowed in two ways, which can be combined:

- **A search box for the username.** It narrows the list to the users whose username contains the text typed, without regard to case: `ali` finds `alice` and `Natalie`. Spaces around the text are ignored, and `%` and `_` are plain characters.
- **Role buttons**, of which several can be chosen at once. There is one button for each role the viewer may manage, and one more, **No role**, for the users who are waiting for a role: `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER`, and No role for an `ADMIN`; `OPERATOR`, `VIEWER`, and No role for a `MANAGER`. With several buttons chosen, the list shows the users who hold any one of the chosen roles, and with No role also the users who hold none. With no button chosen, the list is not narrowed by role.

Neither filter widens the list: a `MANAGER` who searches for the username of an administrator finds nothing.

In the code, who sees whom follows the permissions and not the role names (see [Permissions](../backend/backend-design.md#permissions)): a viewer who may change users who hold a protected role (`USER_MANAGE_PROTECTED` or `ROLE_ASSIGN_PROTECTED`) sees every user, their own account included; any other viewer with `USER_VIEW` sees only the users who hold no protected role, and never their own account. Today these are the administrator and the manager. The rows are ordered by username and then `id`, and paged by key. The search is a match on the text and the role filter a join on `user_roles`, with no new index, because the table of users is small.

The service layer filters the rows. The page lets the viewer grant and revoke roles (the roles offered are those the viewer may grant), disable and enable a user, and soft-delete a user. The roles themselves are fixed and have no page (see Role management).

### Audit log page

`ADMIN` and `MANAGER` open the audit page to read the records of the background work: the work that is not production and the production work as well. The page is read-only and nobody can edit or delete a row. A role that cannot open it, such as `VIEWER`, still reads the order records on the product page and the stock records on the stock page, so an accountant with the `VIEWER` role sees the production records through those pages.

- **A menu of seven areas.** The page opens on a menu with one entry for each area: Accounts, Roles and permissions, Tickets, Stock, Warehouse items, Products, and Orders. The first six read `operation_logs` filtered by `category` (see Categories). **Orders** reads `order_attempts`, the same records as the order records of the product log, so the order records are in two places and written once.
- **A list for one area.** Choosing an area shows its records, newest first, 10 to a page with previous and next, paged by key. Three independent filters can be combined: the action (only the actions of that area), the time (see The time filter, with its choice of direction), and the user (`operator_id`, or `user_id` for orders), chosen from the user picker or as System (see The user picker). For Orders the action filter is the result of the attempt (placed, delisted, changed, out of stock, invalid quantity, or busy). A system action shows the user as System.
- **The indexes.** `operation_logs` is read through `(category, created_at)`, which also orders by `id`, and the user filter through `(operator_id, created_at)`. Orders are read through `idx_order_attempts_created` and `idx_order_attempts_user_created`. The index `(target_type, target_id)` finds the rows of one object.
- **Each row shows itself.** Every row carries its labels (see Labels in audit rows), so it reads without a join. How a row is laid out is decided with the page design (Q-34).

### Account pages

The program has three account functions and nothing more: sign-up, login, and Forgot password, which are ordinary pages. A user cannot change the email or the username, and there is no account or profile page, so there is no way to change the password while logged in. The only way to a new password is Forgot password on the login page. The first administrator is created on the server with a console command, so no page exists for it (see First administrator).

## Permission Matrix

This section says what each seeded role may do in each module. The owner reviewed it on 2026-11-14, before the permission module is built ([database-v1.12.md](../iterations/database-v1.12.md)). It sits on top of Authority over roles and accounts, which says who may change roles and accounts. A role that is not allowed an action is refused with 403, and the frontend hides the control.

Principles:

- **Deny by default.** An action that is not listed is refused. A user with no role can do nothing (see Users without roles).
- **Several roles add up.** A user may hold more than one role, and may do whatever any one of those roles allows. Where a role limits what is seen, the widest view wins: a user who holds `OPERATOR` and `VIEWER` sees everyone's tickets and can search them, as a `VIEWER` does, and can also work on tickets, as an `OPERATOR` does.
- **`VIEWER` is read-only.** It never changes data in any module.
- **`OPERATOR` runs the day-to-day flow**: orders, stock in, write-offs, corrections, and tickets. It does not change master data, or see audit logs or the user page.
- **`MANAGER` can do everything `OPERATOR` can, plus master data, user and role assignment (below manager), and audit logs.**
- **`ADMIN` can do everything `MANAGER` can, plus the actions reserved for it in Authority over roles and accounts.**
- A role counts only when the acting account is a usable account. System actions, such as the archive job and the cancellation of tickets by a delisting, have no acting user and are not covered by this matrix.

| Module | Action | ADMIN | MANAGER | OPERATOR | VIEWER |
| --- | --- | --- | --- | --- | --- |
| Products | View the product page (image, name, and price of the listed products), search it by name, and read the product log (listing records and order records, all or of one product; see Logs) | Yes | Yes | Yes | Yes |
| Products | List an item: open the catalog of warehouse items that are not listed, set the price and, if needed, the name | Yes | Yes | No | No |
| Products | Delist a product, with no reason asked | Yes | Yes | No | No |
| Inventory | Add an item to the warehouse (name, optional image, optional starting quantity; the SKU is generated), and edit the name or image of an item that is not listed | Yes | Yes | No | No |
| Inventory | Delete an item (soft), with no reason asked | Yes | Yes | No | No |
| Inventory | View the stock page, search it by name, and read the warehouse log (item records and stock movements, all or of one item; see Logs) | Yes | Yes | Yes | Yes |
| Inventory | Search the Deleted items module of the warehouse log (a read-only list of deleted items and the own log of one) | Yes | Yes | Yes | Yes |
| Inventory | Stock in (`RESTOCK`), reason required | Yes | Yes | Yes | No |
| Inventory | Write-off or stock out (`WRITE_OFF`), reason required | Yes | Yes | Yes | No |
| Inventory | Correction: type the quantity that was counted (`ADJUST_IN`, `ADJUST_OUT`), reason required | Yes | Yes | Yes | No |
| Orders | Place an order, from the product page (it completes at once). There is no order list or order page: an order is an action, and its record is kept for the history | Yes | Yes | Yes | No |
| Tickets | View the pool, the in-progress and resolved screens of everyone's tickets, the archive screen of `CLOSED` tickets, the cancelled area, the follow-up links, and the assignment history | Yes | Yes | No | Yes |
| Tickets | View the pool, and only their own tickets on the in-progress screen (assigned to them), the resolved screen (resolved by them), and the cancelled area (created or last held by them). No archive screen. See Ticket screens and who sees what | No | No | Yes | No |
| Tickets | Search and filter each ticket screen (see Ticket search), and read the usernames for the creator and assignee filters (see The user picker) | Yes | Yes | No | Yes |
| Tickets | Create manually, choosing zero or more warehouse items (links are written only at creation), or as a follow-up to a `RESOLVED` or `CLOSED` ticket | Yes | Yes | Yes | No |
| Tickets | Take a ticket from the pool (it becomes `IN_PROGRESS` at once), if an eligible assignee | Yes | Yes | Yes | No |
| Tickets | Assign a ticket from the pool, or reassign an `IN_PROGRESS` ticket, to an eligible assignee chosen from the user picker, with a reason (the only way to take a ticket from its assignee). Managers can assign tickets to each other | Yes, to an operator, a manager, or an administrator | Yes, to an operator or a manager, never to an administrator | No | No |
| Tickets | Release a ticket back to the pool, with a reason | Yes, any | Yes, any | Only their own (assigned to them) | No |
| Tickets | Move to `RESOLVED` | Yes | Yes | Only a ticket assigned to them | No |
| Tickets | Cancel an `OPEN` or `IN_PROGRESS` ticket, with a reason | Yes, any | Yes, any | Only their own (assigned to them, or created by them while `OPEN`) | No |
| Tickets | Archive a `RESOLVED` ticket (move to `CLOSED`) at any time, singly or in a batch. The system also archives after 7 days. | Yes | Yes | No | No |
| Tickets | Reopen a ticket | No one | No one | No one | No one |
| Audit logs | Open the audit page, choose one of its seven areas, and filter by action, time, and user (see Audit log page). The order records are also on the product page, which every role can read | Yes | Yes | No | No |
| Audit logs | Edit or delete | No one | No one | No one | No one |
| Users | View the user page, which lists only the users the viewer can manage, and narrow it by username and by role (see User page) | All users | Users who hold neither `ADMIN` nor `MANAGER` | No | No |
| Users | Change the status of another user, or soft-delete one | See Authority over roles and accounts | See Authority over roles and accounts | No | No |
| Users | Disable or soft-delete their own account, or change their own roles | No one | No one | No one | No one |
| Roles | Grant and revoke roles on the user page | Every role | Roles below manager | No | No |
| Roles | Create, update, or delete a role. The four roles are fixed (see Role management) | No one | No one | No one | No one |
| Account | Change the password. There is no account page; the only way is Forgot password on the login page | Yes | Yes | Yes | Yes |

Where the permissions live: they are fixed in the application code and keyed by role code. Each action of this matrix that some roles may do is a named permission, and one list per role says which permissions the role grants (see [Permissions](../backend/backend-design.md#permissions)). The roles are fixed too, so every role that exists has its permissions in the code. This is intended: the system is built for customers, and any new capability goes through the backend developers, so a new role arrives in one backend change, with its `roles` row and its list of permissions.

Confirmed in the review of 2026-11-14: only `ADMIN` and `MANAGER` may archive a ticket or give it to someone else, an `OPERATOR` may cancel only their own tickets, and an `OPERATOR` may move only their own tickets forward. Changed in the review: an `OPERATOR` may also make a correction.

## Concurrency by Module

Order and inventory concurrency is specified under Order and Inventory Flow. This section applies the same thinking to every other module, because any two people can act at the same moment. Each rule below is a working default for the service layer. It is checked against the real code when the module is built, and the order and inventory checks in `db/verification` are the model for how.

**Principles**

- A write that depends on something the program just read must be protected by one of four things: a row lock (`SELECT ... FOR UPDATE`, or `FOR SHARE` for a check that must not change), a conditional update (`WHERE id = ? AND status = ?`), an atomic update (`SET n = n + 1`), or a unique constraint. A plain read followed by a write is never enough.
- A unique constraint is the last line of defense. It does not replace the check the program makes first.
- When one transaction locks several kinds of rows, it locks them in this order: `users` (ascending id), then `roles`, then `products` (ascending id), then `tickets`, then `orders`, then `inventory` (ascending id). A fixed order lowers the chance of deadlock but does not remove it, so every module uses the deadlock and lock-wait retry in Concurrency and duplicate protection.
- The transactions of authentication lock in this order: `users`, then `email_verification_codes`. A request for an email code locks `email_code_request_counts` first (the row of the client address, then the row of the whole system) and then `email_verification_codes`, and no other table.
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
| Warehouse items and products | A listing and a deletion of the same item at once | The listing checks that the item is not deleted and has stock (`NO_STOCK` otherwise) under a shared lock on the `inventory` row, after the product row. A manual write-off needs the row exclusively, so it waits for the listing or the listing waits for it, and the listing never sees a quantity that is about to change. The deletion re-reads the product row after it holds the `inventory` row, and rolls back and retries if a product appeared in between. The item is never left deleted with a `LISTED` product. |
| Warehouse items | An item is edited while it is listed or being listed, or after it was deleted | The edit locks the item's product row first, if there is one, and refuses when the product is `LISTED`. Only then does it lock the `inventory` row and change the name or image. Listing takes the product row first as well, so the two cannot interleave. Lock order: `products`, then `inventory`. |
| Inventory | Manual changes and orders on the same stock | Row lock on `inventory`, as specified under Manual stock changes and Concurrency and duplicate protection. A manual change also checks under the lock that the item is not deleted, and a deletion needs the same lock, so one waits for the other. |
| Orders | A repeated submission | The unique key `UK(user_id, idempotency_key)` and the row locks, as specified under Order and Inventory Flow. A repeated submission adds no row to `order_attempts`. |
| Orders | The log of an attempt while another transaction lists, delists, or deletes the product | A completed row is written inside the order transaction, after the locks it already holds. A refused row is written after the rollback, in a transaction of its own that reads the product rows briefly and holds no other lock, so it cannot join a deadlock cycle of the order. The unique key `UK(order_no, product_id)` is the last guard against a number used twice. |
| Tickets | Two people take the same ticket from the pool, or a take meets an assignment | `UPDATE ... SET assignee_id = ?, status = 'IN_PROGRESS' WHERE id = ? AND status = 'OPEN'`. Zero rows updated means someone else got it first. |
| Tickets | Two status changes, such as archive and cancel, at once | Every transition is a conditional update on the current status, so only one succeeds. The other sees zero rows, reads the ticket, and reports its current state. |
| Tickets | Two managers reassign the same ticket, the assignee releases it as a manager reassigns it, or a reassignment meets a cancel or a resolve | Lock the ticket row, check the status is `IN_PROGRESS`, and compare the current assignee with the one the actor saw on the screen. If it differs, refuse with "ticket changed, please reload", so one change cannot silently overwrite another. Then update `assignee_id` and insert the `ticket_assignment_history` row, all in one transaction. |
| Tickets | A ticket is taken, assigned, or moved in while the person's account is disabled, loses its role, or is soft-deleted, or two tickets reach a person at 98 in progress | Before the change, lock that person's user row with `FOR UPDATE`, re-check that they are an eligible assignee, and count the tickets assigned to them that are `IN_PROGRESS`. Refuse at 99. The exclusive lock makes two simultaneous requests for the same person wait for each other, so the second one sees the first and cannot slip past the limit. Disabling a user or changing roles needs the same row, so one waits for the other. Lock order: `users`, then `tickets`. |
| Tickets | A ticket is created on items that are deleted at the same moment | See Warehouse items and tickets above: the creation locks each chosen item with `FOR SHARE` in ascending id after it inserts the ticket, and refuses the whole ticket if one is gone. Lock order: `users`, then `tickets` (a new row), then `inventory`. |
| Tickets | Two tickets are created at once by a person at 98 open | Lock the creator's user row with `FOR UPDATE`, count the tickets they created that are `OPEN`, refuse at 99, then insert the ticket, all in one transaction. Creations by the same person wait for each other, and creations by different people do not. Lock order: `users`, then `tickets`. |
| Tickets | A batch archive, the system job (7 days, or 99 waiting), and a manager's archive or a cancel overlap | Each ticket is archived in its own transaction through the status gate, `WHERE id = ? AND status = 'RESOLVED'`. A ticket already moved is skipped. Running the same batch or the same job twice is safe. The count of 99 is read before the job starts and need not be exact: if a ticket is resolved or archived in the meantime, the job only archives a few more or fewer, and the next run corrects it. |
| Tickets | A follow-up is opened as the earlier ticket changes | No lock is needed. The number is looked up in the creating transaction. A ticket that is `RESOLVED` or `CLOSED` never goes back, so the permission to follow it cannot be lost, and a number that matches no ticket is refused. The foreign key is the last line of defence. |
| Tickets | A user is disabled or soft-deleted while their tickets are released, resolved, or taken | Disabling or deleting locks the user row exclusively, then sets every `IN_PROGRESS` ticket of that user back to `OPEN` in the same transaction, each by a conditional update on the status and the assignee, and writes the history and log rows. A take or an assignment for that user waits for the same row and then fails the eligibility check. A release or a resolve by the user at the same moment is a conditional update on the status, so whichever commits first wins and the other sees zero rows. Lock order: `users`, then `tickets`. |
| Users and roles | Two people change the same user at once: two replacements of the roles, a replacement and a disabling, or a disabling and a deletion | See Steps of a change to a user. Each locks the user rows of its actor and of the target with `FOR UPDATE` in ascending id, so the second waits. A second replacement then finds roles that differ from the ones its actor saw and is refused with "user changed, please reload", so it cannot silently undo the first. A second status change finds the status already changed and reports it. Lock order: `users`. |
| Users and roles | The actor loses the role or the account that gives the authority, while acting | The actor's own user row is locked with the target's, and the actor's roles and status are read under that lock. A demotion or a disabling of the actor needs the same row, so one waits for the other, and an actor who was demoted first is refused. Lock order: `users`. |
| Users and roles | Two administrators remove each other at once, or the last administrator is removed | Both transactions lock the same two user rows in the same order, so they run one after the other, and the second is refused because its actor is no longer an administrator. The lock on the `ADMIN` row in `roles` and the count after the change are a second line that does not depend on the first, as under Administrator protection. Lock order: `users`, then `roles`. |
| Users and roles | The same role is granted twice | The user row lock makes the second request wait, and it then finds the role already held. `UK(user_id, role_id)` is the last line. |
| Users and roles | A role is taken away while a request of that user is running | Accepted for one request, except in the paths that lock a user row and read the roles again (see Requests already on their way). |
| Users and roles | A user is disabled, enabled, or deleted while their roles are replaced | The same user row, so one waits. A replacement that adds a role to a user who was just disabled or deleted is refused, and one that follows an enabling sees a usable account. |
| Authentication | Two wrong passwords at once | Each login records its result in a short transaction that holds the user row with `FOR UPDATE` (see Login steps), so the two wait for each other. `UPDATE users SET failed_login_count = failed_login_count + 1 ...` is atomic as well, and the lock time is worked out from the new value in the same transaction, so both attempts count. |
| Authentication | A password reset commits after a login has checked the old password and before it records the result | The password is checked outside the transaction. The login then locks the user row with `FOR UPDATE` and compares `password_hash` with the hash it checked. A different hash means the check is void: the login is refused with the general login error, with no token, and nothing is recorded. The reset updates the same row, so one waits for the other, and a login that commits first gets a token that the reset makes invalid through `password_changed_at`. Lock order: `users` only. |
| Authentication | Two logins of the same account at the same moment, on two devices | Each records its success in the transaction that holds the user row with `FOR UPDATE`, so they run one after the other, and each writes its own `session_id`. The one that commits last is the one signed in; the token of the other is refused with `SESSION_REPLACED` on its first request, and its stream, if it opened one, is told. Lock order: `users` only. |
| Authentication | A login commits while the stream of an earlier login is being opened | The token of the stream passed the check before the login committed, so the login sent its event to no stream. After the stream is registered, the server reads `session_id` once more without a lock, and sends `session-replaced` at once when it differs. A login that commits after the registration finds the stream and sends the event itself. Either way the stream is told. |
| Authentication | An account is disabled or soft-deleted while its owner logs in | Disabling and deleting lock the user row, and the login reads `status` and `deleted` again under the same lock. If the disabling commits first, the login answers `ACCOUNT_DISABLED` and issues no token. If the login commits first, the token is refused on its next request by the token check. Lock order: `users` only. |
| Authentication | An email code is submitted twice at once | Consume it with `UPDATE ... SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL AND expires_at > ? AND attempts < 5`, inside the transaction of the sign-up or the reset. Only one request updates the row, and the other waits for it, sees zero rows, and is answered `CODE_INVALID`. Wrong entries increase `attempts` atomically, in a transaction of their own, so they stay counted. |
| Authentication | A new code is requested while an older one is being used | The code is checked first, the password is hashed outside any transaction, and only then the code is used up. A request in between consumes the older code and inserts the new one in one transaction, so the conditional update of the sign-up or the reset finds zero rows and nothing is written: only the latest code works. |
| Authentication | Requests for a code at the same moment, for one address or near a limit | Each request adds 1 to its two rows of `email_code_request_counts` with `INSERT ... ON DUPLICATE KEY UPDATE request_count = request_count + 1`, which is atomic and holds the row until the transaction ends. Every request updates the row of the whole system, so the requests wait for each other there, and the 60-second check, the consuming of the older codes, and the insert of the new one run one request at a time. Of several requests for one address at the same moment one gets a code and the others get `TOO_MANY_REQUESTS`, and a limit cannot be passed by a race. A refused request rolls back and is not counted. The first requests of a new window insert the same row at once and can deadlock; the retry rule covers it. Lock order: the count of the client address, the count of the system, then the code rows. |
| Authentication | Two sign-ups with the same username or email, or one sign-up sent twice | The transaction checks the username and the address first and reports `USERNAME_TAKEN` or `EMAIL_TAKEN`. Two sign-ups at the same moment both pass that check, and the unique constraints reject the second insert, which is reported the same way. The refused transaction rolls back, so its code is still unused. Lock order: the code row, then the new row of `users`, which nobody can be waiting for. |
| Authentication | A password reset meets another reset, or the disabling or soft deletion of the account | The reset locks the user row with `FOR UPDATE` before it uses the code up, and checks under the lock that the account is not soft-deleted and that `password_hash` is still the hash the new password was compared with. A second reset with the same code waits, then finds the hash changed or the code used, and writes nothing. Disabling and deleting need the same row, so one waits for the other. Lock order: `users`, then `email_verification_codes`. |
| Authentication | The cleanup job runs while codes are requested or used | The job finds the dead rows with a plain read and deletes each by its id, with `expires_at < ?` repeated in the delete, so it locks only rows that no request can still use and never a range. Running it twice, or next to a request, changes nothing wrongly. |
| Authentication | Two console commands create an administrator at once, or with the same username | The command locks the `ADMIN` row in `roles` and creates the user in one transaction. The unique keys on `username` and `email` reject the second one with a clear message. Two different administrators are both valid. |

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
| Lines in one order | 20 | The product page sends one line. The limit keeps one request from locking many rows |
| Maximum quantity in one stock input | 999,999 | The starting quantity of an item, a stock-in, a write-off, and a correction are refused above it, so a slip does not reach the stock. The stock itself is limited only by the `INT` column. |
| List page size | 10, and at most 10; the product page 16 by default, and at most 24 | Two buttons, previous and next, the number of the page shown, and no count. On the product page the person chooses 8, 16, or 24. |
| Item image | JPEG, PNG, or WebP, at most 2 MB | Keeps the product page light and refuses files that are not pictures. The type is checked from the file content. |
| SKU, order number, and ticket number generation attempts | 5 | A generated SKU, order number, or ticket number that collides is replaced, and the creation fails with an error if all attempts collide, which is practically impossible with 36^8 values. |
| Open tickets created per person | 99 | A person cannot flood the board with tickets. It counts tickets the person created that are `OPEN`, and it applies to creating a ticket. |
| Tickets in progress per person | 99 | A person cannot hoard work. It counts tickets assigned to the person that are `IN_PROGRESS`, and it applies to taking, assigning, and receiving a ticket by reassignment. |
| Auto-archive delay | 7 days after `resolved_at` | A resolved ticket that nobody archived does not wait for ever. A `MANAGER` or an `ADMIN` can archive it earlier at any time. |
| Resolved tickets waiting before auto-archive | 99 in the whole system | When 99 `RESOLVED` tickets are waiting, the system archives all of them, so the waiting list stays short. |
| Archive job interval | 1 minute | A simple schedule. The job checks the 7-day rule and the count of 99. |
| Email code | 6 digits, valid 10 minutes, 5 wrong entries, resend after 60 seconds | Long enough to be inconvenient to guess, short enough to be quick to use |
| Password length and token life | 8 to 24 characters with a letter and a digit; the token lasts 12 hours | The owner's rule, and a token that needs no refresh because each request checks the account |
| Code requests per client address | 10 accepted requests in an hour | One client cannot request codes for many addresses. A person who mistypes an address a few times is not stopped |
| Code requests of the whole system | 200 accepted requests in a day, counted from midnight in Sydney time | The last guard against spam through the mail service, also from many client addresses, and well below the daily sending limit of the mail account. OpsFlow is an internal program and needs a few dozen codes a day |
| Display time zone of the time filter | `Australia/Sydney` | The days a person picks in a log are the days the users live by |
| Email code cleanup | Once a minute: delete the codes whose `expires_at` has passed, and the request counts whose window started more than two days ago. At most 1000 rows of each table in one run | Expired codes are of no use, and the owner wants a code kept for 10 minutes only. A count is of no use once its window is over. Nothing else is deleted. The limit keeps one run short after a backlog |

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
| `users.session_id` | `CHAR(32)`, null: 16 random bytes in hexadecimal, the id of the newest login. Null for an account that has not logged in since it was added, and for the SYSTEM identity |
| `email_verification_codes.email` | `VARCHAR(254)`, the same as `users.email` |
| `email_verification_codes.code_hash` | `VARCHAR(255)`. It holds 64 hexadecimal characters, or the marker `!NOT-SENT` |
| `email_code_request_counts.scope` | `VARCHAR(64)`: `ALL`, or `IP:` followed by the client address, which has at most 45 characters |
| `inventory.sku`, `inventory_item_events.sku` | `CHAR(8)`, generated, `A-Z` and `0-9` only (`chk_inventory_sku`) |
| `order_items.product_sku` | `VARCHAR(64)`, a copy of `inventory.sku` |
| `inventory.name`, `inventory_item_events.name`, `products.name`, `product_listing_events.name`, `order_items.product_name` | `VARCHAR(200)` |
| `inventory.image_url` | `VARCHAR(500)`, optional. It holds a link or a path to the image, and the image itself is stored by the application. |
| `orders.order_no`, `order_attempts.order_no` | `VARCHAR(64)` |
| `order_attempts.product_name` | `VARCHAR(200)`, the same as `products.name` |
| `tickets.ticket_no` | `CHAR(8)`, generated, `A-Z` and `0-9` only (`chk_tickets_ticket_no`) |
| `orders.idempotency_key` | `VARCHAR(128)` |
| Status and type fields, `order_attempts.result`, `roles.code` | `VARCHAR(32)`, restricted to the allowed values |
| `products.status`, `product_listing_events.action`, `inventory_item_events.action`, `tickets.source` | `VARCHAR(16)`, restricted to the allowed values |
| `tickets.title` | `VARCHAR(200)` |
| `inventory_transactions.reason`, `ticket_assignment_history.reason`, `tickets.cancel_reason` | `VARCHAR(255)` |
| `remark` fields | `VARCHAR(500)` |
| `operation_logs.ip_address` | `VARCHAR(45)` |
| `operation_logs.action`, `operation_logs.target_type` | `VARCHAR(64)` |
| `operation_logs.category` | `VARCHAR(16)`, restricted to the six values under Categories |
| Ticket descriptions, `tickets.resolution` | `TEXT` |
| `operation_logs.before_data`, `after_data` | `JSON` |

`orders.order_no`, `order_attempts.order_no`, `tickets.ticket_no`, and `orders.idempotency_key` use the `utf8mb4_0900_bin` collation, so they are compared exactly and `Abc` and `abc` are different values. `users.username`, `users.email`, `inventory.sku`, and `roles.code` keep the default case-insensitive, accent-insensitive collation, so two usernames that differ only in case count as the same.

`roles.name` and `roles.description` were not covered by the supplied values. Use `VARCHAR(64)` and `VARCHAR(255)` unless a reason to change appears.

### Time zone

Times are stored in UTC. `DATETIME` carries no time zone, and the defaults for `created_at` and `updated_at` use `CURRENT_TIMESTAMP(3)`, which follows the session time zone. Application code and the database must therefore follow one convention:

- Set the session time zone of every application connection to UTC. Changing only `serverTimezone` is not enough. For Connector/J, use `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- Have Java generate every time it writes, such as the expiry of an email code, in UTC.
- Run `SET time_zone = '+00:00';` before any manual SQL that involves time.
- Convert to a local time zone only when displaying times in the user interface.

## Initial Migration

The first executable migration is [`backend/src/main/resources/db/migration/V1__initial_schema.sql`](../../backend/src/main/resources/db/migration/V1__initial_schema.sql). It creates all 16 tables for MySQL 8.0.16 or later, using the types in Field Specifications. It creates structure only. The second migration, [`V2__seed_roles_and_system_identity.sql`](../../backend/src/main/resources/db/migration/V2__seed_roles_and_system_identity.sql), adds rows only: the roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`, and the SYSTEM identity. It changes no table, so it needs no new grant. The third migration, [`V3__email_code_request_counts.sql`](../../backend/src/main/resources/db/migration/V3__email_code_request_counts.sql), adds the 17th table, `email_code_request_counts`: an `id`, the `scope`, the `window_start`, and the `request_count`, with one row for a scope and a window (`uk_email_code_request_counts_window`), a count above zero (`chk_email_code_request_counts_count`), and an index on `window_start` for the cleanup. It has no foreign key and holds no email address and no code. It needs a grant, which is in the privilege script. Never edit a migration that has been applied. Add a new one.

The first application of this migration is done by Flyway, when the backend is connected to Flyway. Do not apply it to `opsflow` by hand, so that Flyway's schema history exists from the start. Experiments use a temporary database.

Run migrations with the `opsflow_migrator` account, which can change the schema. The application connects as `opsflow_app`, which can only read, insert, and update rows in `opsflow`. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#23-mysql-849) for the accounts and their credentials.

The migrations live in the Spring Boot backend at `backend/src/main/resources/db/migration/`, which Flyway reads by default (`classpath:db/migration`). They were moved there from the repository root, not copied, so that only one copy exists.

Beyond the rules listed in Initial Constraints, the migration adds these checks, each verified against MySQL 8.4:

- An order has no status and no cancellation fields: it is complete and final when it exists.
- An order item's `subtotal` equals `unit_price * quantity`.
- The snapshots of an inventory transaction agree with its type and quantity. For example, `SALE` lowers `available` by `quantity`.
- A ticket assignment record must change the assignee. A move from empty to empty, or from a user to the same user, is rejected.

## Concurrency and privilege verification

The schema, the per-table privileges, and the order and inventory flows were verified against MySQL 8.4 in a temporary database, using [`db/verification/run-db-verification.ps1`](../../db/verification/run-db-verification.ps1). The flows in this document are written as stored procedures in [`concurrency-fixtures.sql`](../../db/verification/concurrency-fixtures.sql), and several `mysql` sessions run them at the same moment, under the restricted application account, so the verification also proves that the per-table grants are enough for the flows. Every test is listed by block, with what it shows and what is not tested yet, in the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md).

Latest full run (2026-11-13, MySQL 8.4): 161 checks passed, 0 failed (v1.11; the runs of v1.9 and v1.10 had 156, and the run of v1.8 had 152). The script applies `V1` and `V3`; `V2` only adds rows, and the checks make their own. Five checks are new in v1.11: `V3` applies, and the table `email_code_request_counts` refuses a count that is not positive, keeps one row for a window, refuses a second row for the same window, and can be counted in and cleaned by the application account. The run covers the 17 tables and their checks; the warehouse items (the generated SKU, the unique name among the items that are not deleted, the soft delete) and their events; products and listing events; the orders that complete at once and are final, with the `SALE` transaction rules (and no `RETURN` type); the record of every order attempt, completed or refused, with its constraints and its append-only privileges; the manual stock types and their reason and operator rules; the ticket structure; the privileges of the application account; and the races listed below. Races were repeated 4 to 12 rounds each. The temporary database and accounts were dropped afterwards. The ticket rules, the manual stock changes, and the concurrency rules of the other modules have no race checks yet; they come with the code (T-17, T-34). Since v1.7, nine checks were removed (the comment table and the flag `must_change_password` are gone, and the confirmation, timeout, and exception path checks of earlier versions belong to flows that no longer exist), and eleven were added for `order_attempts`, the two reserved `users` columns, and the index of the item log. The first run of the v1.8 script found one wrong check (it still expected three columns with the binary collation, and `order_attempts.order_no` is the fourth), which was fixed in the check. The final v1.8 script also covers the ticket column `order_no` and the four time indexes of the logs.



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
| Identifiers | `Abc` and `abc` are different idempotency keys, `ORD-aaa` and `ORD-AAA` are different order numbers, and `Alice` and `alice` are still the same username. |

What this does not show: the procedures are an executable specification of the flows in SQL, not the application code, so the application's own retry logic, connection pool, and time zone handling still need the same checks once the backend exists. The run uses one server at the default `REPEATABLE READ` isolation level. A passing run shows that the design holds under the interleavings it produces, not that every possible interleaving was tried.

To run it, use `powershell -STA -File db/verification/run-db-verification.ps1`. It asks for the MySQL root password in a masked dialog, creates a temporary database and two temporary accounts, and drops them at the end. It never touches `opsflow`. Run it again whenever the schema, the privileges, or the flows change.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries. The ticket lists show `SYSTEM` tickets before `MANUAL` ones and then the chosen time order, so their indexes lead with `status` and `source`: `(status, source, created_at)` for the pool and the work lists, `(status, source, closed_at)` for the archive, and `(status, source, cancelled_at)` for the cancelled area. `tickets(created_by, status)` serves the open count, `tickets(assignee_id, status)` the in-progress count, `tickets(order_no, status)` the lookup of the tickets of an order number, and `tickets(status, resolved_at)` the archive job. `inventory_transactions(inventory_id, created_at)`, `product_listing_events(product_id, created_at)`, and `order_attempts(product_id, created_at)` serve the logs by item or product and by day, and `order_attempts(created_at)`, `product_listing_events(created_at)`, `inventory_item_events(created_at)`, and `inventory_transactions(created_at)` serve the logs of all products and all items, and `operation_logs(created_at)` the latest logs. There is no order list, so `orders` has no index for one. An index for a screen that does not exist yet is added by a new migration when the screen is designed.
- Apply unique constraints to business identifiers such as usernames, role codes, warehouse item SKUs, and order numbers. Keep the names of the warehouse items that are not deleted unique with `UNIQUE (name, is_active)`, and put a unique constraint on `products(inventory_id)`, so an item has at most one product.
- Apply composite unique constraints to `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, `inventory_transactions(order_id, inventory_id, type)`, and `ticket_inventory(ticket_id, inventory_id)`. Create each as one composite key and never as separate unique keys on its columns. The physical ERD writes each one in the key column, for example `UK(user_id, idempotency_key)`.
- Require quantities to be greater than zero, stock quantities to be greater than or equal to zero, and monetary values to be non-negative.
- On `inventory_transactions`, require `quantity > 0`, require the two snapshot fields to be greater than or equal to zero, require `operator_id` for every type, require that `order_id` is present exactly when `type` is `SALE`, and require a non-blank `reason` for `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`. Enforce these with `CHECK` constraints and validate them in the service layer.
- Use fixed-precision decimals for money.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation, and do not edit or delete order items after the order is created.
- Place order creation, order-item creation, the stock deduction, the `SALE` transactions, and the completed `order_attempts` rows in the same transaction boundary. Write the rows of a refused attempt in a short transaction of their own after the rollback.
- On `order_attempts`, require a known result (`chk_order_attempts_result`) and an outcome that agrees with it: a completed row has its order and deducted exactly what it ordered, and a refused row has no order and deducted nothing (`chk_order_attempts_outcome`). Keep `UK(order_no, product_id)`, so a product line is recorded once for an order number.
- Treat inventory transactions, ticket assignment history, ticket inventory links, product listing events, inventory item events, order attempts, and operation logs as append-only trace data. Enforce this with privileges as well as in the service layer: after the first migration is applied, the application account gets only `SELECT` and `INSERT` on these tables, and on `order_items`, which is frozen. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) and [`db/admin/restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql).
- Use controlled status values. Enforce valid state transitions in the service layer, and enforce each ticket transition with the conditional update (the status gate) described above. Orders have no status.
- Enforce the ticket status transitions, and the fields each one must fill, in the service layer only: an assignee for `IN_PROGRESS` and none for `OPEN`, a resolution for `RESOLVED`, `closed_by` and `closed_at` for `CLOSED`, and a reason, `cancelled_by`, and `cancelled_at` for `CANCELLED`. The database checks only the allowed status values and the foreign keys, so a service-layer bug can leave a ticket without a resolution. This was chosen on purpose; the service layer and its tests must not skip these rules (T-33).
- Create the SYSTEM identity (`users.is_system` true) in a seed migration (`V2`). Refuse in the service layer to log it in, edit it, disable it, soft-delete it, or grant it a role, keep it out of user lists and assignee pickers, and exempt it from the ticket limits (T-36).
- On `products`, require a price that is not negative, a status of `LISTED` or `UNLISTED`, and the pairing of the delisting fields: a `LISTED` product has no `delisted_at` and no `delisted_by`, and an `UNLISTED` product has both (`chk_products_delisted`). The rule that name and price do not change while a product is `LISTED`, and that a listing needs a price above zero, is enforced in the service layer.
- On `inventory`, require the SKU to be 8 characters from `A-Z` and `0-9` (`chk_inventory_sku`), and require a deleted item to carry both `deleted_at` and `deleted_by` (`chk_inventory_deleted`). Never delete an `inventory` row: delete it softly. The rules that a deleted item cannot be listed, edited, stocked, ordered, or linked are enforced in the service layer under the row lock.
- On `tickets`, require `source` to be `SYSTEM` or `MANUAL`, with no default so that a missing value fails, and require `order_no` for an `ORDER_EXCEPTION` ticket (`chk_tickets_order_required`; the number is checked against `order_attempts` by the service layer). The rule that `source` agrees with the creator being the SYSTEM identity, and that `ticket_inventory` links are written only with the ticket, is enforced in the service layer (T-33).
- Keep the fields of a ticket that are fixed at creation (`title`, `description`, `type`, `source`, `order_no`, `follows_ticket_id`, `created_by`) unchanged, in the service layer only (T-33).
- A ticket follows at most one earlier ticket, set only when the ticket is created. The foreign key `follows_ticket_id` rejects a missing ticket. The rule that the earlier ticket is `RESOLVED` or `CLOSED` is enforced in the service layer.
- Include `created_at` and `updated_at` on primary business tables. Historical and frozen tables, such as `inventory_transactions` and `order_items`, must include at least a creation timestamp.
- Refuse role grants to users who are not usable accounts (`status` is not `ACTIVE`, or `deleted` is true) in the service layer, because no foreign key can. Count a user's roles in a permission check only when the user is a usable account. Set `status` to `DISABLED` when a user is soft-deleted. Write a `ROLE_GRANTED` or `ROLE_REVOKED` row to `operation_logs` in the same transaction as every change to `user_roles`.
- Never insert, update, or delete a `roles` row from the application. The four seeded roles are fixed, and a new role comes only with a migration and the code for its permissions.
- Refuse any change that would leave no usable account holding `ADMIN`, after locking the `ADMIN` row in `roles`. A new user has no role until an administrator grants one.
- Treat `ADMIN` and `MANAGER` as protected roles: let only `ADMIN` appoint or dismiss managers and administrators. Let `MANAGER` assign only roles below manager, and only to users who hold neither `ADMIN` nor `MANAGER`. Refuse any request to disable or soft-delete one's own account or to change one's own roles.
- Create the first administrator on the server with a console command that asks for the password with hidden input. Never write that password to a file, an environment variable, or a log.
- Lock an account after repeated wrong passwords on the schedule in Login security and account recovery, and refuse attempts without checking the password while it is locked. Store email codes as a keyed hash, and let the application account insert, update, and delete `email_verification_codes` and `email_code_request_counts`, the only two tables whose rows the application deletes in its normal work (expired codes and the counts of windows that are over, see Email codes). Tell a person who signs up that an address already has an account, answer a request for a recovery code the same way whether or not a mail is sent, and refuse a request beyond the limits per client address and for the whole system. On every request, load the account and its roles from the database, and refuse a token issued before `users.password_changed_at`.
- Do not cascade deletes into history. Foreign keys from `inventory_transactions`, `ticket_assignment_history`, `ticket_inventory`, `product_listing_events`, `inventory_item_events`, `order_attempts`, and `operation_logs` must not delete or null out history rows.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Add a reservation step and a separate reservation table if orders ever need partial confirmation, partial release, or reservations that outlive their order. Orders complete at once today.
- Add payment and refund entities and a payment-driven order status when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it. Retention is decided: every record (logs, stock movements, order attempts, tickets and their history, events, images) is kept for ever, and only expired email codes are deleted.

When a field or relationship changes, update the migration history, physical ERD, and this document together.
