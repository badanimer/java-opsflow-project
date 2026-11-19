# Frontend Iteration v1.3

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current frontend and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.3 |
| Date | 2026-11-14 |
| Baseline | [frontend-v1.2.md](frontend-v1.2.md) |
| Type | Implementation: the React project and the four account pages |
| Trigger | The owner accepted the Figma draft of the account pages as the basis and asked for the React project to be created. The owner decided that sign-up asks for the password once |

## Summary

- The React project exists in `frontend/`: React 18 with TypeScript, Vite 5, Ant Design 5, and React Router 6.
- The four account pages are built as drawn: Login, Sign-up, Forgot password, and Pending, with the states of the design.
- One API client calls the backend. It adds the token, turns an error body into a typed error, and tells the login state when a login is over or when the account has no role.
- In development Vite passes `/api` on to Spring Boot, so the browser sees one origin and the backend needs no cross-origin rule.
- The suite passes: 87 tests in 6 files, with answers of the server set by each test.
- The frontend has a job in continuous integration.

## Changes

| Area | Implementation |
| --- | --- |
| Project | `frontend/`, created with the Vite template for React and TypeScript. Scripts: `dev`, `build`, `lint`, `test`, `test:watch`, `preview` |
| Routing | `/login`, `/sign-up`, and `/forgot-password` are for people who are not logged in; a logged-in user is sent on to `/`. `/pending` is for a logged-in user with no role. `/` and every later page need a role. Any other path goes to `/` |
| Session | The token and its expiry are kept in `localStorage`. The user and the roles are asked from `GET /auth/me` at each start, after a login, and when the user selects Check again |
| API client | `api()` in `src/api/client.ts`. `UNAUTHENTICATED` or `ACCOUNT_DISABLED` on a request that carried a token clears the token and opens the login page with the reason. `ACCOUNT_NO_ROLE` keeps the token and opens Pending. The same codes from a request without a token, such as the login, are ordinary answers. No answer, and a gateway error of a proxy, are reported as a broken connection |
| Pages | `LoginPage`, `SignUpPage`, `ForgotPasswordPage`, `PendingPage`, and a placeholder `HomePage` in the place of the product page |
| Texts | Every text of the interface is in `src/text.ts`, in English, with the wording of the design |
| Validation | `src/validation.ts` repeats the username rule, the password rule, the email format, and the 6 digits of a code, so a slip is shown under the field and nothing is sent. The server checks again |
| Email codes | `useEmailCode` sends the request and disables the button for 60 seconds with a countdown |
| Layout | No width is fixed: a card has a largest width and fills a narrow screen, and under 480 pixels the button for the code moves under the address. The account pages therefore work on a phone |
| Development proxy | `vite.config.ts` passes `/api` to `http://localhost:8080`, or to `OPSFLOW_API_TARGET`. When the backend is down it answers `502`, which the page shows as "Cannot reach the server" |
| Tests | Vitest with Testing Library and jsdom: the checks of the values, the API client, and the four pages through the whole application with a fake server (`src/test/server.tsx`) |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The Node of the continuous integration job | Node 22, the current long-term version, on which the pinned versions also run | Node 21.7 as on the development machine: it is not a long-term version |
| The versions | Vite 5 and jsdom 25 | The newest Vite and jsdom: they need a newer Node than the 21.7 installed on this machine. The newest jsdom failed at the first test run for that reason |
| How the backend is called | The browser's `fetch`, wrapped once | A request library: the wrapper is small, and one place has to know the error format anyway |
| Where the roles come from | `GET /auth/me`, at each start and on Check again | Storing the roles next to the token: an administrator can change them at any time |
| A user with no role | The route guards open Pending whatever page was asked for, and `ACCOUNT_NO_ROLE` from any request does the same and keeps the token | Logging the user out: the login is valid, and only the role is missing |
| The texts of the server | Not shown. A page decides from the result code and shows its own text | Showing `detail`: its wording may change, and the page would then mix two styles |
| The page for a user with a role | A placeholder that says the user is signed in and names the roles | Nothing at `/`: the login would have nowhere to go until the product page exists |
| The password at sign-up | Asked once, with the control that shows it. The owner's decision | A second field to confirm it |
| The messages of the email field | Shown once, by the form library, under the row of the address and its button | A second list of the same messages written by the page, which the first version had and the tests found |
| The label of the email field | Tied to its input, so a screen reader reads it | Left untied, which the tests found: the field sits inside a row with the button |

## Verification

- `npm run lint`, `npx tsc -b`, `npm test`, and `npm run build` on 2026-11-14: no lint or type error, **87 passed, 0 failed**, and the build succeeds. The build warns that the one script file is larger than 500 kB, which is Ant Design; it is not split yet.
- The development proxy was tried with both servers running: the page is served at `http://localhost:5173`, and through `/api` a wrong login answered `UNAUTHENTICATED`, `GET /auth/me` without a token `UNAUTHENTICATED`, a sign-up code for a registered address `EMAIL_TAKEN`, and a sign-up with a wrong code `CODE_INVALID`, each with its `requestId`. With the backend stopped the proxy answered `502`.
- The owner clicked through the four pages in a browser against the running backend on 2026-11-14, signed up with a code from a real mail, and logged in with the new account (T-61). The owner also reviewed the built pages against the Figma draft and accepted them (T-60).
- A continuous integration job for the frontend was added (T-62): `npm ci`, the lint, the tests, and the build, with Node 22. The same four steps were run locally from the lock file and passed. The job has not run on GitHub yet, because nothing is pushed.

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [`frontend/`](../../frontend/) | New: the code and the tests |
| [Page design document](../frontend/page-design.md) | Updated: Running the Frontend Locally, the layout and the tests of the frontend, and the Change Log |
| [documentation-sync-rules.md](../documentation-sync-rules.md) | Updated: a row for the frontend code and its tests |
| [README.md](../../README.md) | Updated: Documentation Structure and the Project Log |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: T-58 is done and removed, T-22 names what the frontend already does, T-60, T-61, and T-62 are done and removed, and T-55 now covers the first run of both jobs |
| [environment-setup.md](../environment-setup.md) | Updated: the note on the Node version |
| Backend, API design, database design | Not changed: the pages use the endpoints and the result codes that exist |
| [`.github/workflows/frontend-tests.yml`](../../.github/workflows/frontend-tests.yml) | New: the lint, the tests, and the build of the frontend on every push |
| [Backend design document](../backend/backend-design.md) | Updated: one sentence in Continuous integration that points to the frontend job (a note, no Change Log entry) |

## Open Items

- Read the first run of the frontend job, and of the backend job, on GitHub after the push (T-55).
- Return `ACCOUNT_NO_ROLE` from protected backend endpoints as they are built (T-22).
- The build puts everything in one script file of more than 500 kB. Split it when the application has more pages.
