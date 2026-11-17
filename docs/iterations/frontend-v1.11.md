# Frontend Iteration v1.11

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.11 |
| Date | 2026-11-18 |
| Baseline | [frontend-v1.10.md](frontend-v1.10.md) |
| Type | How the frontend is run in the local deployment. No page, text, or test changed |
| Trigger | Q-32, decided on 2026-11-18: the whole system runs from Docker Compose ([backend-v1.13.md](backend-v1.13.md)) |

## Summary

- `frontend/Dockerfile` builds the pages with Node 22 and serves them with nginx 1.27.
- `frontend/nginx.conf` passes `/api` to the backend, so the browser sees one origin, as with the dev server; sets `X-Forwarded-For` to the address of the connection, never to one the client sent; does not buffer the event stream of one signed-in device; accepts an upload of 3 MB, as the backend does; and answers every other path with the application, so a link to a page opens it.

## Changes

| Area | Change |
| --- | --- |
| `frontend/` | New `Dockerfile`, `nginx.conf`, and `.dockerignore` |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Who serves the pages in the deployment | nginx, which also passes `/api` on | The backend serving the built pages: the backend would carry the files of the frontend, and a second origin would need cross-origin rules |
| The client address | nginx sets `X-Forwarded-For` to the address of the connection | Appending to the header the client sent: a client could choose the address the login counts |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated: Change Log, Running the Frontend Locally (the layout and the deployment) |
| Figma | Not changed: no page changed |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
