# OpsFlow Page Design

## Purpose

This document collects the page design of OpsFlow: which pages exist, who sees them, what each one shows, and what it does. It is the starting point for the UI design, and it records the owner's ideas while they are still rough. It is a first version. The frontend stack is chosen, and the account pages, the frame of the application, and the user page are drawn and built (see Frontend Stack And Design Files); the other pages are not drawn yet.

The pages are designed backwards from the database: the screens decide what the database must be able to answer. The rules that reach into the database or the service layer are in [Screens and Lists](../database/database-design.md#screens-and-lists), and the rules of each flow are in [database-design.md](../database/database-design.md). This document does not repeat them in full. Where a rule matters to a page, it is summarized here with a link.

## Change Log

This document always describes the current page design. Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-19 | 1.14 | The Tickets area of the audit page offers Ticket taken as its own action, apart from Ticket assigned, and a take reads "Taken from the pool". Drawn in Figma and built | [frontend-v1.14.md](../iterations/frontend-v1.14.md) |
| 2026-11-19 | 1.13 | Each ticket screen shows the person and the time of its own step in place of the creator: Assignee and Assigned in progress, Resolved by, Archived by, and Cancelled by with their times, ordered and filtered by that time; the Tickets area of the audit page has a Ticket number filter that combines with the action and the user. Drawn in Figma and built | [frontend-v1.13.md](../iterations/frontend-v1.13.md) |
| 2026-11-18 | 1.12 | Who sees which order records (Q-34): an operator sees only the orders they placed, and the page says so; a manager, an administrator, and a viewer get an Ordered by filter, the user picker, on the order records. The last open detail of the page design is decided | [frontend-v1.12.md](../iterations/frontend-v1.12.md) |
| 2026-11-18 | 1.11 | The frontend image of the local deployment: the pages built and served by nginx, which passes `/api` to the backend. No page changed | [frontend-v1.11.md](../iterations/frontend-v1.11.md) |
| 2026-11-18 | 1.10 | The login lockout by client address: the login page shows `TOO_MANY_REQUESTS` as "Too many requests", the expanded audit row labels the locked address, and the sign-in lock rows in Figma read as a lock of one address | [frontend-v1.10.md](../iterations/frontend-v1.10.md) |
| 2026-11-16 | 1.9 | Phase 6 is drawn in Figma and built: the five ticket screens with the search and the batch archive, the ticket detail with its actions, Create ticket as a page of its own, and the audit log page with its seven areas and the rows read by their labels; the warning of Change roles before tickets go back to the pool; the length limit of every text field and the counters of the long ones | [frontend-v1.9.md](../iterations/frontend-v1.9.md) |
| 2026-11-16 | 1.8 | Phase 5 is drawn in Figma and built: the order dialog, the quantity change of the stock page, the two logs as pages of their own with the time filter, the picker, and the box for one order number, the own log of one product or item as tabs, and the Deleted items module | [frontend-v1.8.md](../iterations/frontend-v1.8.md) |
| 2026-11-16 | 1.7 | The code of the frontend is sorted into components, hooks, and plain functions, and the four account pages move into their own folder. No page changes | [frontend-v1.7.md](../iterations/frontend-v1.7.md) |
| 2026-11-15 | 1.6 | The product page and the first part of the stock page are drawn in Figma and built: the cards of the products with the price in Australian dollars, List item, and Delist; the rows of the items with adding, editing (only while not on sale), deleting, and the picture. The quantity changes and the logs come in Phase 5 | [frontend-v1.6.md](../iterations/frontend-v1.6.md) |
| 2026-11-15 | 1.5 | A second confirmation before an action that is hard to take back, and where it is asked; quick ranges in the time filter. The frame of the application with the navigation by permission, the user page, and its dialogs are drawn in Figma and built; the font is the system font. One signed-in device: a page whose account signed in elsewhere shows a dialog, and the login page says why | [frontend-v1.5.md](../iterations/frontend-v1.5.md) |
| 2026-11-14 | 1.4 | After the review of the Permission Matrix: an operator can also make a correction on the stock page, the creator and assignee filters and the choice of an assignee use a user picker that shows usernames only, an administrator sees their own account on the user page with no control to change it, and a user with several roles sees what any of them allows | [frontend-v1.4.md](../iterations/frontend-v1.4.md) |
| 2026-11-14 | 1.3 | Implemented the four account pages, the route guards, and the shared API client in `frontend/` | [frontend-v1.3.md](../iterations/frontend-v1.3.md) |
| 2026-11-13 | 1.2 | The frontend stack is chosen (React, TypeScript, Vite, Ant Design), the interface is in English, the token is kept in `localStorage`, and every page is drawn in Figma before it is built. The four account pages are drawn as a first draft | [frontend-v1.2.md](../iterations/frontend-v1.2.md) |
| 2026-11-12 | 1.1 | A disabled account is told so on the login page and never reaches the pending page; a user whose account is disabled while logged in is sent back to the login page with the same message; the pending page is only for a user with no role. Extended on 2026-11-13: what the sign-up page and Forgot password show for each answer about an email code, and the username rule | [frontend-v1.1.md](../iterations/frontend-v1.1.md) |
| 2026-10-26 | 1.0 | First version: the page map, what each role sees, and a first description of every page, built from the earlier design and from the owner's rough design of the pages | [frontend-v1.0.md](../iterations/frontend-v1.0.md) |

## Frontend Stack And Design Files

| Item | Choice |
| --- | --- |
| Framework | React with TypeScript, built with Vite. The project lives in `frontend/`, next to `backend/` |
| Components | Ant Design. Its default look is the starting point of the visual design |
| Font | The system font of the device, which is Ant Design's default. The Figma file draws with Inter as a stand-in; the difference is in the letter shapes only, not in the layout (the owner's decision, 2026-11-15) |
| Language of the interface | English, for every text on every page |
| Login token | Kept in the `localStorage` of the browser, so a refresh keeps the user logged in until the token expires after 12 hours. On `UNAUTHENTICATED` or `ACCOUNT_DISABLED` the frontend removes it |
| Design before code | Each page is drawn in Figma first, reviewed by the owner, and then built against the finished endpoints |
| Order of work | Beside the backend, one module at a time |

