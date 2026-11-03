# Database Iteration v1.7

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.7 |
| Date | 2026-10-24 |
| Baseline | v1.6, see [database-v1.6.md](database-v1.6.md) |
| Type | Design rules and schema changes (the warehouse comes first: `inventory` and `products` change meaning, and one new table) |
| Table count | 16 (two added: `product_listing_events` and `inventory_item_events`) |
| Trigger | The owner's design of the products part: only a manager or an administrator lists a product, from a catalog of items that are already in the warehouse, and an operator can only order. Later in the same iteration the owner added how warehouse items are created and deleted, the rule that every quantity must be valid, and how a person opens a ticket on chosen items. Then the owner decided that an order needs no confirmation and completes the moment it is placed, and that deleting an item does not touch its stock |

## Summary

Version 1.7 makes the warehouse the starting point and turns the products part into a thin layer on top of it. A product is a warehouse item that has been put on sale. The part does three things: list, delist, and order.

- A warehouse item (`inventory`) exists before it is ever sold. It has a SKU, a name, an optional image, its stock, and a record of who added it and when.
- A product (`products`) is created the first time an item is listed, and keeps its id for ever. Listing and delisting change its status. Neither asks for a reason, and each is recorded in a new table, `product_listing_events`.
- Only a manager or an administrator lists or delists. A listed product shows only an image, a name, and a price. An operator can only order.
- Name and price do not change while a product is listed. To change either, delist and list again. Old orders keep the name and price they were placed under.
- Order creation locks the product rows with `FOR SHARE` and checks the name and price the user saw, so a delisting or a price change makes a stale order fail with a clear reason.
- Delisting cancels, by the system, the open and in-progress tickets linked to the item, with a reason that can be traced.
- A manager or an administrator adds a warehouse item with a unique name, an optional image, and an optional starting quantity. The program generates the SKU (8 random characters). An item can be deleted softly, which delists its product, cancels its open tickets, writes off its stock, and is recorded in a new table, `inventory_item_events`.
- Every quantity in the program must be valid, and the rule is written down once (Valid quantities).
- A person opens a ticket on zero or more chosen warehouse items from the ticket pool screen, and the creation fails with a clear result if a chosen item was deleted in the meantime.
- An order completes the moment it is placed and is final. There is no reservation, no confirmation step, no 30-minute timeout, no timeout job, no status, no cancellation, and no exception path. A wrong order is corrected through a ticket, a manual stock change, and a new order.
- Deleting an item no longer writes off its stock or waits for reserved stock: the stock left on a deleted item no longer matters, and a person counts the stock again when the item is added again.
- A ticket number is 8 random characters (`A-Z`, `0-9`), and the ticket form has an optional field for the number of an earlier ticket, which makes the new ticket a follow-up to it.
- Q-28 is decided: a ticket on several products is cancelled when any one of them is delisted or deleted.
- The indexes for the catalog search, the product page, and the product records are created.

## Changes

### The warehouse comes first

- `inventory` is now the warehouse item. It gains `sku` (unique), `name`, `image_url` (optional), and `created_by`, and it loses `product_id` and `uk_inventory_product`. Its `created_at` is the time the item entered the warehouse. Later in the iteration it gained `deleted_at`, `deleted_by`, and the generated column `is_active`, and its SKU became `CHAR(8)` with a format check.
- `products` is now an item put on sale. It gains `inventory_id` (unique, so an item has at most one product), and it loses `sku`, `description`, `deleted`, and the old `ACTIVE` and `INACTIVE` statuses. Its statuses are `LISTED` and `UNLISTED`, with `listed_at`, `listed_by`, `delisted_at`, and `delisted_by`. A `CHECK` pairs the delisting fields with the status.
- The product row is created at the first listing and never deleted. Its id stays the same through every delisting and listing, so every order stays tied to it.
- The stock records (`inventory`, `inventory_transactions`) and the product records (`products`, `product_listing_events`) are separate. Listing never creates or changes stock, and a stock-in never lists. A stock-in is its own record, as before.
- Soft deletion of products is gone. A product is listed and delisted, and nothing is deleted. This closes the product part of Q-16. The rest of Q-16 closed with the order change below: an order is never pending, so nothing waits for a disabled user.

