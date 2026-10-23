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
- A session that fails with a deadlock or a lock wait timeout is run again up to two more times, the way the application would, unless the test says it must not be retried.
- `-Rounds` sets how many times each race is repeated. The default is 12. Some races use a third or a half of it, as noted below. More rounds make a rare interleaving more likely to show up. A passing run shows that the design holds under the interleavings it produced, not that every possible interleaving was tried.
- To run it: `powershell -STA -File db/verification/run-db-verification.ps1`.

## Latest run

**2026-10-23, MySQL 8.4: 107 passed, 0 failed.**

| Block | Checks |
| --- | --- |
| A. Setup | 2 |
| B. Structure | 55 |
| C. Privileges | 27 |
| D. Concurrency | 23 |
| Total | 107 |

How the count grew:

| Date | Checks | What was added |
| --- | --- | --- |
| 2026-10-14 | 62 | The first full run: structure, privileges, and the order and inventory races (v1.2) |
| 2026-10-22 | 66 | The new `users` columns and `email_verification_codes` (v1.4) |
| 2026-10-23 | 94 | Manual stock types and their reason rule, `ticket_inventory`, the ticket statuses, follow-up links, and the 14-table count (v1.5) |
| 2026-10-23 | 97 | `users.is_system`, no `priority` column, and the three ticket indexes (v1.6) |
| 2026-10-23 | 103 | `tickets.source`, and the order required on an `ORDER_EXCEPTION` ticket (v1.6) |
| 2026-10-23 | 107 | The list indexes and the three exception path races (v1.6) |

## Block A. Setup (2 checks)

| Check | What it shows |
| --- | --- |
| `migration V1 applies cleanly` | The migration runs without error on an empty MySQL 8.4 database |
| `verification procedures load` | The stored procedures of the fixtures compile |

## Block B. Structure (55 checks)

These are single-session checks of what the database enforces by itself. Design rules: Field Specifications, Initial Constraints, and Tickets in the design document.

| Area | Checks | What they show |
| --- | --- | --- |
| Tables and append-only shape | `14 tables exist`; `ticket_comments has no updated_at (append-only)`; `order_items has no updated_at (frozen)`; `inventory has no version column` | The table count, and that append-only and frozen tables carry no update timestamp and the stock row has no version counter (pessimistic locking only) |
| Case sensitivity of identifiers | `order_no, ticket_no, idempotency_key use utf8mb4_0900_bin`; `order_no is case-sensitive`; `identical order_no is still rejected`; `idempotency keys Abc and abc are different for one user`; `the same key for the same user is rejected`; `ticket_no is case-sensitive`; `usernames keep the case-insensitive rule` | Generated numbers and keys compare as binary strings, while usernames stay case-insensitive |
| Comment corrections | `a correction comment can point at a comment of the same ticket`; `a comment can be corrected more than once`; `a correction pointing at another ticket comment is rejected by the database`; `a correction pointing at a missing comment is rejected`; `a corrected comment cannot be deleted while a correction points at it` | The composite foreign key on `ticket_comments` |
| Users | `users.must_change_password defaults to FALSE`; `users.failed_login_count defaults to 0`; `users.is_system defaults to FALSE`; `email_verification_codes rejects an unknown purpose` | The defaults and the allowed values of the identity and login columns |
| Ticket shape and indexes | `tickets has no priority column`; `tickets has the indexes for the open count, the order lookup, the archive job, and the system-first lists`; `orders and operation_logs have their list indexes` | The priority is gone and the indexes of the ticket lists, the counts, the archive job, and the order and log lists exist |
| Ticket source and order | `a ticket source other than SYSTEM or MANUAL is rejected`; `a ticket with source SYSTEM is accepted`; `a ticket without a source is rejected`; `an ORDER_EXCEPTION ticket without an order is rejected`; `an ORDER_EXCEPTION ticket that names its order is accepted`; `only an ORDER_EXCEPTION ticket needs an order` | `chk_tickets_source` and `chk_tickets_order_required` |
| Ticket inventory links | `one ticket can cover several inventory records`; `the same inventory record cannot be linked to a ticket twice`; `a link to a missing inventory record is rejected`; `a ticket with linked inventory records cannot be deleted` | `ticket_inventory`: many links per ticket, unique per pair, foreign keys that restrict |
| Ticket statuses and follow-ups | `a ticket can be CANCELLED with a reason, a canceller, and a time`; `there is no REOPENED status`; `a ticket can be CLOSED with a closer and a time`; `a ticket can follow an earlier ticket`; `following a ticket that does not exist is rejected`; `a ticket that has a follow-up cannot be deleted`; `closed_by must be an existing user`; `cancelled_by must be an existing user` | The five statuses, the absence of a reopen status, and `follows_ticket_id` with its foreign keys |
| Manual stock types | `a WRITE_OFF with a reason and an operator is accepted`; `a WRITE_OFF that raises stock is rejected`; and for each of `RESTOCK`, `WRITE_OFF`, `ADJUST_IN`, and `ADJUST_OUT`: without a reason, with a blank reason, and without an operator are rejected (12 checks) | `chk_inventory_tx_manual` and the movement check |

