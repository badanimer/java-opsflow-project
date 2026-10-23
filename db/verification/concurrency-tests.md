# Verification Log: Structure, Privilege, and Concurrency Tests

## Purpose

This file records what [`run-db-verification.ps1`](run-db-verification.ps1) checks, block by block, so the tests can be reviewed and looked up without reading the script. For each block it says what is tested, how, what is expected, and which design rule it supports. It also says what is not tested yet.

It is not the design. The rules live in [database-design.md](../../docs/database/database-design.md). It is not a history of every run either: it keeps the latest result and a short list of how the test count grew.

The check names in this file are the names the script prints, so a line in the run output can be found here with a search. Checks that repeat one pattern over several tables or types are written as one entry with the pattern: the twelve reason and operator checks on the manual stock types, and the refusals of the application account. The script prints one line for each of them.

## How the tests run

- The script creates a temporary database, `opsflow_verify`, and two temporary accounts, then drops them at the end. It never touches `opsflow`. The MySQL root password is asked for in a masked dialog and is used only to create and drop them.
- It applies [`V1__initial_schema.sql`](../migration/V1__initial_schema.sql), the stored procedures in [`concurrency-fixtures.sql`](concurrency-fixtures.sql), and [`restrict-app-privileges.sql`](../admin/restrict-app-privileges.sql), adapted to the temporary names.
- **The stored procedures are an executable specification of the flows, not the application code.** They exist so that the rules can be tried against a real MySQL server before the backend exists. The application's own retry logic, connection pool, and time zone handling still need the same checks once the backend is built (T-17, T-34).
- Concurrent tests start several `mysql` sessions at the same moment, released together by a lock barrier (`GET_LOCK('go')`), and run them as the restricted application account. This also proves that the per-table grants are enough for the flows.
- A session that fails with a deadlock or a lock wait timeout (its result starts with `RETRYABLE`) is run again up to two more times, the way the application would, unless the test says it must not be retried.
- `-Rounds` sets how many times each race is repeated. The default is 12. Some races use a third or a half of it, as noted below. More rounds make a rare interleaving more likely to show up. A passing run shows that the design holds under the interleavings it produced, not that every possible interleaving was tried.
- To run it: `powershell -STA -File db/verification/run-db-verification.ps1`.

## Latest run

**2026-10-24, MySQL 8.4: 149 passed, 0 failed.** Races were repeated 4 to 12 rounds each.

| Block | Checks |
| --- | --- |
| A. Setup | 2 |
| B. Structure | 88 |
| C. Privileges | 31 |
| D. Concurrency | 28 |
| Total | 149 |

How the count grew:

| Date | Checks | What was added |
| --- | --- | --- |
| 2026-10-14 | 62 | The first full run: structure, privileges, and the order and inventory races (v1.2) |
| 2026-10-22 | 66 | The new `users` columns and `email_verification_codes` (v1.4) |
| 2026-10-23 | 94 | Manual stock types and their reason rule, `ticket_inventory`, the ticket statuses, follow-up links, and the 14-table count (v1.5) |
| 2026-10-23 | 97 | `users.is_system`, no `priority` column, and the three ticket indexes (v1.6) |
| 2026-10-23 | 103 | `tickets.source`, and the order required on an `ORDER_EXCEPTION` ticket (v1.6) |
| 2026-10-23 | 107 | The list indexes and the three exception path races (v1.6) |
| 2026-10-24 | 149 | v1.7: the warehouse-first schema, products, listing and delisting, adding and deleting items, tickets on items, and orders that complete at once and are final. The confirmation, timeout, expired-confirmation, cancellation, and exception path tests (10 checks) were removed, because those flows no longer exist, and 52 checks were added, among them the 8-character ticket number and the follow-up by number. See the iteration file |

