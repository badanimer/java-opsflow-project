# OpsFlow

OpsFlow is a Java full-stack order, inventory, and operations-ticket system. See [README.md](README.md) for the project overview.

## Documentation Rules

Before changing any design document, diagram, or iteration record, follow the rules below. They define which documents must be updated together and how to record each iteration.

@docs/documentation-sync-rules.md

All documentation is written in English.

## Where To Find Things

Read [docs/project-todo.md](docs/project-todo.md) first to see what is still open. Then use this map to find the rest. Do not rely on memory of earlier conversations: the answers are in these files.

| To find | Look in |
| --- | --- |
| Project overview, documentation structure, Project Log | [README.md](README.md) |
| Open tasks and undecided questions | [docs/project-todo.md](docs/project-todo.md) |
| The current database design: tables, fields, order and inventory flow, concurrency rules, privileges, verification results | [docs/database/database-design.md](docs/database/database-design.md) |
| What changed in each iteration, why, and which decisions were made | [docs/iterations/](docs/iterations/), one file per version |
| Which documents must be updated together | [docs/documentation-sync-rules.md](docs/documentation-sync-rules.md) |
| The executable schema | [db/migration/](db/migration/) |
| Privileges for the application account, run once after the first migration | [db/admin/](db/admin/) |
| The temporary-database check of the schema, privileges, and concurrency rules | [db/verification/](db/verification/) |
| Local setup: MySQL accounts, environment variables, services, follow-up tasks | [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md) |
| Diagrams: Simplified ERD, Physical ERD, Business Process Flowchart (Figma boards, not in git) | The Design Diagrams table in [docs/database/database-design.md](docs/database/database-design.md) |
| Learning material (English and Chinese) | [opsflow_learning_docs_en/](opsflow_learning_docs_en/) and [汉化说明书/](汉化说明书/) |

Secrets never go into the repository. Real passwords come from environment variables, and the files only hold placeholders.