The status transitions, the fields each transition must fill, and the ticket limits are enforced in the service layer, so this block does not test them. See What is not tested yet.

## Block C. Privileges (27 checks)

The application account must be able to do what the flows need and nothing else. Design rule: Initial Constraints, and [`restrict-app-privileges.sql`](../admin/restrict-app-privileges.sql).

| Check | What it shows |
| --- | --- |
| `privilege script adapts to the temporary names without touching real accounts`; `privilege script runs` | The privilege script is applied to the temporary names only |
| `app: update mutable tables (...)`; `app: insert into every table it writes`; `app: SELECT ... FOR UPDATE on inventory` | The grants are enough for the flows, including the row lock |
| `app is refused: UPDATE` or `DELETE` on `inventory_transactions`, `ticket_assignment_history`, `ticket_comments`, `ticket_inventory`, `operation_logs`, `order_items` (frozen), `user_roles` | Append-only and frozen tables accept `SELECT` and `INSERT` only (14 checks) |
| `app is refused: DELETE` on `orders`, `users`, `products`, `inventory`, `email_verification_codes` | Nothing that is soft-deleted or kept for good can be deleted (5 checks) |
| `app is refused: CREATE TABLE`; `app is refused: DROP TABLE` | The application cannot change the schema |
| `app can delete user_roles once DELETE is granted on that table only` | The later grant for role revocation works and stays limited to one table |

## Block D. Concurrency (23 checks)

Each test names its scenario, how it is run, what must hold, and the design rule it supports. The rules are in Order and Inventory Flow and in Concurrency by Module in the design document. In the numbers below, `Rounds` is 12 by default.

### D1. Confirm and cancel at the same time (1 check)

- **`confirm racing cancel: exactly one wins, stock and transactions always consistent`.** 12 rounds. Two sessions run `confirm_order` and `cancel_order` on the same order of two items.
- **Must hold:** exactly one wins. The loser sees `ALREADY_*`. If the confirm wins, stock is `7,0,3` and `8,0,2` with two `CONFIRM` rows and no `RELEASE`. If the cancel wins, stock is back to `10,0,0` and `10,0,0` with two `RELEASE` rows and no `CONFIRM`.
- **Supports:** the status gate. Only one transaction can move an order out of `CREATED`.

### D2. Repeated submission (2 checks)

- **`repeated submission: one order, stock reserved once`.** 12 rounds. Two sessions of the same user submit the same idempotency key at once.
- **Must hold:** one order, stock reserved once (`8,2`), one `RESERVE` row, and the results are `CREATED` and `DUPLICATE`.
- **`the same key from two different users creates two orders`.** Once. The idempotency key is unique per user, not globally.
- **Supports:** `UK(user_id, idempotency_key)` and the duplicate handling.

### D3. A multi-item order with one item short (2 checks)

- **`one item short: whole order rolled back, no order, no stock change, no transaction`.** Once. One of three items has too little stock.
- **Must hold:** the result is `OUT_OF_STOCK`, and there is no order, no item, no stock change, and no inventory transaction.
- **`five buyers race for the last unit: one wins, four see out of stock, nothing half-done`.** 12 rounds, five sessions, orders of two items where one product has a single unit left.
- **Must hold:** one `CREATED`, four `OUT_OF_STOCK`, one order, and stock exactly as one buyer would leave it.
- **Supports:** the all-or-nothing transaction and the check of every item under lock.

### D4. Many reservations on one stock row (2 checks)

- **`ten buyers, stock for six: no oversell, snapshot chain unbroken and ends at the real stock`.** `Rounds / 2` rounds (6), ten sessions, stock of 20, three units each.
- **Must hold:** exactly six orders are created, stock ends at `2,18`, there are six `RESERVE` rows, and the before and after snapshots of the inventory transactions form an unbroken chain from 20 to 2.
- **`negative control: reading stock without a lock DOES break the snapshot chain`.** Six sessions run a deliberately wrong procedure that reads the stock without a lock. The chain must break. This shows that the chain check can fail, so the passing run above means something.
- **Supports:** pessimistic row locking with `SELECT ... FOR UPDATE`, and exact snapshots.

### D5. Timeout, expired confirmation, and duplicate actions (6 checks)

- **`two timeout jobs at once: each expired order cancelled and released exactly once, live order untouched`.** `Rounds / 3` rounds (4). Two timeout jobs run together over three expired orders and one live one.
- **Must hold:** the expired orders are cancelled once each and released once each, the live order stays `CREATED`, and the stock ends at `9,1`.
- **`confirming an expired order cancels it and releases the stock at once`.** Once, in sequence. The first confirmation returns `EXPIRED_CANCELLED` and the stock is released. The second returns `ALREADY_CANCELLED`.
- **`confirming an unknown order reports not found`.** Once.
- **`two confirmations at once: one completes, the other sees it done, stock deducted once`.** Once. The results are `COMPLETED` and `ALREADY_COMPLETED`, and stock is `7,0,3` with one `CONFIRM` set.
- **`cancelling a completed order is refused`.** Once. The result is `ALREADY_COMPLETED`.
- **`two cancellations at once: stock released once`.** 4 rounds. The results are `CANCELLED` and `ALREADY_CANCELLED`, and stock is back to `10,0` with one `RELEASE` set.
- **Supports:** the status gate on the timeout job, the expired confirmation rule, and the rule that a repeated request never moves stock twice.