Two bugs of the tests themselves were found and fixed by the first runs of v1.7, and one design finding. A test that expected a lower-case SKU to be a duplicate ran into the new format check. The retry of a session ignored a `RETRYABLE` result that carried a suffix. And an order that copied the SKU with `INSERT ... SELECT` from `inventory` deadlocked with an order listing the same items in the opposite order, in 10 of 12 rounds: the shared locks it took on the stock rows had to be upgraded to exclusive locks. The design now says that the SKU is read with a plain read.

## Block A. Setup (2 checks)

| Check | What it shows |
| --- | --- |
| `migration V1 applies cleanly` | The migration runs without error on an empty MySQL 8.4 database |
| `verification procedures load` | The stored procedures of the fixtures compile |

## Block B. Structure (88 checks)

These are single-session checks of what the database enforces by itself. Design rules: Field Specifications, Initial Constraints, and the sections on products, orders, and tickets in the design document.

| Area | Checks | What they show |
| --- | --- | --- |
| Tables and append-only shape | `16 tables exist`; `ticket_comments has no updated_at (append-only)`; `order_items has no updated_at (frozen)`; `inventory has no locked_quantity column and orders has no status, reservation, or cancellation columns (an order is final)`; `inventory has no version column` | The table count, that append-only and frozen tables carry no update timestamp, that the reservation, status, and cancellation columns are gone, and that the stock row has no version counter (pessimistic locking only) |
| Case sensitivity of identifiers | `order_no, ticket_no, idempotency_key use utf8mb4_0900_bin`; `order_no is case-sensitive`; `identical order_no is still rejected`; `idempotency keys Abc and abc are different for one user`; `the same key for the same user is rejected`; `two different ticket numbers of 8 characters are accepted`; `a ticket number that is not 8 characters is rejected`; `a ticket number with lower-case letters is rejected`; `usernames keep the case-insensitive rule` | Generated numbers and keys compare as binary strings, while usernames stay case-insensitive |
| Comment corrections | `a correction comment can point at a comment of the same ticket`; `a comment can be corrected more than once`; `a correction pointing at another ticket comment is rejected by the database`; `a correction pointing at a missing comment is rejected`; `a corrected comment cannot be deleted while a correction points at it` | The composite foreign key on `ticket_comments` |
| Users | `users.must_change_password defaults to FALSE`; `users.failed_login_count defaults to 0`; `users.is_system defaults to FALSE`; `email_verification_codes rejects an unknown purpose` | The defaults and the allowed values of the identity and login columns |
| Ticket shape and indexes | `tickets has no priority column`; `tickets has the indexes for the open count, the order lookup, the archive job, and the system-first lists`; `orders and operation_logs have their list indexes` | The priority is gone and the indexes of the ticket lists, the counts, the archive job, and the order and log lists exist |
| Warehouse items | `the warehouse and product indexes for the catalog and the product page exist, and the unused date index is gone`; `two warehouse items cannot share a SKU`; `a warehouse item needs a real user as its creator`; `a SKU that is not 8 characters is rejected`; `a SKU with lower-case letters is rejected`; `two warehouse items cannot share a name, whatever the case`; `the name of a deleted item can be used again`; `a deleted item must carry both the time and the user`; `an item that still shows stock can be marked deleted, because the stock no longer matters` | `uk_inventory_sku`, `chk_inventory_sku`, `uk_inventory_name_active` (the name is unique among the items that are not deleted, and free again after a deletion), `chk_inventory_deleted` (the pairing only), and the indexes of the catalog and the product page |
| Item events | `an item event records the item, the SKU, the name, the quantity, and the user`; `an item event with an unknown action is rejected`; `an item event with a negative quantity is rejected`; `a warehouse item with events cannot be deleted` | `inventory_item_events`: `chk_item_events_action`, `chk_item_events_quantity`, and the foreign key that restricts |
| Products | `an item can be listed as a product`; `an item has at most one product`; `a product needs an existing warehouse item`; `a negative price is rejected`; `a product status other than LISTED or UNLISTED is rejected`; `a LISTED product cannot carry delisting fields`; `an UNLISTED product must carry its delisting fields`; `a product can be delisted with a time and a user` | `uk_products_inventory`, `fk_products_inventory`, `chk_products_price`, `chk_products_status`, and `chk_products_delisted` |
| Listing events | `a listing event records the product, the item, the name, the price, and the user`; `a listing event with an unknown action is rejected`; `a product with listing events cannot be deleted` | `product_listing_events`: `chk_product_events_action` and the foreign key that restricts |
| Ticket source and order | `a ticket source other than SYSTEM or MANUAL is rejected`; `a ticket with source SYSTEM is accepted`; `a ticket without a source is rejected`; `an ORDER_EXCEPTION ticket without an order is rejected`; `an ORDER_EXCEPTION ticket that names its order is accepted`; `only an ORDER_EXCEPTION ticket needs an order` | `chk_tickets_source` and `chk_tickets_order_required` |
| Ticket inventory links | `one ticket can cover several inventory records`; `the same inventory record cannot be linked to a ticket twice`; `a link to a missing inventory record is rejected`; `a ticket with linked inventory records cannot be deleted` | `ticket_inventory`: many links per ticket, unique per pair, foreign keys that restrict |
| Ticket statuses and follow-ups | `a ticket can be CANCELLED with a reason, a canceller, and a time`; `there is no REOPENED status`; `a ticket can be CLOSED with a closer and a time`; `a ticket can follow an earlier ticket`; `following a ticket that does not exist is rejected`; `a ticket that has a follow-up cannot be deleted`; `closed_by must be an existing user`; `cancelled_by must be an existing user` | The five statuses, the absence of a reopen status, and `follows_ticket_id` with its foreign keys |
| Manual stock types | `a WRITE_OFF with a reason and an operator is accepted`; `a WRITE_OFF that raises stock is rejected`; and for each of `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`: without a reason, with a blank reason, and without an operator are rejected (12 checks) | `chk_inventory_tx_manual`, the movement check, and `operator_id NOT NULL` (a missing operator is refused with error 1048) |
| Sales | `RETURN is not a transaction type (an order is never cancelled)`; `a SALE without an order is rejected`; `a SALE that raises stock is rejected`; `a SALE with its order and no reason is accepted`; `the same order cannot be sold twice from one item`; `a manual stock type cannot name an order` | `chk_inventory_tx_type`, `chk_inventory_tx_order_required`, the movement check, `chk_inventory_tx_manual` (a sale needs no reason), and `uk_inventory_tx_order_inventory_type` |

