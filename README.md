# OpsFlow

OpsFlow is a Java full-stack portfolio project for order, inventory, and operations-ticket management. It is designed to demonstrate business modeling, transactional consistency, role-based access control, exception handling, and auditability in a realistic back-office system.

The repository currently contains the initial design and staged learning documents. Application code and database migrations will be added as the implementation progresses.

## Design Documents

The current project design is documented separately from the learning guide:

- [Initial Database Structure And Business Flow](docs/database/initial-database-design.md)
- [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) - a concise overview for the README and project introduction.
- [Initial Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) - table fields, keys, and implementation-oriented relationships.
- [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) - the English order, inventory, audit, and exception-ticket flow.

The simplified ERD is intended for quick communication. The physical ERD and database document are the references for implementation.

## Documentation Structure

- [`docs/database`](docs/database/) contains the current database design baseline and will later hold schema decisions and migration notes.
- [`opsflow_learning_docs_en`](opsflow_learning_docs_en/) contains the English learning path.
- [`汉化说明书`](汉化说明书/) contains the Chinese learning path.
- [`ENVIRONMENT_SETUP.md`](ENVIRONMENT_SETUP.md) records the local development environment setup.

## How To Use The Learning Guide

Open one unit at a time. Before writing code, read the sections about what you need to understand and what questions you should consider. When you ask AI for help, do not only ask for code. Share your current design, what you have already tried, what you are unsure about, and what trade-off you are trying to understand.

At the end of each unit, try to keep three outputs:

- A short summary in your own words.
- A design note, code draft, or diagram you can keep improving.
- Three questions you still do not fully understand.

## Unit List

1. [01 Project Understanding And Business Modeling](opsflow_learning_docs_en/01_project_understanding.md)
2. [02 Database Design](opsflow_learning_docs_en/02_database_design.md)
3. [03 Spring Boot Project Setup](opsflow_learning_docs_en/03_spring_boot_setup.md)
4. [04 Product And Inventory Module](opsflow_learning_docs_en/04_product_inventory.md)
5. [05 Authentication And Role-Based Access Control](opsflow_learning_docs_en/05_auth_rbac.md)
6. [06 Core Order Creation Flow](opsflow_learning_docs_en/06_order_flow.md)
7. [07 Redis Idempotency And Rate Limiting](opsflow_learning_docs_en/07_redis_idempotency.md)
8. [08 Exception Ticket Module](opsflow_learning_docs_en/08_ticket_module.md)
9. [09 Operation Logs And Audit Trail](opsflow_learning_docs_en/09_audit_logs.md)
10. [10 Frontend Admin Interface](opsflow_learning_docs_en/10_frontend_admin.md)
11. [11 RabbitMQ Asynchronous Tasks](opsflow_learning_docs_en/11_rabbitmq_async.md)
12. [12 Testing, API Documentation, And Validation](opsflow_learning_docs_en/12_testing_docs_validation.md)
13. [13 Docker Deployment And Project Documentation](opsflow_learning_docs_en/13_docker_readme.md)
14. [14 Resume Writing And Interview Preparation](opsflow_learning_docs_en/14_resume_interview.md)

## Core Learning Principle

Do not turn this project into a random list of features. What you really need to build is an understanding of business systems: why orders need states, why inventory cannot be reduced carelessly, why logs matter, why exceptions become tickets, and why Redis and message queues solve real problems such as duplicate submissions, asynchronous processing, failure recovery, and troubleshooting.

When you can explain those problems clearly, the project becomes useful for internship applications.
