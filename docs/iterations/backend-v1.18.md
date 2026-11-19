# Backend Iteration v1.18

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.18 |
| Date | 2026-11-19 |
| Baseline | [backend-v1.17.md](backend-v1.17.md) |
| Type | Version 1.0.0, the API documentation, the demo data, and the secrets script for macOS and Linux |
| Trigger | The owner, 2026-11-19, preparing the project for a résumé: a person who runs the project should find data on every page, a reader should see the API without reading the code, the deployment should start on macOS and Linux without PowerShell, and the finished program should have a version |

## Summary

- **Version 1.0.0** of the backend (`pom.xml`) and of the frontend (`package.json`). The image of the backend copies the jar whatever its version.
- **API documentation:** springdoc 3.0.3 with Swagger UI at `/api/v1/docs` and the description at `/api/v1/api-docs`, open with no login. `ApiDocsConfig` writes into every operation who may call it, from its declaration, and asks for the token on every operation that is not public.
- **Demo data:** the console command `seed-demo` makes five accounts, one for each role, with a password typed at a hidden prompt, and then fills the deployment through the API: six items with pictures drawn in the code, five products, stock changes, three orders and one refused, and a ticket on every ticket screen.
- **`deploy/new-env.sh`:** makes `.env` as `new-env.ps1` does, for macOS and Linux.

## Changes

| Area | Change |
| --- | --- |
| Build | `pom.xml`: version 1.0.0 and `springdoc-openapi-starter-webmvc-ui` 3.0.3. `Dockerfile`: the jar copied by a pattern. `frontend/package.json` and its lock file: version 1.0.0 |
| `common/web` | `ApiDocsConfig`: the description of the API and who may call each operation |
| `auth` | `SecurityConfig` lets the documentation through with `GET`. `AdminCreator.createWithRole`, the transaction of `create` with any fixed role. `DemoAccounts`, the five accounts. `CreateAdminCommand.describeStartFailure` is public |
| `demo` | New package: `SeedDemoCommand` (the console), `DemoSeeder` (the steps), `DemoClient` (the HTTP calls), `DemoImages` (the pictures) |
| `OpsflowBackendApplication` | The argument `seed-demo` |
| `application.yml` | The paths of springdoc, and only the package `com.opsflow` described |
| Deployment | `compose.yaml`: `OPSFLOW_DEMO_API_URL` for the command, and both secrets scripts named. `new-env.sh`. `.env.example` names both scripts |
| Tests | `ApiDocsTest` (2) and `SeedDemoTest` (2) |
| Documentation | Backend design: Change Log, Stack, Demo data, API documentation, Configuration, Deployment, Tests. API design, environment, README, and the test log |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The version of springdoc | 3.0.3, the last of the line built on Spring Boot 4.0 | 3.1 and later, built on Spring Boot 4.1 |
| How the demo writes its data | The accounts in the database, as `create-admin` does, and everything else through the HTTP API of the running backend as each person | Calling the services of each module from `demo`, which the rules between the packages forbid for their types, or SQL, which would skip the checks and the log rows |
| The password of the demo accounts | Typed at a hidden prompt, as for `create-admin` | A password written in the repository, which the rule of secrets forbids |
| The pictures | Drawn in the code, with shapes and no text | Image files in the repository, or text, which needs fonts in the container |
| When the demo runs | On a deployment without the five usernames; it stops at an item whose name is taken | Mixing into a deployment that holds real data |
| `new-env.sh` | `sh`, with the same letters and lengths as `new-env.ps1`, and `.env` readable by its owner only | Asking macOS and Linux users to install PowerShell |

## Verification

| Check | Result |
| --- | --- |
| `mvnw test` | 466 passed, 0 failed (2026-11-19); the run before had 462 |
| `new-env.sh` | In Git Bash and in Alpine Linux: four secrets of 32 letters and digits and a Base64 key of 32 bytes, `.env` with mode 600, and an existing `.env` kept |
| `seed-demo` in a deployment | In a second Compose project on port 8001, with its own volumes, so the owner's deployment was not touched: the first run made every step and printed the summary, a second run was refused because `adam` exists. Swagger UI answered through nginx (`/api/v1/docs` redirects to the page, which reads `/api/v1/api-docs`, 42 paths), and `/api/v1/users` stayed 401. The project was removed afterwards with its volumes |
| `docker compose config` | Valid |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated |
| [API design](../api/api-design.md) | Updated in [api-v1.13.md](api-v1.13.md) |
| [Environment](../environment-setup.md) | Updated: `OPSFLOW_DEMO_API_URL`, `new-env.sh` |
| [README.md](../../README.md) | Updated: Running It, Highlights, Tech Stack, the test count |
| [Test log](../../db/verification/concurrency-tests.md) | Updated |
| Database design, migrations, privileges | Not changed |

## Open Items

None.