The status transitions, the fields each transition must fill, and the ticket limits are enforced in the service layer, so this block does not test them. See What is not tested yet.

## Block C. Privileges (31 checks)

The application account must be able to do what the flows need and nothing else. Design rule: Initial Constraints, and [`restrict-app-privileges.sql`](../admin/restrict-app-privileges.sql).

| Check | What it shows |
| --- | --- |
| `privilege script adapts to the temporary names without touching real accounts`; `privilege script runs` | The privilege script is applied to the temporary names only |
| `app: update mutable tables (users, roles, products, inventory, orders, tickets, email_verification_codes), including the soft delete of an item`; `app: insert into every table it writes`; `app: SELECT ... FOR UPDATE on inventory` | The grants are enough for the flows, including the soft delete of an item and the row lock |
| `app is refused: UPDATE` or `DELETE` on `inventory_transactions`, `ticket_assignment_history`, `ticket_comments`, `ticket_inventory`, `product_listing_events`, `inventory_item_events`, `operation_logs`, `order_items` (frozen), `user_roles` | Append-only and frozen tables accept `SELECT` and `INSERT` only (18 checks) |
| `app is refused: DELETE` on `orders`, `users`, `products`, `inventory`, `email_verification_codes` | Nothing that is soft-deleted or kept for good can be deleted (5 checks) |
| `app is refused: CREATE TABLE`; `app is refused: DROP TABLE` | The application cannot change the schema |
| `app can delete user_roles once DELETE is granted on that table only` | The later grant for role revocation works and stays limited to one table |

