# Backend Iteration v1.15

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.15 |
| Date | 2026-11-19 |
| Baseline | [backend-v1.14.md](backend-v1.14.md) |
| Type | How the deployment runs, and the documentation prepared for a public repository. No code changed |
| Trigger | After a restart of the machine, `http://localhost:8000` did not answer: the containers had no restart policy and stayed stopped. The owner asked on 2026-11-19 for OpsFlow to start when Docker Desktop starts, without Docker Desktop starting with Windows. The same day the owner asked for the repository to be reviewed and tidied before it is made public on GitHub, to be shown on a résumé |

## Summary

- `mysql`, `redis`, `rabbitmq`, `backend`, and `web` in `deploy/compose.yaml` have `restart: unless-stopped`.
- Starting Docker Desktop starts OpsFlow again; a service stopped by hand stays stopped.
- The README is rewritten for a reader who sees the project for the first time: the highlights, an architecture diagram, how to run it, and the tests, with CI badges. The Project Log moved out of it into [`docs/iterations/README.md`](README.md).
- `ENVIRONMENT_SETUP.md` moved to [`docs/environment-setup.md`](../environment-setup.md) and was rewritten as a guide for any development machine, without the paths and hardware of one machine and without the steps that were already done.
- An MIT `LICENSE` was added.

## Changes

| Area | Change |
| --- | --- |
| `deploy/compose.yaml` | `restart: unless-stopped` on the five long-running services, and a note at the top of the file |
| `README.md` | Rewritten: Highlights, Architecture (a Mermaid diagram), What It Does, Tech Stack, Running It, Tests with the counts of the latest runs, Documentation Structure, How It Was Built, Status, License |
| Project Log | Moved from `README.md` to `docs/iterations/README.md`, unchanged, with a table of the current-state documents at the top |
| Environment | `ENVIRONMENT_SETUP.md` moved to `docs/environment-setup.md`. Removed: the install paths and the hardware of the development machine, the record of what was set up on which day, the one-off grants for a database made before a migration, and the list of follow-up tasks, all done. Kept: the tools, the three MySQL accounts and the per-table rights, Redis, RabbitMQ, Docker, the variables, the mail account, and the rules for secrets. Every link to the old file now points to the new one, the iteration files included |
| Figma links | The link of the Business Process Flowchart lost the tracking parameters it was copied with |
| `LICENSE` | MIT |
| Process | [documentation-sync-rules.md](../documentation-sync-rules.md) and `CLAUDE.md` name `docs/iterations/README.md` as the place of the Project Log |
| Documentation | Backend design: Change Log, After the machine restarts under Deployment, and the links to the environment document. Roadmap: Phases 7 and 8 marked done, with Q-28 left |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The restart policy | `unless-stopped`: back after Docker starts, but not after the owner stopped it | `always`, which would start a service the owner stopped; `on-failure`, which does not start after a restart of the machine |
| Which services | The five that keep running | `migrate` and `privileges`, which run once and end; with a restart policy they would run again on every start of Docker without a reason |
| Docker Desktop at sign-in | Not set (the owner's choice): OpsFlow runs when the owner starts Docker Desktop | Starting Docker Desktop with Windows |
| The Git history when the repository is made public | Kept as it is (the owner's choice). It holds the database password of [Lessons From The Password Exposure](database-v1.4.md#lessons-from-the-password-exposure), which was rotated long ago and was only ever used on the development machine, and the full history is part of what the project shows | A new repository with one commit, which would drop the history |
| `CLAUDE.md` and the sync rules in a public repository | Kept (the owner's choice), and the README says the code was written with an AI coding assistant under those rules | Removing `CLAUDE.md` from the repository |
| The README | Rewritten for a first-time reader, with the Project Log in its own file (the owner's choice) | Only fixing the links and the status |
| A license | MIT (the owner's choice) | No license, which lets others read the code but not use it |

## Verification

| Check | Result |
| --- | --- |
| `docker compose config` | Valid |
| `docker compose up -d` | The five containers recreated with the policy `unless-stopped`, all running, the data kept, and `http://localhost:8000` answering 200 (2026-11-19) |
| Links | Every relative link in the Markdown files points to a file that exists, and every link to a heading points to a heading that exists |
| `mvnw test`, `npm test` | Not run: no code changed. One comment in `MySqlIntegrationTest` names the new path of the environment document |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated |
| [README.md](../../README.md) | Rewritten |
| [Project Log](README.md) | New: moved from `README.md` |
| [environment-setup.md](../environment-setup.md) | Moved from `ENVIRONMENT_SETUP.md` and rewritten |
| [documentation-sync-rules.md](../documentation-sync-rules.md), `CLAUDE.md` | Updated: the place of the Project Log and of the environment document |
| [implementation-roadmap.md](../implementation-roadmap.md) | Updated: Phases 7 and 8 |
| `LICENSE` | New |
| Figma boards | Not changed. Their sharing must allow anyone with the link to view them, or a visitor of the public repository is asked to log in |

## Open Items

None.
