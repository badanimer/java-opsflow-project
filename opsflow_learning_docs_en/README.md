# OpsFlow Full-Stack Project Learning Guide

This folder contains the English version of the OpsFlow learning documents.

OpsFlow is a medium-sized Java full-stack portfolio project: an order, inventory, and operations ticket management system. The purpose is not to follow a mechanical tutorial. The purpose is to help you understand what to think about, what to research, how to reason about business systems, and how to ask AI better questions when you get stuck.

## How To Use This Guide

Open one unit at a time. Before writing code, read the sections about what you need to understand and what questions you should consider. When you ask AI for help, do not only ask for code. Share your current design, what you have already tried, what you are unsure about, and what trade-off you are trying to understand.

At the end of each unit, try to keep three outputs:

- A short summary in your own words.
- A design note, code draft, or diagram you can keep improving.
- Three questions you still do not fully understand.

## Unit List

1. [01 Project Understanding And Business Modeling](01_project_understanding.md)
2. [02 Database Design](02_database_design.md)
3. [03 Spring Boot Project Setup](03_spring_boot_setup.md)
4. [04 Product And Inventory Module](04_product_inventory.md)
5. [05 Authentication And Role-Based Access Control](05_auth_rbac.md)
6. [06 Core Order Creation Flow](06_order_flow.md)
7. [07 Redis Idempotency And Rate Limiting](07_redis_idempotency.md)
8. [08 Exception Ticket Module](08_ticket_module.md)
9. [09 Operation Logs And Audit Trail](09_audit_logs.md)
10. [10 Frontend Admin Interface](10_frontend_admin.md)
11. [11 RabbitMQ Asynchronous Tasks](11_rabbitmq_async.md)
12. [12 Testing, API Documentation, And Validation](12_testing_docs_validation.md)
13. [13 Docker Deployment And Project Documentation](13_docker_readme.md)
14. [14 Resume Writing And Interview Preparation](14_resume_interview.md)

## Core Learning Principle

Do not turn this project into a random list of features. What you really need to build is an understanding of business systems: why orders need states, why inventory cannot be reduced carelessly, why logs matter, why exceptions become tickets, and why Redis and message queues solve real problems such as duplicate submissions, asynchronous processing, failure recovery, and troubleshooting.

When you can explain those problems clearly, the project becomes useful for internship applications.