## Block D. Concurrency (28 checks)

Each test names its scenario, how it is run, what must hold, and the design rule it supports. The rules are in Order and Inventory Flow and in Concurrency by Module in the design document. In the numbers below, `Rounds` is 12 by default.

### D1. Repeated submission (2 checks)

- **`repeated submission: one order, stock deducted once`.** 12 rounds. Two sessions of the same user submit the same idempotency key at once.
- **Must hold:** one order, stock deducted once (`8,2`), one `SALE` row, and the results are `COMPLETED` and `DUPLICATE`.
- **`the same key from two different users creates two orders`.** Once. The idempotency key is unique per user, not globally.
- **Supports:** `UK(user_id, idempotency_key)` and the duplicate handling.

### D2. A multi-item order with one item short (2 checks)

- **`one item short: whole order rolled back, no order, no stock change, no transaction`.** Once. One of three items has too little stock.
- **Must hold:** the result is `OUT_OF_STOCK`, and there is no order, no item, no stock change, and no inventory transaction.
- **`five buyers race for the last unit: one wins, four see out of stock, nothing half-done`.** 12 rounds, five sessions, orders of two items where one product has a single unit left.
- **Must hold:** one `COMPLETED`, four `OUT_OF_STOCK`, one order, and stock exactly as one buyer would leave it.
- **Supports:** the all-or-nothing transaction and the check of every item under lock.

### D3. Many sales on one stock row (2 checks)

- **`ten buyers, stock for six: no oversell, snapshot chain unbroken and ends at the real stock`.** `Rounds / 2` rounds (6), ten sessions, stock of 20, three units each.
- **Must hold:** exactly six orders are completed, stock ends at `2,18`, there are six `SALE` rows, and the before and after snapshots of the inventory transactions form an unbroken chain from 20 to 2.
- **`negative control: reading stock without a lock DOES break the snapshot chain`.** Six sessions run a deliberately wrong procedure that reads the stock without a lock. The chain must break. This shows that the chain check can fail, so the passing run above means something.
- **Supports:** pessimistic row locking with `SELECT ... FOR UPDATE`, and exact snapshots.

### D4. Listing, delisting, and ordering under a product (6 checks)

The rounds are `Rounds / 3` (4) where a race is repeated.

- **`two managers list the same item at once: one product, one listing event`.** Two sessions run `list_product` for the same item. **Must hold:** the results are `LISTED` and `ALREADY_LISTED`, and there is one product and one `LISTED` event.
- **`listing again after a delisting keeps the product id, takes the new price and name, and records every step`.** In sequence: list, delist, list again with a new price and name, list again, and list a missing item. **Must hold:** the results are `LISTED`, `DELISTED`, `LISTED`, `ALREADY_LISTED`, and `NOT_FOUND`; the product id is the same; the product has the new values; and the events read `LISTED,DELISTED,LISTED`.
- **`an order is refused when the price changed, the product is delisted, or the quantity is not valid, and it writes nothing`.** In sequence. **Must hold:** a stale price gives `PRODUCT_CHANGED`, a matching price and name gives `COMPLETED`, a quantity of 0 or 10000 gives `INVALID_QUANTITY`, and an order after the delisting gives `PRODUCT_DELISTED`. Only the one good order exists, and only its stock is deducted.
- **`five orders racing a delisting: each is completed or refused, and none is created after the delisting`.** Five order sessions and one delisting at once. **Must hold:** the delisting succeeds, every order is `COMPLETED` or `PRODUCT_DELISTED`, the sold quantity equals the number of orders completed, and no order was created after `delisted_at`. This is the effect of the shared lock on the product row.
- **`delisting cancels the open and in-progress tickets of the product and leaves the others alone`.** Four tickets: open, in progress, and resolved, all linked to the product's item, and an open one linked to another item. **Must hold:** the first two are cancelled with the reason `PRODUCT_DELISTED:1`, the SYSTEM user as canceller, and a time; the other two are untouched.
- **`two delistings at once: one wins, one delisting event, each ticket cancelled once`.** **Must hold:** the results are `DELISTED|2` and `ALREADY_DELISTED|0`, there is one `DELISTED` event, and two tickets are cancelled.
- **Supports:** the unique key and the status gate on products, and the shared lock that orders take on product rows.

