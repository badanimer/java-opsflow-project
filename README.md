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
- [`db/migration`](db/migration/) contains the executable database migrations, applied in version order. See [Initial Migration](docs/database/database-design.md#initial-migration).
- [`db/admin`](db/admin/) contains database administration scripts that are not migrations, such as the per-table privileges for the application account.
- [`db/verification`](db/verification/) contains the script that verifies the schema, the privileges, and the order and inventory concurrency rules in a temporary database. See [Concurrency and privilege verification](docs/database/database-design.md#concurrency-and-privilege-verification).
- [`docs/iterations`](docs/iterations/) contains one summary file per design iteration, indexed in the Project Log below.
- [`docs/project-todo.md`](docs/project-todo.md) lists the confirmed tasks and the undecided questions that are still open.
- [`docs/documentation-sync-rules.md`](docs/documentation-sync-rules.md) defines which documents must be updated together and how iterations are recorded. Read it before changing any design document or diagram.
- [`opsflow_learning_docs_en`](opsflow_learning_docs_en/) contains the English learning path.
- [`汉化说明书`](汉化说明书/) contains the Chinese learning path.
- [`ENVIRONMENT_SETUP.md`](ENVIRONMENT_SETUP.md) records the local development environment setup.

## Project Log

Iteration notes live in [`docs/iterations`](docs/iterations/), one file per iteration. Entries are listed newest first. The process for recording iterations and keeping documents in sync is defined in [documentation-sync-rules.md](docs/documentation-sync-rules.md).

| Date | Version | Area | Summary | Details |
| --- | --- | --- | --- | --- |
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
- Ticket comments and resolution tracking
- Auditable business operations

## Implementation Direction

The backend will be implemented with Spring Boot and MySQL. Database changes will be managed as versioned migrations alongside the application code. Redis and RabbitMQ will be introduced when the corresponding idempotency, rate-limiting, and asynchronous-processing requirements are implemented.

The learning documents remain available as supporting material, while `README.md` and `docs/` define the current project scope and implementation decisions.