### D6. The exception path (3 checks)

This is the flow that stops an order and opens a ticket when an order goes wrong. Design rule: Exception path. The fixture is the procedure `open_exception_ticket`.

- **`two exception paths for one order at once: one ticket, one cancellation, stock released once`.** 4 rounds. Two sessions open an exception ticket for the same order.
- **Must hold:** the results are `OPENED_RELEASED` and `ALREADY_HANDLED`, there is one `SYSTEM` ticket for the order, the order is `CANCELLED` with `EXCEPTION_TICKET:` and a ticket number in its reason, stock is back to `10,0`, and there is one set of `RELEASE` rows. One order never gets two tickets.
- **`when the records disagree the order is stopped, a ticket is opened, and the stock is left as found`.** Once. The release is skipped.
- **Must hold:** the result is `OPENED_STOCK_UNTOUCHED`, stock stays `6,4`, there are no `RELEASE` rows, the ticket is an `INVENTORY_EXCEPTION`, and the order is `CANCELLED`.
- **`a confirmation racing the exception path: exactly one wins, and a ticket exists only if the order was stopped`.** 4 rounds. A confirmation and the exception path run together.
- **Must hold:** either the confirmation completes (stock `6,0,4`, no ticket) and the exception path reports `ALREADY_HANDLED`, or the exception path stops the order (stock `10,0,0`, one ticket) and the confirmation reports `ALREADY_CANCELLED`.
- **Supports:** the status gate as the first statement of the path, and the lock order (the order row, then a new ticket row, then inventory).

### D7. Deadlocks and lock waits (7 checks)

- **`locking rows in opposite orders: MySQL aborts exactly one session with a deadlock`.** Once. Two sessions lock inventory rows 1 and 2 in opposite orders, each after inserting a log row and sleeping 1.5 seconds. Exactly one is aborted with error 1213 and the other commits.
- **`the aborted session left nothing behind (its earlier insert was rolled back too)`.** The aborted session's log row is gone, so a deadlock rolls back the whole transaction.
- **`the aborted session succeeds when it is retried`.** Run again in a new transaction, it commits.
- **`orders listing items in opposite order never deadlock (locks are taken by ascending inventory id)`.** 12 rounds. Two orders of the same three products, listed in opposite orders, run together with plenty of stock.
- **Must hold:** every order is created, and no retry was needed.
- **`a lock wait timeout (error 1205) happens`.** One session holds a row lock for 7 seconds, and a waiter with a 2-second wait timeout runs into it.
- **`after a lock wait timeout the earlier statements of the transaction are still pending (only the timed-out statement rolled back)`.** The waiter's earlier insert is still visible after the timeout.
- **`the application must roll back explicitly, which removes them`.** After an explicit `ROLLBACK` the insert is gone.
- **Supports:** the ascending lock order, and the rule that the application rolls back explicitly before every retry (Concurrency and duplicate protection).

## What is not tested yet

These rules are designed but have no race test, because the flows are not written as procedures and the application code does not exist. Each is a task for when its module is built (T-17, T-34). The exception path was added as a procedure, so the same approach can be used before the backend exists.

| Module | Races in Concurrency by Module without a test |
| --- | --- |
| Inventory | A manual stock change (stock in, write-off, direct set with the value the user saw) racing an order on the same stock (T-30) |
| Tickets | Two people taking the same ticket from the pool; a reassignment racing a release, a cancel, or a resolve; the open count and the in-progress count at 98; a user disabled while their tickets are released or resolved; a comment arriving as a ticket is archived or cancelled; the archive job racing a manager's archive, and the count of 99 |
| Users and roles | Grant, revoke, replace, or disable at once; the last administrator removed twice; a role granted while it is deleted |
| Authentication | Two wrong passwords at once; an email code submitted twice; two sign-ups with the same username |
| Products | Two managers editing the same product; a price change racing an order |

The ticket status transitions, the fields each must fill, the ticket limits, and the SYSTEM identity rules are service-layer rules (T-33, T-35, T-36). They need tests in the application code.

## How to add or change a test

1. Write the check in `run-db-verification.ps1` with a clear name. Put a new flow in `concurrency-fixtures.sql` as a stored procedure, and keep it in step with the flow in the design document.
2. Record it in this file in the right block: the check name exactly as the script prints it, the scenario, how it is run (sessions and rounds), what must hold, and the design rule it supports. Update the counts in Latest run, and add a row to How the count grew.
3. Run the script, and record the result in the iteration file and in the Concurrency and privilege verification section of the design document.
4. When a test changes or is removed, change its entry here and say why in the iteration file. A test that is removed without a reason is a rule that is no longer checked.
5. When a race in the table above gets a test, move it out of What is not tested yet into its block.