### Listing and delisting

- **Who:** only a `MANAGER` or an `ADMIN`. **Reason:** none asked. **Record:** a row in `product_listing_events` (the product, the item, the name and price at that moment, the user, and the time) and a row in `operation_logs`.
- **The catalog:** pressing List opens the warehouse items that are not listed now, searchable by item name and by nothing else (the owner removed the search by entry time). The image need not show there. Listed and deleted items are not offered, and the service layer refuses them.
- **Listing:** asks for a price, above zero, and shows the name (the item's name, changeable in the dialog). It does not need stock.
- **A listed product cannot be edited.** To change the name or the price, delist and list again. The name or image of a warehouse item can be edited only while the item is not listed.
- **Delisting:** sets `UNLISTED`, writes the event, and cancels the tickets of the product, in one transaction.
- **Tickets of the product:** every `OPEN` or `IN_PROGRESS` ticket linked through `ticket_inventory` to the item is cancelled by the system, with the SYSTEM identity as canceller and the reason `PRODUCT_DELISTED:` and the product id. Each cancellation is logged. A ticket opened after the delisting is not cancelled. A ticket that links several products is cancelled when any one of them is delisted. The owner decided this (Q-28 is now only about how the people concerned are reminded), and the screen shows a message such as "Widget has been delisted, please open the ticket again" built from the reason code.
- **Orders already placed:** an order is completed the moment it is placed, so delisting changes nothing about orders that exist. Q-27 is closed by that decision.

### The product page and the order dialog

- The product page lists the `LISTED` products by name, each with its image, name, and price only, and can be searched by name. Everyone who can view products sees it. The List and Delist controls show only to a manager or an administrator, and the Order button only to those who may order.
- The Order button opens a dialog for the quantity, typed or changed with plus and minus, then confirms. The screen shows success with the order number, or the reason for the failure.
- The results are `COMPLETED`, `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, `OUT_OF_STOCK`, `INVALID_QUANTITY`, `NOT_ALLOWED`, and `BUSY`. The owner asked for a clear reason on failure and said I should fill in the cases. A failed creation leaves no order, so it opens no ticket.
- The screen does not show the stock. A shortage appears only when the order fails.

### Order creation checks the product

- Order creation reads each product row with `SELECT ... FOR SHARE`, in ascending product id, before it writes anything. It checks that the product is `LISTED`, that the name and price match what the user saw, and that the quantity is a whole number from 1 to 9999. The three failures are `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, and `INVALID_QUANTITY`.
- The shared lock lets orders run side by side, while a delisting or a listing needs the row exclusively. So an order is never created under a product after its delisting has committed. This replaces the v1.6 rule that order creation takes no lock on products.
- The inventory rows are reached through `products.inventory_id`, and the SKU of the order item is copied from the warehouse item.

### Adding and deleting warehouse items

- **Adding:** a `MANAGER` or an `ADMIN` gives a name (required, not used by another item in the warehouse, compared without regard to case), an optional image, and an optional starting quantity (empty means 0, and a number must be valid). The program generates the SKU, 8 random characters from `A-Z` and `0-9`, and generates another on a collision. A starting quantity above 0 becomes a `RESTOCK` with the reason `initial stock`. The item, the restock, an `inventory_item_events` row, and the log row are one transaction.
- **Name rule in the database:** `UNIQUE (name, is_active)`, where `is_active` is a generated column that is 1 for an item that is not deleted and empty for a deleted one. MySQL allows many empty values in a unique key, so the name of a deleted item is free again.
- **Deleting:** soft, by a `MANAGER` or an `ADMIN`, with no reason, recorded. The owner said the product of a deleted item must be deleted too and that the deletion is recorded as an inventory record. Because orders, stock records, tickets, and events refer to the item, the row stays and carries `deleted_at` and `deleted_by`. The product row also stays, is delisted, and can never be listed again.
- **What the deletion does in one transaction (the owner did not specify these, so they are my defaults, see Q-30):** it delists the product; cancels the open and in-progress tickets linked to the item with the reason `ITEM_DELETED:` and the item id; marks the item deleted; and writes the `inventory_item_events` row and the logs. It does not touch the stock: the owner said that the stock left on a deleted item no longer matters and that a person verifies the quantity when the item is added again. The first version of this rule wrote the stock off and refused while stock was reserved; the owner replaced it.
- **A new table:** `inventory_item_events` is append-only (the application account has `INSERT` only). It holds the item, the action `CREATED` or `DELETED`, the SKU and name at that moment, the quantity (the starting stock, or the stock the item showed when it was deleted, for the record), the user, and the time. It is separate from `product_listing_events`, because the owner asked for the addition and deletion of items to belong to the inventory records.

### Valid quantities

- The owner asked that every value related to a quantity be valid by default, and that this be recorded. It is now a rule with its own section: a whole number, within the range of its use, and never one that takes stock below zero or past the `INT` column. It covers the starting quantity, stock-ins, write-offs, direct sets, and order lines.
- Two ranges are my defaults: one stock input (a starting quantity, a stock-in, a write-off, or a direct set) is at most 999,999, and an order line stays at 1 to 9999. The 9999 limit applies to one order line only. It is not the stock, and the screen never shows the stock (Q-30).

### Opening a ticket on items

- The ticket pool screen gets a Create ticket button for the roles that may create tickets. The person chooses zero or more warehouse items from a list that works like the catalog for listing, searched by name only, then fills in the type, title, and description, and confirms.
- The list shows every item that is not deleted, listed or not, because a ticket can be about an item that is not on sale. This is my default (Q-30).
- The confirmation is one transaction: the creator's user row, the limit of 99, the ticket, then a shared lock on each chosen item in ascending id with a check that it still exists, then the links. If any chosen item is gone, nothing is written and the screen names the items that changed (`ITEM_CHANGED`). The other results are `CREATED`, `LIMIT_REACHED`, `NOT_ALLOWED`, and `BUSY`.
- A deletion takes the item exclusively and cancels the linked open tickets only after it holds the row, so a ticket that committed first is cancelled, and a ticket that comes after is refused. This is the one place where tickets are locked after `inventory`, and the design document explains why it is safe.

### Orders complete at once and are final

The owner explained that placing an order shows its result straight away and that no one confirms it. The order part was changed to match. I first kept a cancellation of completed orders as a default of mine; the owner asked what it was for and chose to remove it.

- **Create:** one transaction checks the products, inserts the order with its items, locks the stock rows in ascending id, refuses with `OUT_OF_STOCK` when any item is short, deducts `available_quantity`, adds to `sold_quantity`, and records one `SALE` per item with the ordering user. A failed placement leaves no order.
- **Final:** an order has no status and is never edited, cancelled, or deleted. `orders` lost `status`, `reserved_until`, `finished_at`, and `status_reason`; the cancellation fields I had added were removed again. `inventory.locked_quantity` is gone. The transaction types are `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, `ADJUST_OUT`, and `SALE`, with only `available_before` and `available_after` as snapshots, and `operator_id` is required for every type.
- **Correcting a wrong order (the owner's process):** the operator opens a ticket; the handler, the operator or someone else, puts the stock back to what it was before the order with a manual stock change; the handler resolves the ticket; the operator places the order again. The wrong order and its `SALE` stay in the history, and `sold_quantity` keeps counting what orders took.
- **Removed:** the confirm flow, the cancel path with its timeout, the timeout job and its three operating parameters, the statuses (`CREATED`, `COMPLETED`, `CANCELLED`, `FAILED`), the exception path, the rule that an order is shown as waiting to be handled, the two automatic ticket cases, and the `RETURN` transaction type. The program opens no ticket by itself today. The ticket fields for it (`tickets.source` `SYSTEM`, the SYSTEM identity as creator) stay for the day an automatic case is added.
- **A finding of the verification:** copying the SKU into the order items with `INSERT ... SELECT` from `inventory` took shared locks on the stock rows before the exclusive locks. Two orders that list the same items in opposite order then deadlocked on the lock upgrade, in 10 of 12 rounds. The SKU is now read with a plain read, and the design says so.

### Ticket numbers and the follow-up field

- **Ticket number:** `tickets.ticket_no` changed from `TKT-` and a UUID to 8 random characters from `A-Z` and `0-9`, generated when the ticket is created (`CHAR(8)`, `chk_tickets_ticket_no`, still unique). It is short so that people can read it and type it. Order numbers keep their form.
- **Follow-up field:** the Create ticket form has an optional field for the number of an earlier ticket. The program looks it up inside the creating transaction and sets `follows_ticket_id`. The ticket is refused with `FOLLOW_NOT_FOUND` when no ticket has that number, and with `FOLLOW_NOT_ALLOWED` when the ticket is not `RESOLVED` or `CLOSED`. The existing follow-up rules did not change: one earlier ticket at most, set at creation, never changed.
- **Concurrency:** no lock is needed, because a `RESOLVED` or `CLOSED` ticket never goes back. The foreign key stays as the last line of defence.

### Concurrency by Module

- The three old product rows are replaced by six: two managers listing the same item, a listing against a delisting, an order against a delisting or a listing, a delisting that cancels tickets while they are taken, resolved, cancelled, or archived, two items with the same SKU, and an item edited while it is listed or being listed.
- Later in the iteration these were extended: two items added with the same name; two generated SKUs that collide; two deletions of one item; an order against the deletion of its item (the order is completed and then the item is deleted, or the order is refused); a ticket opened on an item against its deletion; a listing against a deletion; and a ticket created on items that are deleted at the same moment. A manual stock change also checks that the item is not deleted, under the lock it already takes.
- The lock order is now `users`, `roles`, `products`, `tickets`, `orders`, `inventory`. A delisting goes from the product row to the ticket rows, order creation from the product rows to the order and the stock, and an edit of an item from its product row to its inventory row.

### Indexes

- `inventory`: `uk_inventory_sku`, and `uk_inventory_name_active` on the name and the generated `is_active`, which also serves the name search and prefixes. The plain name index and the entry-time index were dropped, because the owner removed the search by time and the unique key covers the name.
- `products`: `uk_products_inventory`, and `idx_products_status_name` for the product page and its search by name.
- `product_listing_events`: by product and time, by item, and by user.
- The name search is a `LIKE` match, which an index serves only for a prefix. For the expected size of a catalog that is enough. A full-text search is not part of the first version. The owner has said that tags are an idea for later (see [future-ideas.md](../future-ideas.md)).

### Product logs

- A first version of the log formats for `ITEM_CREATED`, `ITEM_UPDATED`, `ITEM_DELETED`, `PRODUCT_LISTED`, and `PRODUCT_DELISTED` is in the design document, next to the ticket logs.

### Items decided as not for the first version

- Categories, units of measure, and low-stock alerts are not part of the first version (this closes Q-14). The owner wants the simplest basics: list, delist, and order. A tag system is recorded as an idea in [future-ideas.md](../future-ideas.md).

### Migration, fixtures, and tests

- `V1__initial_schema.sql` was edited in place, because it has not been applied anywhere (T-01 is still open). `inventory` is now created before `products`, and `product_listing_events` and `inventory_item_events` are new.
- `restrict-app-privileges.sql` gives the application account `INSERT` on `product_listing_events` and `inventory_item_events`, in the append-only group. A joined pair of grants on one line was also split.
- The verification fixtures and script were updated for the new schema and gained procedures and tests for listing, delisting, and ordering under a product, and later for adding and deleting items and opening a ticket on items (the procedures `create_item`, `delete_item`, and `create_ticket`, which takes the follow-up number). The SKUs in the fixtures became 8 characters, because the database checks the format. **The full run on 2026-10-24 passed: 149 checks, 0 failed** (the first runs found a SKU test that was wrong, a retry detail in the script, and the deadlock described under Orders complete at once, and all three were fixed). The owner will add more tests, and then everything is run together. The test log says which checks are new.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Where the product comes from | The warehouse first: `inventory` is the item, and a product is created at its first listing and kept for ever | The old model, in which a product came first and its inventory row was created with it, which does not fit "list from what is already in the warehouse" |
| Product and stock records | Separate. Listing never touches stock, and a stock-in never lists | One record for both, which would tie the stock history to the sales state |
| Changing name or price | Only by delisting and listing again, with the new values | Editing a listed product, which would let a price change under an open order screen |
| What an old order keeps | The name and price it was placed under, copied into the order item. The product id stays the same | Referring to the current product values, which would rewrite history |
| Reason for listing and delisting | None asked. A record with the user and the time is kept | Asking for a reason, which the owner declined |
| Stale order screens | The request carries the name and price seen, and creation fails with `PRODUCT_DELISTED` or `PRODUCT_CHANGED` | Accepting the order at the current values, which could charge a price the user never saw |
| Order against delisting | Order creation reads the product row with `FOR SHARE`, a delisting needs it exclusively | No lock on products (the v1.6 rule), which lets an order commit after the delisting and make an order under an unlisted product |
| Tickets of a delisted product | Cancelled by the system in the same transaction, with `PRODUCT_DELISTED:` and the product id | Leaving them open, which the owner did not want; cancelling them later, which leaves a window |
| Soft deletion of products | Removed. A product is listed or delisted | Keeping `deleted`, which nothing would use |
| Search | A `LIKE` match on the name only, in the catalog for listing and in the list for opening a ticket | A full-text index, which adds a parser and tests for a catalog that is small; a search by entry time, which the owner removed |
| Who makes the SKU | The program, 8 random characters from `A-Z` and `0-9`, checked for format and uniqueness by the database | A SKU typed by a person, which the owner replaced with a generated id |
| Name of an item | Required, and unique among the items that are not deleted, without regard to case | A name that may repeat, which would make the lists ambiguous |
| Deleting an item | Soft, with `deleted_at` and `deleted_by`, because history refers to the row | Removing the row, which the foreign keys of orders, stock records, tickets, and events forbid |
| Stock of an item being deleted | Left alone. It no longer matters, and a person counts it when the item is added again | Writing it off and refusing while stock is reserved (the first version), which the owner replaced |
| When an order takes its stock | At once, in the transaction that places it, with a `SALE` row. No reservation, confirmation, timeout, or status | A 30-minute reservation and a confirmation step (the design until now), which the owner said is not needed |
| Cancelling an order | None. An order is final, and a wrong order is corrected through a ticket, a manual stock change, and a new order | A cancellation that returns the stock (my first default), which the owner removed |
| Automatic tickets | None today. A failed placement leaves nothing to investigate | The exception path with its tickets, which needed an order that is stopped half-way |
| Copying the SKU into an order item | A plain read | `INSERT ... SELECT` from `inventory`, which deadlocks two orders that list the same items in opposite order |
| Tickets of a deleted item | Cancelled with `ITEM_DELETED:` and the item id, as for a delisting | Leaving them open on an item that no longer exists |
| Starting quantity | Optional, 0 by default, recorded as a `RESTOCK` with the reason `initial stock` | Setting the quantity on the row without a record, which would break the rule that history explains every unit |
| Valid quantities | One rule for every quantity, with ranges in Operating Parameters | Checks scattered by module |
| Ticket on several products | Cancelled when any one is delisted or deleted | Cancelled only when all are, which the owner declined |
| Naming the earlier ticket of a follow-up | The 8-character ticket number, typed in an optional field and looked up in the creating transaction | A pick list of tickets, which the owner did not ask for and which would not scale |
| Form of a ticket number | 8 random characters from `A-Z` and `0-9`, like the SKU | `TKT-` and a UUID, which is too long to read out or type |
| Opening a ticket on items | Zero or more items, locked with `FOR SHARE`, the whole ticket refused when one is gone | Linking without a check, which could link a deleted item; adding links later, which the design forbids |
| Stock on the product page | Not shown. A shortage appears when the order fails | Showing stock, which the owner did not ask for and which is stale as soon as it is shown |
| Image | An optional link on the warehouse item, shown on the product page | An image on the product, which would have to be copied at every listing |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: the table count, Product and Inventory (warehouse items, adding and deleting items, listing and delisting, the product page, valid quantities), Creating a ticket on items, Create order, the order dialog, Correcting a wrong order, Exceptions, Product logs, the Permission Matrix, Concurrency by Module, Operating Parameters, Relationship Summary, Field Specifications, Initial Constraints, and the Change Log |
| [Initial migration](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) | Updated in place: `inventory` and `products` redefined, `product_listing_events` and `inventory_item_events` added, with their indexes and checks; not yet applied anywhere |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: `INSERT` on `product_listing_events` and `inventory_item_events` |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: `product_listing_events` and `inventory_item_events` in the append-only list |
| [Verification script and fixtures](../../db/verification/run-db-verification.ps1) | Updated: the table count is 16, the seeds and the stock procedures follow the new schema, `create_order` checks the products, and there are new procedures (`check_products`, `list_product`, `delist_product`, `create_item`, `delete_item`, `create_ticket`), 28 new structure and privilege checks, and 15 new concurrency checks. **Run on 2026-10-24: 149 passed, 0 failed** |
| [Test log](../../db/verification/concurrency-tests.md) | Rewritten for the new model: blocks A to D with 149 checks, the latest run, and how the count grew |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated: `inventory` and `products` redefined, `product_listing_events` and `inventory_item_events` added, and the connectors between them and the users |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated in the same way, with main columns only |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn) | Updated: order creation completes the order at once, the confirmation and timeout sections are gone, a new section shows how a wrong order is corrected through a ticket, and there are sections for listing and delisting, adding and deleting a warehouse item, and opening a ticket on items |
| [project-todo.md](../project-todo.md) | Updated: T-10, T-11, and T-14 rewritten for orders that complete at once; T-38 removed; T-39 extended, T-40 and T-41 added; Q-14, Q-16, and Q-27 closed; Q-04 narrowed; Q-28, Q-29, and Q-30 added and reworded after the owner's answers |
| [future-ideas.md](../future-ideas.md) | A tag system added, at the owner's request |

## Open Items

- Implement the products part (T-39), the ticket form (T-40), the quantity checks (T-41), and the order flows (T-10).
- Run the verification script with the new fixtures and tests, together with the tests the owner will add, and record the result here.
- How the people concerned are reminded when a ticket is cancelled by the system (Q-28), the grouping of all records by stage (Q-29), and the defaults of the products part that need the owner's confirmation (Q-30).
- The audit formats of the other actions (Q-03).
- The open items of [database-v1.6.md](database-v1.6.md), [database-v1.5.md](database-v1.5.md), [database-v1.4.md](database-v1.4.md), [database-v1.3.md](database-v1.3.md), and [database-v1.2.md](database-v1.2.md) still apply, except those listed above.
