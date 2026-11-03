# OpsFlow

OpsFlow is a Java full-stack application for order, inventory, and operations-ticket management. It models a realistic back-office system with transactional consistency, role-based access control, exception handling, and auditability.

The repository currently contains the initial design, staged learning documents, and the initial database migration. Application code will be added as the implementation progresses.

## Design Documents

The current project design is documented separately from the learning guide:

- [Database Structure And Business Flow](docs/database/database-design.md)
- [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) - a concise overview for the README and project introduction.
- [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) - table fields, keys, and implementation-oriented relationships.
- [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) - the English order, inventory, audit, and exception-ticket flow.

The simplified ERD is intended for quick communication. The physical ERD and database document are the references for implementation.

## Documentation Structure

- [`docs/database`](docs/database/) contains the current database design baseline and will later hold schema decisions and migration notes.
- [`docs/backend`](docs/backend/) contains the current description of the backend: the stack, the layout of the project, the configuration, and how it is run. The code is in [`backend`](backend/).
- [`docs/frontend`](docs/frontend/) contains the page design: which pages exist, who sees them, and what each one shows. It is a first version, and the visual design is not started.
- [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration/) contains the executable database migrations, applied in version order. See [Initial Migration](docs/database/database-design.md#initial-migration).
- [`db/admin`](db/admin/) contains database administration scripts that are not migrations, such as the per-table privileges for the application account.
- [`db/verification`](db/verification/) contains the script that verifies the schema, the privileges, and the order and inventory concurrency rules in a temporary database. See [Concurrency and privilege verification](docs/database/database-design.md#concurrency-and-privilege-verification). The test log [`concurrency-tests.md`](db/verification/concurrency-tests.md) lists every test by block, with what it shows and what is not tested yet.
- [`docs/iterations`](docs/iterations/) contains one summary file per design iteration, indexed in the Project Log below.
- [`docs/project-todo.md`](docs/project-todo.md) lists the confirmed tasks and the undecided questions that are still open.
- [`docs/future-ideas.md`](docs/future-ideas.md) keeps rough ideas for features that can be added after the program is complete. An idea goes in only after the owner approves it.
- [`docs/documentation-sync-rules.md`](docs/documentation-sync-rules.md) defines which documents must be updated together and how iterations are recorded. Read it before changing any design document or diagram.
- [`opsflow_learning_docs_en`](opsflow_learning_docs_en/) contains the English learning path.
- [`汉化说明书`](汉化说明书/) contains the Chinese learning path.
- [`ENVIRONMENT_SETUP.md`](ENVIRONMENT_SETUP.md) records the local development environment setup.

## Project Log

Iteration notes live in [`docs/iterations`](docs/iterations/), one file per iteration. Entries are listed newest first. The process for recording iterations and keeping documents in sync is defined in [documentation-sync-rules.md](docs/documentation-sync-rules.md).

| Date | Version | Area | Summary | Details |
| --- | --- | --- | --- | --- |
| 2026-11-03 | 1.0 | Backend | The Spring Boot skeleton: a Maven project in `backend/`, Flyway and the MySQL driver, configuration from environment variables with the UTC parameters, and the migrations moved into the project | [backend-v1.0.md](docs/iterations/backend-v1.0.md) |
| 2026-11-03 | 1.10 | Database | Shortened the keeping of email codes: a code is deleted as soon as it expires (10 minutes after it was made), by a job that runs once a minute, instead of 24 hours after it expires. No table changed | [database-v1.10.md](docs/iterations/database-v1.10.md) |
| 2026-10-26 | 1.0 | Frontend | First page design: the page map, what each role sees, and a first description of every page, including the stock page with its log, the ticket screens, the user page, and the audit log page. The owner's rough ideas were turned into rules for lists, search, and paging that the database answers | [frontend-v1.0.md](docs/iterations/frontend-v1.0.md) |
| 2026-10-27 | 1.9 | Database | Decided the open database questions: an item with no stock cannot be listed (`NO_STOCK`); a deleted item still refuses manual stock changes, with the reason stated; every record is kept for ever except expired email codes, which a job deletes 24 hours after they expire (the application account gets `DELETE` on `email_verification_codes` only); and defined the log formats that were missing: stock logs, order records (`order_attempts` is the record), user and account logs (sign-up, status, soft delete, password reset, login lock), and the ticket number and batch id on archived tickets; added `order_attempts.product_name`, the name of the product at the time of an attempt; and made a deleted item unavailable in the pickers, gave deleted items their own module in the warehouse log, defined the labels every audit row carries, added the search by order number, and redesigned the audit page as a menu of seven areas, with a `category` on every audit row | [database-v1.9.md](docs/iterations/database-v1.9.md) |
| 2026-10-26 | 1.8 | Database | Cleaned the order part of every statement left from the old order lifecycle (an order is complete and final); defined the ticket screens and what each role sees on them; fixed the username and the email after sign-up and reserved two columns for a later change flow; defined item images; and stated that the open tickets of a disabled creator stay in the pool; then, from the page design, removed ticket comments, dropped the order list index, added `order_attempts` (every attempt to place an order is recorded, with its order number, whether or not it succeeds; the schema has 16 tables), split the log of an item into a warehouse log and a product log, made the first administrator a console command on the server (no forced password change), and defined ticket search, the user and audit pages, paging, and the order number on the ticket form | [database-v1.8.md](docs/iterations/database-v1.8.md) |
| 2026-10-24 | 1.7 | Database | Put the warehouse first: `inventory` became the warehouse item and `products` an item put on sale, created at its first listing. Added `product_listing_events` and the rules for listing, delisting (which cancels the tickets of the product), the product page, and ordering from it; order creation now locks the product rows and checks the name and price the user saw; added the search and list indexes and the product concurrency rules; added `inventory_item_events` and the rules for adding a warehouse item (generated SKU, unique name, optional starting quantity) and deleting it softly, a single rule that every quantity must be valid, opening a ticket on chosen items, and an order that completes the moment it is placed (no reservation, confirmation, timeout, or exception path) that is final, with a wrong order corrected through a ticket | [database-v1.7.md](docs/iterations/database-v1.7.md) |
| 2026-10-23 | 1.6 | Database | Replaced the ticket priority with `tickets.source` and a built-in SYSTEM identity (`users.is_system`) that opens and archives for the program; made `OPEN` a pool and taking a ticket start it at once; added releasing a ticket, returning the tickets of a disabled or deleted assignee, two limits of 99 per person, archiving after 7 days or when 99 are waiting, and a separate cancelled area; required an order on an `ORDER_EXCEPTION` ticket and had the program fill in the order and the problem on automatic tickets; wrote inventory links only at creation; defined the ticket log formats and three ticket indexes, ordered every ticket list with system tickets first, fixed a ticket's fields after creation, made the exception path stop the order and open the ticket together, and added the indexes for the ticket lists and the order and log lists | [database-v1.6.md](docs/iterations/database-v1.6.md) |
| 2026-10-22 | 1.5 | Database | Added the `WRITE_OFF` stock type and defined manual stock changes: a required reason and operator on every manual change, and direct quantity setting for `MANAGER` and `ADMIN` only; added the table `ticket_inventory` so one ticket can cover a batch of products, wrote down when a ticket should be opened, defined the ticket status flow (no reopening, `CLOSED` as the archive, a new `CANCELLED` status) and follow-up tickets, added the Concurrency by Module section, and defined who may take and reassign a ticket | [database-v1.5.md](docs/iterations/database-v1.5.md) |
| 2026-10-21 | 1.4 | Database | Defined a usable account, refused role grants to accounts that are not usable, made permission checks ignore them, defined role management (create, update, delete, replace a user's roles) and its audit logs, made a user with no role a valid state, fixed the rule that at least one usable `ADMIN` must always exist, defined how the first administrator is created, and added `users.must_change_password` | [database-v1.4.md](docs/iterations/database-v1.4.md) |
| 2026-10-21 | 1.3 | Database | Required the service layer to refuse role grants to soft-deleted users, and required role grants and revocations to be logged | [database-v1.3.md](docs/iterations/database-v1.3.md) |
| 2026-10-14 | 1.2 | Database | Unified stock concurrency on pessimistic row locking, clarified expired confirmation, froze order items, made composite keys explicit, added the initial migration, and verified concurrency and privileges | [database-v1.2.md](docs/iterations/database-v1.2.md) |
| 2026-10-14 | 1.1 | Database | Added stock reservation, redefined order statuses, and aligned the physical ERD with the design document | [database-v1.1.md](docs/iterations/database-v1.1.md) |

## Current Scope

The initial system scope includes:

- User authentication and role-based authorization
- Product and inventory management
- Transactional order creation with inventory deduction
- Inventory transaction history
- Exception-ticket assignment and reassignment history
- Ticket resolution tracking
- Auditable business operations

## Implementation Direction

The backend will be implemented with Spring Boot and MySQL. Database changes will be managed as versioned migrations alongside the application code. Redis and RabbitMQ will be introduced when the corresponding idempotency, rate-limiting, and asynchronous-processing requirements are implemented.

The learning documents remain available as supporting material, while `README.md` and `docs/` define the current project scope and implementation decisions.