### D5. Adding and deleting warehouse items, and opening a ticket on items (9 checks)

The procedures are `create_item`, `delete_item`, and `create_ticket`. A race is repeated `Rounds / 3` (4) times.

- **`adding an item: the starting stock is recorded as a restock, a repeated name or SKU and a bad quantity are refused, and each addition has an event`.** In sequence. **Must hold:** an item with 25 and one with 0 are `CREATED`; a name that differs only in case gives `NAME_EXISTS`; a repeated SKU gives `SKU_EXISTS`; a quantity of -1 or 1000000 gives `INVALID_QUANTITY`; only the first item has a `RESTOCK` with the reason `initial stock` (0 to 25); and the events read `CREATED:25,CREATED:0`.
- **`two managers add an item with the same name at once: one item, one event, one starting restock`.** Two sessions with different SKUs. **Must hold:** one is `CREATED` and one is `NAME_EXISTS`, and there is one item, one event, and one restock.
- **`deleting an item delists its product, cancels the open tickets linked to it, leaves its stock alone, records the event, and frees its name`.** In sequence, with the four tickets of the delisting test. **Must hold:** the first deletion is `DELETED|2`; a second is `ALREADY_DELETED`; a missing item is `NOT_FOUND`; listing the deleted item is `NOT_FOUND`; the item still shows 10 and is marked deleted; its product is `UNLISTED` with one `DELISTED` event; the open and in-progress tickets are cancelled with `ITEM_DELETED:3`, and the resolved and other-item tickets are untouched; the event is `DELETED:10`; no stock transaction was written; and the name can be used for a new item.
- **`two deletions of the same item at once: one wins, one event, each ticket cancelled once`.** **Must hold:** `DELETED|2` and `ALREADY_DELETED|0`, one event, no stock transaction, two tickets cancelled, and one `DELISTED` event.
- **`an order racing the deletion of its item: either the order is completed and then the item is deleted, or the item is deleted and the order is refused`.** **Must hold:** either `COMPLETED` and `DELETED`, with one order and one unit sold, or `PRODUCT_DELISTED` and `DELETED`, with no order and the stock untouched. A deleted item never has an order placed after it. This rests on the shared lock on the product row.
- **`a ticket opened on an item racing its deletion: either the ticket is opened and then cancelled, or it is refused, and no open ticket stays linked to a deleted item`.** **Must hold:** either `CREATED` and `DELETED|1`, with the ticket cancelled by `ITEM_DELETED:3`, or `ITEM_GONE` and `DELETED|0`, with no ticket and no link. This rests on the shared lock on the warehouse row in the ticket creation, and on the deletion cancelling the tickets after it holds the warehouse row.
- **`a ticket can be opened on no items or on several, and one deleted item refuses the whole ticket with nothing written`.** In sequence. **Must hold:** a ticket on `[]` and a ticket on two live items are `CREATED` (two links in all); a ticket on a live and a deleted item gives `ITEM_GONE`, and neither the ticket nor a link remains.
- **`a ticket can be opened as a follow-up by the number of a RESOLVED or CLOSED ticket; an unknown number or an unfinished or cancelled ticket is refused with nothing written`.** In sequence, with a resolved, a closed, an open, and a cancelled ticket. **Must hold:** following the resolved or the closed ticket is `CREATED` and stores `follows_ticket_id`; following the open or the cancelled ticket gives `FOLLOW_NOT_ALLOWED`; a number that matches no ticket gives `FOLLOW_NOT_FOUND`; the refused requests write no ticket and no link.
- **`listing an item racing its deletion: the item is never left deleted with a LISTED product`.** On an item with no product. **Must hold:** either `LISTED` and `DELETED` with the product `UNLISTED`, or `NOT_FOUND` and `DELETED` with no product. If the listing slips in between the first read of the deletion and its lock, the deletion rolls back and is retried.
- **Supports:** `UNIQUE (name, is_active)`, the lock order `products`, then `inventory`, then the tickets it cancels, and the shared lock on the warehouse row when a ticket is created.

