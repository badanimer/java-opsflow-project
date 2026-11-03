# Database Iteration v1.9

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.9 |
| Date | 2026-10-27 |
| Baseline | v1.8, see [database-v1.8.md](database-v1.8.md) |
| Type | Design rules, one privilege change, and one column (`order_attempts.product_name`). The migration was edited in place, because it has not been applied anywhere |
| Table count | 16 (unchanged) |
| Trigger | The owner's answers before the database is created: the open questions Q-01, Q-03, Q-17, Q-30, and Q-35 of [project-todo.md](../project-todo.md) |

## Summary

- **Listing needs stock.** An item with no stock cannot be listed. The check reads `available_quantity` under the shared lock the listing already takes on the `inventory` row, and refuses with `NO_STOCK`. It applies at the moment of listing only.
- **A deleted item still refuses manual stock changes.** The rule stays, and the reason is now stated: for the person the item is gone from the warehouse, and adding it again means creating a new item and counting its stock by hand. The row is kept only because history refers to it. Delisting a product is a different thing and never touches stock.
- **Retention.** Every record is kept for ever, except expired email codes, which a job deletes 24 hours after `expires_at`, once an hour. The application account gets `DELETE` on `email_verification_codes` only.
- **The missing log formats.** Stock logs, order records, user and account logs, and the archive rows are defined (see Changes). Q-03 is closed by this.
- **A deleted item is offered nowhere.** The owner does not mind how it is stored. What counts is that a deleted item cannot be seen and cannot be chosen by name in a log filter or a picker, so the backend search carries no option the screen cannot show. Its records stay in the database and in the lists of all records. The product of a deleted item is not offered by the product picker either. To find a deleted item anyway, the warehouse log gets a read-only module, Deleted items, with its own search by name or SKU and the own log of the chosen item (the owner's request). It needs no new table, column, or index.
- **An attempt keeps the product name.** `order_attempts.product_name` holds the name at the time of the attempt, so a refused attempt stays readable after a product is listed under another name. The price is not kept (Q-35 is closed).
- **No password change for a logged-in user.** There is no user page, so the only way to change a password is Forgot password on the login page. `PASSWORD_CHANGED` was removed from the log formats and the change-password screen from T-50.
- **The audit page is a menu of seven areas, and every audit row carries a category.** Accounts, Roles and permissions, Tickets, Stock, Warehouse items, Products, and Orders. The first six read `operation_logs` through the new column `category` (six allowed values, set by the application from one table of actions); Orders reads `order_attempts`, which stays the only record of an order and is also shown in the product log, so a role without the audit page (a `VIEWER`) still finds it. The owner decided: tag every record, no merging of areas, orders in both places, system actions shown as System.
- **The SYSTEM identity stays as designed.** It is a real row in `users` with no role and no login, used where a column needs a user. System actions have an empty `operator_id` in the audit log.
- **Every audit row carries a label.** A ticket row has its `ticketNo`, an account row its `username`, a role row its `roleCode` and the `username` it is about, an item or stock row its `sku` and `name`, and a product row its `name` and the `sku` of its item. A row that names another user carries that user's `username`. The labels are copies taken at the time of the action, so a row still reads after an object is renamed or deleted.
- **The stock, item, and product records stay written twice** (the specific table and `operation_logs`), by the owner's decision: the specific table is the detail and `operation_logs` is the index of the audit page.
- **Search by order number.** The order numbers were shown but could not be searched. One box for one whole order number now finds the lines of an attempt (order records), the stock movements of the order, and the tickets that name it. No new table, column, or index: `(order_no, product_id)`, `orders.order_no` with `order_id`, and `idx_tickets_order_status` serve it.
- **The assignment history** is in the Tickets area of the audit page through the `TICKET_ASSIGNED`, `TICKET_REASSIGNED`, and `TICKET_RELEASED` rows, which already existed, and on the ticket detail from `ticket_assignment_history`. Nothing was added except the labels.
- **Q-29 is closed.** Its two parts were decided: the classification is the seven areas and the `category`, and the format of each record is in Auditing. The formats may still change in detail when the audit page is laid out (Q-34).
- **Password rule and token life.** A password is 8 to 24 characters with a letter and a digit; a new password may not equal the current one (compared with the hash, so no history table); weak passwords are not blocked. A token lasts 12 hours with no refresh. Q-18 and Q-19 are closed.
- **Q-01 is dropped.** `user_roles` keeps no history table, and nothing more is recorded about it.

## Changes

### Logs

The owner's requirements: a record says who, when, what, and how much; only an action that was carried out is recorded; a refused order attempt is recorded too, so that its number can be used in a ticket.

| Area | What was decided |
| --- | --- |
| Manual stock changes | One `operation_logs` row for each change, in the same transaction: `STOCK_RESTOCK`, `STOCK_WRITE_OFF`, or `STOCK_ADJUSTED`, with the quantity before and after, the quantity changed, the SKU and the name, the transaction id, and the reason in `remark`. |
| Orders | No `operation_logs` row. `order_attempts` already records, for every attempt, the user, the time, the product, the quantity, the result, the stock deducted, and the order number, a refused attempt included. A second record would only be able to disagree with it. |
| Users and accounts | `USER_REGISTERED` (sign-up and the console command), `USER_STATUS_CHANGED`, `USER_DELETED`, `PASSWORD_RESET`, and `LOGIN_LOCKED`, each with the actor, the target user, and the time. A refused action writes nothing. A successful login and a single wrong password are not logged. No password, code, or hash goes into a log. |
| Archiving | Each archived ticket has its own `TICKET_CLOSED` row with its ticket number, and the rows of one batch or one run of the job share a `batchId`. |
| Opening a ticket on an order | Part of `TICKET_CREATED` (`orderId`). A ticket that cannot be opened writes nothing. |

### Listing, deletion, and email codes

- `list_product` in the fixtures reads the stock under the shared lock, for the first listing and for a relisting, and returns `NO_STOCK`. The extra items of the listing races now carry 5 units.
- `restrict-app-privileges.sql` grants `DELETE` on `email_verification_codes`. [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) lists it.
- Image files are kept for ever like every other record, so the "cleanup job" for images that [database-v1.8.md](database-v1.8.md) left to Q-17 is dropped.

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Listing an item with no stock | Refused with `NO_STOCK`, at the moment of listing only | Allowed (v1.7); or also delisting a product whose stock reaches zero |
| Manual stock change on a deleted item | Still refused, the reason stated | Allowed, which would keep alive an item that every other rule treats as gone |
| Is the deleted item really removed from the database | No: the row is kept (soft delete), because orders, stock records, tickets, and events refer to it | A hard delete, which would need those foreign keys to be dropped or the history to be lost. Not done unless the owner asks |
| Retention | Keep everything; delete only expired email codes, 24 hours after `expires_at`, hourly | Keeping the codes for ever; or deleting at expiry |
| Order records | `order_attempts` is the record, with the product name added | A duplicate row in `operation_logs` |
| Price in the attempt | Not kept | Keeping the price the user saw, which no one asked for |
| Deleted items in the pickers | Not offered; found in the Deleted items module of the warehouse log | Offered and marked as deleted (v1.8) |
| Password rule | 8 to 24 characters, a letter and a digit, not equal to the current password | A password history, a weak-password list, or a refresh token |
| Password change | Forgot password on the login page only | A change-password screen for a logged-in user |
| Login logging | Only the lock is logged | Logging every login or every failure |
| Audit page areas | A `category` column on every audit row, set from one table in the code, and a menu of seven areas | Guessing the area from `target_type` and the action prefix; merging stock, items, and products into one area |
| Orders on the audit page | Read from `order_attempts`, and also in the product log | A second copy in `operation_logs` |
| Double writing of stock, item, and product records | Kept: the specific table for the detail, `operation_logs` for the audit page | Dropping the audit rows, which would need the audit page to read several tables |
| Labels | On every audit row, copied at the time of the action | A join at read time, which fails or shows a new name after a deletion or a rename |
| Order number lookup | One box for one whole number in the order records, the stock movements, and the ticket search | A search by part of a number, or a new index |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Listing and delisting, Manual stock changes, Email codes, Operating Parameters, Auditing (Stock logs, Order records, User and account logs, the archive row), the products race rows, the privilege rules, Future Evolution, and the Change Log |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql), [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: `DELETE` on `email_verification_codes` |
| [Verification script and fixtures](../../db/verification/run-db-verification.ps1) | Updated: the refusal of `DELETE` on `email_verification_codes` removed (its reason is the new grant); four checks added (the application can delete expired codes and keeps a valid one, an item with no stock cannot be listed, an attempt needs the name of the product, and a recorded attempt carries that name); the extra items of three checks carry stock; the inserts into `order_attempts` and the two attempt procedures carry the name. **Run on 2026-10-27: 156 passed, 0 failed** (A 2, B 91, C 31, D 32), after the audit categories were added (the index check changed, one check added for the category, and the inserts into `operation_logs` carry a category) |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the entries, the counts of blocks B and D, and the table of how the count grew. Run recorded: 156 passed, 0 failed |
| [page-design.md](../frontend/page-design.md) | A reference to Q-03 changed to Q-34; the pickers and the Deleted items module |
| [project-todo.md](../project-todo.md) | Updated: Q-01, Q-03, Q-17, Q-18, Q-19, Q-29, and Q-30 removed; T-20 and T-29 carry the password rule and the token life; T-46, T-47, and T-50 carry the order number box and the labels; T-39 and the new T-49 and T-50 carry the decisions, and Q-35 closed |
| [Initial migration](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) | Updated in place: `order_attempts.product_name VARCHAR(200) NOT NULL`, and `operation_logs.category VARCHAR(16) NOT NULL` with `chk_operation_logs_category` and the index `idx_operation_logs_category_created` replacing `idx_operation_logs_created` |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated on 2026-10-27 through the Figma connector: a `category` row (`varchar`, NOT NULL) added to `operation_logs`, after `operator_id`; and a `product_name` row (`varchar`, NOT NULL, the name of the product at the time of the attempt) added to the `order_attempts` table, after `product_id` |
| Simplified ERD, Business Process Flowchart | Not changed: no table or flow changed in the diagrams |

## Open Items

- Lay out the audit page and its rows (Q-34).
- Implement the new logs (T-50) and the email code cleanup (T-49).