The UI design file is [OpsFlow UI in Figma](https://www.figma.com/design/68brMszRAYI4d261GKL1GU). It is not tracked by git. Its page "Login and Sign-up" holds the design of the account pages, which the owner reviewed and accepted on 2026-11-14:

- **Components**: a field (label, input, and a helper or error line), three buttons (primary, default, disabled), three alerts (error, info, warning), and the logo. The colours are variables with the default values of Ant Design.
- **Login**: the empty page, and its states: wrong username or password, locked after failed attempts, account disabled, empty fields, signing in, after a password reset (a success message), session expired, signed in elsewhere (added on 2026-11-15, a warning), something went wrong (with the request id as a reference to report), and cannot reach the server.
- **Sign-up**: the empty page, and its states: code sent (the button for a new code is disabled for 60 seconds), email already registered, wrong or expired code, username taken (the code is kept), too many requests, invalid username, invalid email, invalid password, a code that is not 6 digits, empty fields, sending the code, and creating the account.
- **Forgot password**: the empty page, and its states: code sent, a new password equal to the current one, invalid password, wrong or expired code, too many requests, and resetting.
- **Pending**: the page of a user with no role, with a button that checks again and a link to sign out, and the state after a check that still found no role.

Two more pages of the file were drawn for Phase 3 and reviewed by the owner on 2026-11-15:

- **App shell and Users**: the frame of the application for each role and for a user with two roles, loading the account, cannot reach the server, a fault with its reference, Not allowed, Page not found, the navigation in a drawer on a small screen; the user page for an administrator, a manager, a viewer who may change nothing, and one who may only change roles, with its filters, paging, empty lists, and failures; and Change roles, Disable, Enable, and Delete with their second confirmations, their running states, and each answer of the server; and `Session / Signed in elsewhere`, the dialog of a page whose account signed in on another device. Each frame has a note for the builder: the permission, the Ant Design component, and the result code.
- **Stock and Products** (drawn by Codex and reviewed by the owner on 2026-11-15, with the two changes of [frontend-v1.6.md](../iterations/frontend-v1.6.md)): the product page, List item, Delist, the stock page, Add item, Edit item, Delete item, and the picture of an item, each in every state, for each kind of role, with the room for Phase 5.
- **Orders, Stock changes, and Logs** (drawn by Codex and reviewed by the owner on 2026-11-16, with three corrections; see [frontend-v1.8.md](../iterations/frontend-v1.8.md)): the order dialog in every state; Adjust quantity for stock in, write-off, and correction in every state; the chooser of View records; the four lists of records; the own log of a product, of an item, and of a deleted item with and without a product; and Deleted items. The product page and the stock page of "Stock and Products" have frames for a manager, an operator, and a viewer.
- **Tickets and Audit** (drawn by Codex and reviewed on 2026-11-16, with the corrections of [frontend-v1.9.md](../iterations/frontend-v1.9.md)): the five ticket screens for every role with the search, the order, the paging, and the batch archive; the detail of each status for every role, and the dialogs of Take, Assign, Reassign, Release, Resolve, Cancel, and Archive in every state; Create ticket; the audit log menu and its seven areas with the rows expanded; and the navigation of each role.
- **Shared components**: tags, the filter bar, the paged list, the confirm dialog, the dialog that requires a reason, the result message, the time filter with its quick ranges, the user picker, and the searchable picker. The last four are for later pages.

Rules that the states follow:

- **The page checks a value before it sends it.** An empty field, a username or a password that breaks its rule, an address that is not an email address, and a code that is not 6 digits are shown under the field, in red, and nothing is sent. The server checks the same rules again. The message for a password is "Use 8 to 24 characters, with at least one letter and one digit."
- **Every text field has the length limit of the API**, checked in the page after the spaces around the text are removed: a text over it is shown in red under the field and nothing is sent, as the stock dialogs already do. The limits are a name 200, a reason 255 (stock changes, and releasing, reassigning, assigning, and cancelling a ticket), a ticket title 200, a ticket description 4,000, and a resolution 2,000. A long text box (a description or a resolution) shows how many characters are used, such as "120 / 4000". See Limits in [api-design.md](../api/api-design.md#general-rules).
- **Every "too many" message says "Please try again in a minute."**, for the lock of the login and for the limits on code requests alike, and names no time. This is the owner's wording. The real wait can be longer: the lock grows to 5 and 15 minutes and to midnight, and the limits on code requests last to the end of the hour or of the day. A person who tries again after a minute is then refused again with the same message.
- **While a request runs** the button is disabled and says what is happening ("Signing in…"), so a second click sends nothing.
- **A failure that is not the user's** has two messages: "Something went wrong" with the request id for `INTERNAL_ERROR` and `BUSY`, and "Cannot reach the server" when no answer came.
- **After a password reset** the user is sent to the login page, which says that the password was reset. **After `UNAUTHENTICATED` on any page** the login page says that the session has expired.
- **One signed-in device.** When the account signs in on another device, every signed-in page, the pending page included, shows the dialog "You have been signed out" at once. It cannot be closed; its one button, Sign in again, removes the token and opens the login page, which says "You were signed out because your account signed in on another device. If this was not you, reset your password." The page learns it from the event stream `GET /auth/session-events`, from `SESSION_REPLACED` on any request, and by asking for the account when the person comes back to the tab. When another tab of the same browser signed in again, no dialog is shown: the page loads the new account. See One signed-in device in the [database design](../database/database-design.md#login-security-and-account-recovery).

## Running the Frontend Locally

From `frontend/`, run `npm ci` to install the locked dependencies, then `npm run dev`. Open `http://localhost:5173`. Vite forwards `/api` to Spring Boot at `http://localhost:8080` by default. Set `OPSFLOW_API_TARGET` in the shell that starts Vite if the backend runs at a different address. The backend setup is described in [OpsFlow Backend](../backend/backend-design.md). The whole system, the pages included, also runs from Docker Compose at `http://localhost:8000` (see [Deployment](../backend/backend-design.md#deployment)).

Run `npm test`, `npm run lint`, and `npm run build` from `frontend/` to check the project. While a change is made, `npx vitest related src/pages/tickets/TicketListPage.tsx` runs only the tests that import the files named, and `npx vitest run src/pages/tickets` the tests of one folder; before every commit, run all three commands in full. The frontend tests substitute API responses. The owner clicked through the four pages against the running backend on 2026-11-14, signed up with a code from a real mail, and logged in. On 2026-11-15 the owner clicked through the frame of the application and the user page with every role, and signed in to one account on two browsers to see the first one signed out. On 2026-11-16 the owner clicked through the product page and the stock page as a manager and as a viewer: an item added with a picture, edited, listed, delisted, and deleted, and the refusals. Later that day the owner clicked through Phase 5: orders, the three stock changes, the logs with their filters, the own logs, Deleted items, the page number, and the page size of the product page. On 2026-11-17 the owner clicked through Phase 6: the five ticket screens, a ticket through every status, the cascades of the user and warehouse modules, the search, Create ticket, and the audit log page. The click-through brought four changes, built the same day: a second confirmation for every change of roles, the tickets of one item as a search filter, a reason on every log row, and the status buttons with the deleted users on the user page and the rows about one user on the audit page.

The project needs Node 20 or later. It uses Vite 5 and jsdom 25, because the newest versions of both need a newer Node than the 21.7 on the development machine; raise them together with Node.

| Path in `frontend/` | Content |
| --- | --- |
| `src/api` | `client.ts`, the one place that calls the backend, with `readEventStream` for the event stream and multipart for a file; `auth.ts`, `users.ts`, `items.ts` (with the three stock changes), `products.ts`, `orders.ts`, `logs.ts`, `tickets.ts` (with the user picker), and `audit.ts`, the endpoints of Authentication, Users, warehouse items with their pictures, products, orders, the logs, the tickets, and the audit log page; `paging.ts`, the page and the cursors of every list |
| `src/auth` | The stored token (`session.ts`), the login state (`AuthProvider.tsx`, `context.ts`; it also keeps the event stream open and checks the account when the tab is shown again), the route guards (`guards.tsx`, with `RequirePermission`), and `permissions.ts`, the permission names and `can` |
| `src/pages` | One folder for each part of the application: `account/`, the four account pages (login, sign-up, Forgot password, and Pending); `users/`: the user page, the Change roles dialog, the tags, and `userRules.ts`; `stock/`: the stock page, `ItemDialog.tsx`, which adds and edits an item, and `AdjustQuantityDialog.tsx`; `products/`: the product page (the home page), `ListItemDialog.tsx`, the two steps of List item, and `OrderDialog.tsx`; `logs/`: View records (`RecordsChooser.tsx`), a list of one kind (`LogListPage.tsx`), the own log (`OwnLogPage.tsx`), Deleted items, the columns of each kind, and the filters kept in the address; `tickets/`: the five screens (`TicketListPage.tsx`, with the search and the batch archive), the detail (`TicketDetailPage.tsx`), the dialog of an action (`TicketActionDialog.tsx`), Create ticket (`CreateTicketPage.tsx`), and the search kept in the address (`ticketState.ts`); `audit/`: the menu and an area (`AuditPages.tsx`), and how a row reads (`auditRows.ts`). Not allowed and Page not found (`StatusPages.tsx`) stay at the top |
| `src/components` | What is drawn and shared by the pages: the frame of the application (`AppLayout.tsx`, `navigation.tsx` with every page of the Page Map and its permission, `TopBar.tsx`, `PageHeader.tsx`), the frame of the account pages, the logo, `SessionReplacedDialog.tsx`, `ConfirmDialog.tsx` (with a mode that leaves only Close), `CursorPager.tsx`, `ListStates.tsx` (the empty and failed list), `ItemThumbnail.tsx` (a picture or its placeholder), `ImageField.tsx` (the picture of an item, uploaded by itself), and the parts of the logs: `TimeFilter.tsx`, `RecordPicker.tsx` (the searchable picker), `OrderNumberSearch.tsx`, `RecordTable.tsx` (a list of records with its states), and `RecordOrderNumber.tsx`; and `UserPicker.tsx`, the user picker of the ticket search, the assignment, and the audit page |
| `src/hooks` | The React hooks the pages share: `usePagedList.ts` (a searchable list paged by key), `useRecords.ts` (a list of records that starts again when a filter changes), and `useEmailCode.ts` (sending an email code and the wait before the next one) |
| `src/lib` | Plain functions with no React: `format.ts` (how a price is shown, `$12.50`), `days.ts` (today in Sydney, the quick ranges, and a time as "15 Nov 2026 15:58"), `validation.ts` (the checks a page makes before it sends a value), `imageFile.ts` (the size and the first bytes of a picture), `failure.ts` (the message for a failed request), and `roles.ts` (the four roles as the interface names them, and which are protected) |
| `src/text.ts` | Every text of the interface |
| `src/test` | The set-up of the tests, the fake server, the screen width (`screen.ts`), and the users of the user page tests (`users.ts`) |
| `vite.config.ts` | The development proxy and the test configuration |
| `Dockerfile`, `nginx.conf`, `.dockerignore` | The image of the local deployment (2026-11-18, Q-32): the pages built with Node 22 and served by nginx, which passes `/api` to the backend as the dev server does, sets the client address, and does not buffer the event stream. See [Deployment](../backend/backend-design.md#deployment) |

The tests are next to the code they test, in files that end in `.test.ts` or `.test.tsx`: `lib/validation.test.ts`, `api/client.test.ts`, one for each of the four account pages in `pages/account/`, `components/AppLayout.test.tsx` for the navigation of each role and the pages that cannot be opened, `pages/users/UsersPage.test.tsx` and `UserChanges.test.tsx` for the user page and its four changes, `auth/SessionReplaced.test.tsx` for one signed-in device, `pages/stock/StockPage.test.tsx` and `ItemDialog.test.tsx` for the stock page and adding and editing an item with its picture, `pages/products/ProductsPage.test.tsx` and `ListItemDialog.test.tsx` for the product page, delisting, and List item, `OrderDialog.test.tsx` and `stock/AdjustQuantityDialog.test.tsx` for the two dialogs of Phase 5, `pages/logs/LogListPage.test.tsx` and `OwnLogPage.test.tsx` for the logs, `pages/tickets/TicketListPage.test.tsx`, `TicketDetailPage.test.tsx`, and `CreateTicketPage.test.tsx` for the tickets, `pages/audit/AuditPages.test.tsx` and `auditRows.test.ts` for the audit log page, and `lib/format.test.ts` and `lib/days.test.ts`. They open the whole application at a path and set the answers of the server themselves, so they need no backend. The answer of `GET /auth/me` in a test carries the permissions that the backend's `Role` gives, from a copy kept in `test/server.tsx`; the application itself has no such table. A test of a small screen sets the width in `test/screen.ts`. The fake server answers the event stream with one that stays open, and the product page with an empty page; a test sends an event or sets the products itself. The latest run, on 2026-11-19, had 302 tests in 25 files, all passed, after Ticket taken on the audit page ([frontend-v1.14.md](../iterations/frontend-v1.14.md)); the run before it, the same day, had 300. A test may take 30 seconds (`testTimeout` in `vite.config.ts`), and the ticket and audit tests 45, because they drive whole pages of Ant Design in jsdom while the other files run in parallel; with 15 seconds, the slowest older tests failed under the load of the new ones and passed on their own. GitHub Actions runs `npm ci`, the lint, the tests, and the build on every push, with Node 22, in [`.github/workflows/frontend-tests.yml`](../../.github/workflows/frontend-tests.yml). The job needs no backend and no secret.

Every page of a user with a role sits in the frame of the application. The navigation shows the entries of the user's permissions only. Every page of the Page Map is built; an entry whose page is not built would be shown disabled. A page whose permission the user lacks says Not allowed, and an unknown address says Page not found. The root route is the product page. A response of `ACCOUNT_NO_ROLE` from a protected API call sends the signed-in user to Pending and keeps the token.

## Principles

- **The first page is the product page.** After login every user with a role lands on it, even when no product is listed.
- **An order is an action, not a page.** There is no order list and no order page. A person with the right orders from a product, chooses a quantity, and gets a result with an order number. Every attempt is recorded, successful or not, and is read in the product log of the item.
- **The warehouse comes first.** The stock page is where items are added, removed, and corrected. The product page only shows the items that are on sale.
- **Each role sees only what it needs.** An operator sees only its own tickets after the pool. A viewer reads everything that a manager reads, and changes nothing.
- **Lists have two buttons.** Previous and next, 10 rows by default, the page sized to the layout. No page numbers, no total (see [Home page and paging](../database/database-design.md#home-page-and-paging)).
- **Times are shown in the display time zone** (`Australia/Sydney`) and stored in UTC.
- **No notifications, no comments, no dashboard** in the first version. A person finds out about a change by looking at the screen. The one exception is a security notice, not a notification centre: a page is told at once when its account signs in on another device (see One signed-in device under Frontend Stack And Design Files).
- **An action that is hard to take back asks for a second confirmation** (see Second Confirmation).

## Second Confirmation

The owner decided on 2026-11-15 that a key decision is confirmed a second time. A second confirmation is asked when an action:

- cannot be undone;
- takes access away from a person at once; or
- changes the roles of a user (every change since 2026-11-17, not only one that gives or takes away Administrator or Manager or leaves no role).

It is not asked for an action that is frequent and easily reversed, or where the dialog already makes the person type a reason or a quantity and shows what will change. Too many confirmations teach people to click through them.

How it looks: a dialog that names the target and says what will happen, with a confirm button that repeats the verb ("Delete", "Disable", never "OK"), in the danger style when the action removes something, and Cancel as the safe choice. While the request runs, both buttons are disabled.

| Action | Second confirmation | Why |
| --- | --- | --- |
| Delete a user | Yes | It cannot be undone |
| Disable a user | Yes | The person loses access at once |
| Enable a user | No | It gives back what was there, and Disable undoes it |
| Change roles, every change | Yes, after Save (Figma frames "Change roles / Confirm ordinary change" and "Confirm with tickets returning"), naming each role added and removed (the owner's decision on 2026-11-17, after clicking through the page: before it only a change of Administrator or Manager and an empty set were asked again) | A role decides what a person can see and do, and a wrong tick is easy |
| Change roles, when the new set is empty | The same confirmation says that the user will be able to use nothing | The user is sent to the pending page |
| Change roles, when tickets in progress would go back to the pool | The same confirmation says how many | The user loses the work in hand, and others must take it |
| Delete a warehouse item | Yes | It cannot be undone, delists its product, and cancels its open tickets |
| Delist a product | Yes | It cancels the open tickets linked to it |
| Archive tickets, singly or in a batch | Yes, naming how many | Archived tickets cannot be moved again |
| Cancel a ticket | No second step | The reason dialog is the confirmation; it says that a cancelled ticket cannot be reopened |
| Release or reassign a ticket | No second step | The reason dialog is the confirmation |
| Stock in, write-off, correction | No second step | The reason dialog is the confirmation, and it shows the quantity before and after |
| Place an order | No | The order dialog is the confirmation, and a repeated click is caught by the idempotency key |
| Sign out | No | Nothing is lost |

## Page Map

| Page | Who sees it | What it is for |
| --- | --- | --- |
| Login | Everyone | Log in |
| Sign-up | Everyone | Create an account with an email code |
| Forgot password | Everyone | Set a new password with an email code. The only way to change a password |
| Pending page | A user with no role | Shows that the account is waiting for an administrator to assign permissions |
| Product page | `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER` | The home page. Lists the products on sale, is where an order is placed, and has the product log (View records, and a View button on each product) |
| Stock page | The same four roles | One row for each warehouse item, with its stock, its correction button, a View button for its own log, and a View records button for the warehouse log of all items |
| Ticket pool | The same four roles | `OPEN` tickets that nobody holds |
| Ticket in progress | The same four roles | `IN_PROGRESS` tickets |
| Ticket resolved | The same four roles | `RESOLVED` tickets that wait to be archived |
| Ticket archive | `ADMIN`, `MANAGER`, `VIEWER` | `CLOSED` tickets |
| Ticket cancelled | The same four roles | `CANCELLED` tickets |
| Ticket detail | Anyone who can see the ticket in a list | Everything about one ticket |
| User page | `ADMIN`, `MANAGER` | Manage the users below the viewer's level |
| Audit log page | `ADMIN`, `MANAGER` | Read the log of important actions |

**Not planned for the first version:** an order list or order page, a page for the roles themselves (the four roles are fixed), an account or profile page, a page for creating the first administrator (done with a console command on the server), comments on tickets, a notification centre, and a dashboard.

## What Each Role Sees

| Page or part | `ADMIN` | `MANAGER` | `OPERATOR` | `VIEWER` |
| --- | --- | --- | --- | --- |
| Product page | Yes, with List and Delist | Yes, with List and Delist | Yes, with Order | Yes, read only |
| Order button | Yes | Yes | Yes | No |
| Stock page and the warehouse log | Yes | Yes | Yes | Yes |
| Product log (on the product page) | Yes | Yes | Yes | Yes |
| Stock in and write-off (plus and minus) | Yes | Yes | Yes | No |
| Correction (typing the quantity that was counted) | Yes | Yes | Yes | No |
| Add and delete a warehouse item | Yes | Yes | No | No |
| Ticket pool | All | All | All | All |
| Ticket in progress | Everyone's | Everyone's | Only tickets assigned to them | Everyone's |
| Ticket resolved | All | All | Only tickets they resolved | All |
| Ticket archive | Yes | Yes | No | Yes |
| Ticket cancelled | All | All | Only tickets they created or last held | All |
| Search on the ticket screens | Yes | Yes | No | Yes |
| Create a ticket | Yes | Yes | Yes | No |
| User page | All users | Users below manager | No | No |
| Audit log page | Yes | Yes | No | No |

A user who holds several roles sees and can use whatever any one of them allows. For example, a user who is both an operator and a viewer sees everyone's tickets and the search, and can also take a ticket.

The full rules are in the [Permission Matrix](../database/database-design.md#permission-matrix).

## Pages

### Login, sign-up, and Forgot password

Ordinary pages, in the usual style of a web application. There are three functions and no more.

- **Login** takes a username and a password. An error never says whether the username exists. After five wrong passwords the account is locked for a time, and the page says "Too many failed attempts. Please try again in a minute." It does not show the time the lock ends, although the API sends it (see [Login security](../database/database-design.md#login-security-and-account-recovery)). The SYSTEM identity cannot log in. The first administrator is created on the server with a console command, so no page exists for it (see [First administrator](../database/database-design.md#first-administrator)).
- **A disabled account** is told so on the login page. When the password is right and the account is disabled, the login answers `ACCOUNT_DISABLED` and gives no token: the page stays where it is and shows "This account has been disabled. Please contact an administrator." When any later request answers `ACCOUNT_DISABLED`, the account was disabled while the user was logged in: the frontend clears its login state, goes back to the login page, and shows the same message. After the account is enabled again, the user logs in again (see [Disabled accounts](../database/database-design.md#login-security-and-account-recovery)).
- **Sign-up** takes a username, an email, and a password. The page sends a 6-digit code to the email, and the account is created when the code is entered. The new account has no role. The sign-up logs the user in, so the page goes straight to the pending page, which says that the account is waiting for an administrator to assign permissions. A username is 3 to 64 letters and digits, with no space and no other character.
- **Forgot password** takes the email, sends a code, and then takes the new password. The reply is the same whether or not the email is registered. A new code can be requested after 60 seconds.
- **The code, on both pages.** On the sign-up page, a request for a code for an address that already has an account answers `EMAIL_TAKEN`, and the page says "This email is already registered." with links to the login page and to Forgot password. On Forgot password, the page always says that a code was sent if the address has an account, because the server gives the same answer whether or not a mail went out. The button for a new code is disabled for 60 seconds. On `TOO_MANY_REQUESTS` the page says "Too many requests. Please try again in a minute.", and on `CODE_INVALID` "The code is wrong or no longer valid. Please request a new one." On `USERNAME_TAKEN` the page keeps the code and asks for another username. A new password that equals the current one is refused with a message on that field.
- The email and the username cannot be changed. There is no account page. A person changes the password only through Forgot password on the login page.

### Pending page

Every user passes it after login. If the user has no role, it says "Waiting for an administrator to assign permissions". Otherwise it is skipped. It is checked again on refresh and on the next login. A disabled account never sees it, because it gets no token: its message is on the login page.

### Product page

The home page for every role.

- It lists the products that are `LISTED`, ordered by name. Each product shows its image (or nothing), its name, and its price, and nothing else. There is a search box for the name. When nothing is listed, it says so.
- **Order** (operator, manager, administrator): a button on each product opens a dialog with a quantity that starts at 1 and changes by typing or with plus and minus. Confirming shows the result, always with an order number, whether the order was placed or refused. The reasons for a refusal are: the product was delisted, it changed, not enough stock, the quantity is not valid, not allowed, or busy. The number can be read again in the order records of the product log, and is what a ticket about an order names, a refused order included. The stock is never shown.
- **View** (every role): a button on each product opens the own log of that product, with its listing records and its order records in two parts (see Logs).
- **View records** (every role): a button on the page that opens the product log of all products (see Logs).
- **How many products a page shows** is chosen with three icons at the end of the search row: 8 large cards (one square), 16 (four squares, the default), or 24 small ones (nine squares). The one that is on is filled blue, and a new choice starts at page 1. The columns divide the page at every width, so a full page fills its rows.
- **Prices are shown in Australian dollars**, with two decimals: `$12.50` (the owner's decision, 2026-11-15). Only the display has the sign: the API and the database hold the number, so another currency is a change in one place of the frontend (`format.ts`). The price field of List item shows the sign before the number.
- **List and Delist** (manager, administrator): List opens a catalog of the warehouse items that are not listed, searched by name only, then asks for a price above zero and shows the name, which can be changed. Delist takes no reason. See [Listing and delisting](../database/database-design.md#listing-and-delisting).

### Stock page

One row for each warehouse item that is not deleted, ordered by name, with a search box for the name, as on the product page. From left to right:

1. the image;
2. the name;
3. the stock quantity;
4. the button for changing the quantity;
5. the View button for the own log of that item.

A **View records** button at the top of the page opens the warehouse log of all items (see Logs). A manager or an administrator also has buttons to add an item and to delete an item.

- **Changing the quantity.** An operator, a manager, or an administrator chooses one of three: a plus (stock in), a minus (write-off), or **Correction**, a field to type the quantity that was counted, for a stock count that differs from the number shown. The reason is required for all three, and Save is enabled only when the quantity has changed and the reason is filled in. See [Manual stock changes](../database/database-design.md#manual-stock-changes).
- **Adding an item** takes a name, an optional image, and an optional starting quantity. The SKU is generated. **Deleting an item** takes no reason and does not touch the stock. See [Warehouse items](../database/database-design.md#warehouse-items) and [Item images](../database/database-design.md#item-images).
- **Editing an item** (manager, administrator) changes its name or its image, and is offered only while the item is not on sale. Each row says whether its item is on sale, so the page knows when to offer Edit, and a manager sees why it is missing.
- **Built in two steps** (the owner's decision, 2026-11-15). Phase 4 built the rows (the image, the name, the SKU, the stock quantity, and whether the item is on sale), the search, the paging, and adding, editing, and deleting an item. Phase 5 built Adjust quantity, the View buttons, and the logs.
- **Adjust quantity** is one dialog with a segmented control of the three changes, each offered only with its permission (`STOCK_IN`, `STOCK_WRITE_OFF`, `STOCK_CORRECT`). It shows the stock before and after as the person types. A write-off larger than the stock shown, and a correction equal to it, are refused under the field. When the server finds that the stock changed (`STOCK_CHANGED`, or `OUT_OF_STOCK` for a write-off), and after a failure whose result is unknown, the dialog offers Reload item: it reads the item again and asks for the change again. A stock change has no idempotency key, so the page never sends one twice by itself.

### Logs

The two pages that keep logs work the same way. The product page has the product log, and the stock page has the warehouse log. Everyone who can see the page can read its log, and nothing in a log can be changed. See [Logs](../database/database-design.md#logs).

**Three ways in, on each page:**

1. **All records.** The View records button asks which of two kinds to read, then shows that kind for all products (or all items), newest first.
   - Product page: **listing records** (a product was listed or delisted) or **order records** (every attempt to order, placed or refused).
   - Stock page: **item records** (an item was added or deleted) or **stock movements** (orders, stock in, write-offs, and manual corrections), or the **Deleted items** module: a list of the deleted items, newest deletion first, with one box that searches by name or SKU. A row opens the own log of that item (item records, stock movements, and the listing and order records of its product, if it had one). It is the only place where a deleted item can be searched for.
2. **One chosen.** Inside that view, a searchable picker (see below) narrows the records to one product or item, and the time filter (see below) narrows them by time. The order records and the stock movements also have a box for one whole order number, which finds that order's lines or its stock movements. See [Finding by order number](../database/database-design.md#logs). The order records of a manager, an administrator, or a viewer also have an **Ordered by** filter, the user picker with "Everyone" when nothing is chosen, which works with the product and the time filter and is disabled while an order number is applied.

   **An operator sees only the orders they placed** (Q-34, 2026-11-18), in the list of all order records, in the own log of a product, and in the search by order number, as on the ticket screens. The list has no Ordered by filter and says "You see only the orders you placed." under the filters. The stock movements show every sale to every role. See [Who sees which order records](../database/database-design.md#logs).
3. **The own log of one.** The View button on a row opens the log of that product or item alone, with both kinds of record in two parts. It has the time filter and no other filter. A delisted product and a deleted item are no longer rows on their pages, and a deleted item is not offered by the picker either, so the records of a deleted item are found in the lists of all records and in the Deleted items module.

**Columns:**

| Record | Columns |
| --- | --- |
| Listing record | Time, product, listed or delisted, the name and price at that moment, who did it |
| Order record | Time, product, order number, who ordered, quantity, result (placed, delisted, changed, out of stock, invalid quantity, busy), stock deducted |
| Item record | Time, item, added or deleted, name and SKU at that moment, quantity, who did it |
| Stock movement | Time, item, type, order number (for an order), reason (empty for an order), amount (plus or minus), who did it |

An operator who needs the number of an order that failed opens the order records, filters by the product and the day, and copies the number into the ticket form (see Create ticket). Every list is paged with two buttons, 10 rows a page, and shows the number of its page between them, centred at the foot of the list.

**The time filter.** One template is used wherever a list is filtered by time: the two logs, the own log of one product or item, the search on the ticket screens, and the audit log page. The person picks either a start day and an end day (both included, the end day today at the latest), or a start day alone, which means from that day until now. Beside it, the person chooses newest first (the default) or oldest first. A **quick range** fills both days at once: Last 1 day, 3 days, 7 days, 15 days, 1 month, or 3 months. The end day is today in `Australia/Sydney`. For N days the start day is today minus N−1 days, so Last 1 day is today from midnight. For months, the start day is the same day 1 or 3 months earlier (the last day of that month when it is shorter), plus one day: on 15 November, Last 1 month starts on 16 October. Changing either day by hand clears the quick range. Days after today cannot be picked. The quick range is worked out in the page and sent as the two days, so the server needs nothing new. See [The time filter](../database/database-design.md#the-time-filter).

**How the logs are built** (the owner's review of the Figma design, 2026-11-16):

- **Each list is a page of its own**, with an address that keeps its filters, so that a reload, Back, or a link shows the same records: `/products/records/listing`, `/products/records/orders`, `/stock/records/items`, `/stock/records/movements`, `/stock/records/deleted`, the own log `/products/{id}/records` and `/stock/{id}/records`, and a deleted item `/stock/records/deleted/{sku}`. View records opens a dialog that asks which kind; Deleted items is offered only with `DELETED_ITEMS_VIEW`.
- **The own log** shows its parts as tabs, the one read most first (the owner's choice): a product opens on its order records, then its listing records; an item on its stock movements, then its item records; a deleted item on its stock movements, item records, order records, then listing records. The time filter and the direction stay when the tab changes.
- **The order number box** takes `ORD-` and 32 letters or digits in any case, and writes it as the numbers are made. While a number is applied, the picker and the time filter are disabled; Clear order number gives them back as they were.
- **On a small screen** each record is a stack of its labelled values.

**The searchable picker.** One shared control is used wherever a screen asks for a product or an item. It is a dropdown list sorted alphabetically by name, with a scroll bar when it is long, and a text box in it that narrows the list as the person types. The product picker offers every product ever listed except those of deleted items, and the item picker every item that is not deleted, with the SKU shown beside the name so that equal names can be told apart. A deleted item is offered nowhere.
### Ticket screens

There is one screen for each stage, and each ticket carries a status label.

| Screen | Status | Label |
| --- | --- | --- |
| Pool | `OPEN` | Waiting to be taken |
| In progress | `IN_PROGRESS` | In progress |
| Resolved | `RESOLVED` | Resolved |
| Archive | `CLOSED` | Archived |
| Cancelled | `CANCELLED` | Cancelled |

- **A ticket that is taken leaves the pool** for every role at once, like a commission that someone accepted, and appears on the In progress screen.
- **A list row** shows the ticket number, the type, the title, the person and the time of the step the screen is about, and the status label (the owner's decision, 2026-11-19). The rest is in the detail view.

  | Screen | Person column | Time column |
  | --- | --- | --- |
  | Pool | Created by | Created |
  | In progress | Assignee: the person who holds it now, whom a manager assigned or reassigned it to, or who took it | Assigned: when they got it |
  | Resolved | Resolved by | Resolved |
  | Archive | Archived by (System for the archive job) | Archived |
  | Cancelled | Cancelled by (System when the system cancelled it) | Cancelled |
- **Order.** By the time the row shows, the newest first by default, switchable to the oldest first. Tickets opened by the system are always shown before tickets opened by a person.
- **Search** (manager, administrator, viewer; not operator). Each screen searches only its own status. The filters are the kind (system or manual), the creator, the assignee, and the days of the time the screen shows (the time filter of Logs): the creation days in the pool, the days the tickets were taken or assigned in progress, and so on, and a ticket can also be found by its number, or by an order number it names, and nothing else. The creator and the assignee are chosen from a picker that shows usernames only. See [Ticket search](../database/database-design.md#ticket-search) and [The user picker](../database/database-design.md#the-user-picker).
- **What an operator sees:** the whole pool, and on the other screens only their own tickets, so an operator has no search. The operator has no archive screen. See [Ticket screens and who sees what](../database/database-design.md#ticket-screens-and-who-sees-what).
- **How it is built** (2026-11-16): one address for each screen (`/tickets/pool`, `/in-progress`, `/resolved`, `/archive`, `/cancelled`), with the search kept in the address. A system ticket has a light blue row. The search bar starts with Search by: Filters, Ticket number, or Order number; the two numbers are used alone, and the filters are disabled while one is chosen. The Assignee filter is left out of the pool, where no ticket has an assignee. A user without the search has only the direction. On Resolved, a user who may archive chooses tickets with a checkbox, up to 100 across the pages, and Archive selected asks a second confirmation that names how many; the result lists the archived numbers and each skipped number with its status now. Since 2026-11-17 the filters include one warehouse item, chosen from the item picker; it combines with the others.

### Ticket detail

Opened by clicking a row. It shows the number, the type, the title, the description, the status, the creator, the assignee, the order if there is one, the linked items, the ticket it follows and the tickets that follow it, the history of assignees, and the resolution or the cancel reason when there is one. There are no comments.

The buttons depend on the status and on the role:

| Button | Who | Needs |
| --- | --- | --- |
| Take | An operator, a manager, or an administrator, on an `OPEN` ticket | Under 99 tickets in progress |
| Assign | A manager or an administrator, on an `OPEN` ticket | An eligible assignee, chosen from the user picker: an operator or a manager, and an administrator only when an administrator assigns; and a reason |
| Reassign | A manager or an administrator, on an `IN_PROGRESS` ticket | A new assignee from the same picker, and a reason |
| Release | The assignee, or a manager or an administrator | A reason |
| Resolve | The assignee, or a manager or an administrator | A resolution text |
| Cancel | A manager or an administrator for any ticket, an operator for their own | A reason |
| Archive | A manager or an administrator, on a `RESOLVED` ticket, singly or in a batch | |

- **How it is built** (2026-11-16): the detail is a page of its own (`/tickets/{id}`): the content on the left, the information on the right, and the assignment history below. The buttons follow the permissions and the ticket: Cancel in the pool only for its creator or with `TICKET_HANDLE_ANY`, and Resolve, Release, and Cancel in progress only for the assignee or with `TICKET_HANDLE_ANY`. Each action other than Take and Archive opens a dialog that is its own confirmation. A refusal keeps what was typed: a ticket that changed offers Reload ticket, and a ticket that left what the person may see offers Back to list. A linked ticket the person may not see shows its number without a link.

### Create ticket

A Create ticket button on the pool screen opens a page of its own (`/tickets/new`), so that up to 20 chosen items and a long description stay in view and the field errors stay visible (the owner accepted Codex's proposal on 2026-11-16). The form works in this order:

1. choose 0 to 20 warehouse items from a list searched by name;
2. choose the type, and write the title (1 to 200 characters) and the description (1 to 4,000, with a counter);
3. optionally type the number of an earlier ticket to make this one a follow-up;
4. optionally type the number of an order (required for an order exception; the number comes from the result the person saw when placing the order, or from the order records of the product log, and it may be the number of a refused order);
5. confirm.

The result is shown on the form: the ticket was opened (with its number), no ticket or order attempt has that number, the earlier ticket cannot be followed yet, an item no longer exists, the person already has 99 open tickets, not allowed, or busy. On failure the typed details are kept; when an item no longer exists, the page names it and Reload item list takes it out of the chosen items. The order number is accepted in any case, and the follow-up number is shown in capitals. See [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items).

When a product is delisted or an item is deleted, the tickets linked to it are cancelled by the system. The cancelled screen shows a message built from the reason, for example "Widget has been delisted, please open the ticket again".

### User page

For managers and administrators. It lists only the users the viewer can manage: all users for an administrator, and for a manager the users who hold neither the manager nor the administrator role. Users with no role and disabled users are listed. The list has a search box for the username and a row of role buttons, of which several can be chosen: one for each role the viewer may manage, and **No role** for the users who are waiting for one. An administrator has Administrator, Manager, Operator, Viewer, and No role. A manager has Operator, Viewer, and No role. A user can have roles granted or revoked, be disabled or enabled, and be deleted. A change of roles that leaves the user no role that may take tickets sends their tickets in progress back to the pool: the second confirmation says how many first ("alice has 2 tickets in progress and will no longer be able to take tickets. They will go back to the pool."), and the message after the change says how many went. Disable and Delete warn in the same way in their confirmation, from the number of tickets in progress on the row. A second row of buttons narrows the list by status: Active, Disabled, and Deleted; a deleted user is listed only with Deleted, with the tag Deleted and no control (2026-11-17). The roles on offer are those the viewer may grant, and a user may hold several. Nobody can disable or delete their own account or change their own roles: an administrator sees their own row without those controls, and a manager does not see their own row. The four roles are fixed, so there is no page for creating or changing roles. See [User page](../database/database-design.md#user-page).

### Audit log page

For managers and administrators. The page opens on a menu of seven areas: Accounts, Roles and permissions, Tickets, Stock, Warehouse items, Products, and Orders. Choosing one shows a read-only list of its records, newest first, 10 to a page, filtered by three independent things: the action (only the actions of that area; for Orders, the result), the time (the time filter of Logs), and the user, chosen from the user picker, which also offers people whose accounts are disabled or deleted, or as System. The Orders area shows the same records as the order records of the product log, so a role that cannot open the audit page, such as `VIEWER`, still finds them on the product page. A system action shows the user as System. See [Audit log page](../database/database-design.md#audit-log-page).

How a row is laid out (Q-34, decided on 2026-11-16 with the Figma design): five columns, the time, the action, the user (System for the system), the target by its label (the ticket number, the username, or the name and SKU), and a summary of what changed, such as "Active → Disabled", "olive → bob", "10 → 15 (+5)", or "Stock at deletion: 9 · Cancelled tickets: K7Q2M9XA". View opens the fields before and after, by their labels, with the reason and the address the request came from; an id is never shown. The Orders area shows the result, the person, the order number, and "A4 paper · 3 ordered · 3 deducted". Each area has its own address (`/audit/tickets`), and the filters are kept in it. The Accounts and the Roles and permissions areas have one more filter, About user, for what was done to one account (2026-11-17). The actions of the Tickets area tell a take from an assignment (2026-11-19): Ticket taken, whose summary is "Taken from the pool" and whose user is the person who took it, and Ticket assigned, whose summary is "Assigned to olive" and whose user is the manager who assigned it. The Tickets area has one more filter, Ticket number (2026-11-19): typed in any case and applied with Find or Enter, it shows the rows of that ticket, and with the action and the user it finds one step of one ticket, such as who reassigned `K7Q2M9XA`, when, and why (View shows the reason). A number that is not 8 letters and digits shows "Enter an 8-character ticket number." under the box, and clearing the box takes the filter off. Every row has a reason, shown when the row is opened: the one a person typed, or a fixed one that the system wrote; a row of a change that returned tickets to the pool names them in its summary.

## Open Details

These are not designed yet. Each is a question for the UI design, or for the owner.

None is open now. The last one, whether the order records can be filtered by who ordered, was decided on 2026-11-18 (see Logs and [frontend-v1.12.md](../iterations/frontend-v1.12.md)).

Settled on 2026-11-15: the font is the system font, the navigation is a menu on the left that moves into a drawer below 768 px, and a list on a small screen is stacked (see [frontend-v1.5.md](../iterations/frontend-v1.5.md)).