### D6. Deadlocks and lock waits (7 checks)

- **`locking rows in opposite orders: MySQL aborts exactly one session with a deadlock`.** Once. Two sessions lock inventory rows 1 and 2 in opposite orders, each after inserting a log row and sleeping 1.5 seconds. Exactly one is aborted with error 1213 and the other commits.
- **`the aborted session left nothing behind (its earlier insert was rolled back too)`.** The aborted session's log row is gone, so a deadlock rolls back the whole transaction.
- **`the aborted session succeeds when it is retried`.** Run again in a new transaction, it commits.
- **`orders listing items in opposite order never deadlock (locks are taken by ascending inventory id)`.** 12 rounds. Two orders of the same three products, listed in opposite orders, run together with plenty of stock.
- **Must hold:** every order is completed, and no retry was needed. This check found that copying the SKU with `INSERT ... SELECT` from `inventory` takes shared locks that deadlock on the upgrade (10 of 12 rounds needed a retry). The fixture and the design now read the SKU with a plain read.
- **`a lock wait timeout (error 1205) happens`.** One session holds a row lock for 7 seconds, and a waiter with a 2-second wait timeout runs into it.
- **`after a lock wait timeout the earlier statements of the transaction are still pending (only the timed-out statement rolled back)`.** The waiter's earlier insert is still visible after the timeout.
- **`the application must roll back explicitly, which removes them`.** After an explicit `ROLLBACK` the insert is gone.
- **Supports:** the ascending lock order, the plain read of the SKU, and the rule that the application rolls back explicitly before every retry (Concurrency and duplicate protection).

## What is not tested yet

These rules are designed but have no race test, because the flows are not written as procedures and the application code does not exist. Each is a task for when its module is built (T-17, T-34).

| Module | Races in Concurrency by Module without a test |
| --- | --- |
| Inventory | A manual stock change (stock in, write-off, direct set with the value the user saw) racing an order on the same stock (T-30), or racing the deletion of the item |
| Tickets | Two people taking the same ticket from the pool; a reassignment racing a release, a cancel, or a resolve; the open count and the in-progress count at 98; a user disabled while their tickets are released or resolved; a comment arriving as a ticket is archived or cancelled; the archive job racing a manager's archive, and the count of 99 |
| Users and roles | Grant, revoke, replace, or disable at once; the last administrator removed twice; a role granted while it is deleted |
| Authentication | Two wrong passwords at once; an email code submitted twice; two sign-ups with the same username |
| Products and warehouse items | A warehouse item edited while it is being listed. A SKU collision is left to the unique key |

The ticket status transitions, the fields each must fill, the ticket limits, and the SYSTEM identity rules are service-layer rules (T-33, T-35, T-36). They need tests in the application code.

## How to add or change a test

1. Write the check in `run-db-verification.ps1` with a clear name. Put a new flow in `concurrency-fixtures.sql` as a stored procedure, and keep it in step with the flow in the design document.
2. Record it in this file in the right block: the check name exactly as the script prints it, the scenario, how it is run (sessions and rounds), what must hold, and the design rule it supports. Update the counts in Latest run, and add a row to How the count grew.
3. Run the script, and record the result in the iteration file and in the Concurrency and privilege verification section of the design document.
4. When a test changes or is removed, change its entry here and say why in the iteration file. A test that is removed without a reason is a rule that is no longer checked.
5. When a race in the table above gets a test, move it out of What is not tested yet into its block.
