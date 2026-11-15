# OpsFlow Page Design

## Purpose

This document collects the page design of OpsFlow: which pages exist, who sees them, what each one shows, and what it does. It is the starting point for the UI design, and it records the owner's ideas while they are still rough. It is a first version. The frontend stack is chosen, and the account pages, the frame of the application, and the user page are drawn and built (see Frontend Stack And Design Files); the other pages are not drawn yet.

The pages are designed backwards from the database: the screens decide what the database must be able to answer. The rules that reach into the database or the service layer are in [Screens and Lists](../database/database-design.md#screens-and-lists), and the rules of each flow are in [database-design.md](../database/database-design.md). This document does not repeat them in full. Where a rule matters to a page, it is summarized here with a link.

## Change Log

This document always describes the current page design. Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
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
- **Shared components**: tags, the filter bar, the paged list, the confirm dialog, the dialog that requires a reason, the result message, the time filter with its quick ranges, the user picker, and the searchable picker. The last four are for later pages.

Rules that the states follow:

- **The page checks a value before it sends it.** An empty field, a username or a password that breaks its rule, an address that is not an email address, and a code that is not 6 digits are shown under the field, in red, and nothing is sent. The server checks the same rules again. The message for a password is "Use 8 to 24 characters, with at least one letter and one digit."
- **Every "too many" message says "Please try again in a minute."**, for the lock of the login and for the limits on code requests alike, and names no time. This is the owner's wording. The real wait can be longer: the lock grows to 5 and 15 minutes and to midnight, and the limits on code requests last to the end of the hour or of the day. A person who tries again after a minute is then refused again with the same message.
- **While a request runs** the button is disabled and says what is happening ("Signing in…"), so a second click sends nothing.
- **A failure that is not the user's** has two messages: "Something went wrong" with the request id for `INTERNAL_ERROR` and `BUSY`, and "Cannot reach the server" when no answer came.
- **After a password reset** the user is sent to the login page, which says that the password was reset. **After `UNAUTHENTICATED` on any page** the login page says that the session has expired.
- **One signed-in device.** When the account signs in on another device, every signed-in page, the pending page included, shows the dialog "You have been signed out" at once. It cannot be closed; its one button, Sign in again, removes the token and opens the login page, which says "You were signed out because your account signed in on another device. If this was not you, reset your password." The page learns it from the event stream `GET /auth/session-events`, from `SESSION_REPLACED` on any request, and by asking for the account when the person comes back to the tab. When another tab of the same browser signed in again, no dialog is shown: the page loads the new account. See One signed-in device in the [database design](../database/database-design.md#login-security-and-account-recovery).

## Running the Frontend Locally

From `frontend/`, run `npm ci` to install the locked dependencies, then `npm run dev`. Open `http://localhost:5173`. Vite forwards `/api` to Spring Boot at `http://localhost:8080` by default. Set `OPSFLOW_API_TARGET` in the shell that starts Vite if the backend runs at a different address. The backend setup is described in [OpsFlow Backend](../backend/backend-design.md).

Run `npm test`, `npm run lint`, and `npm run build` from `frontend/` to check the project. The frontend tests substitute API responses. The owner clicked through the four pages against the running backend on 2026-11-14, signed up with a code from a real mail, and logged in. On 2026-11-15 the owner clicked through the frame of the application and the user page with every role, and signed in to one account on two browsers to see the first one signed out.

The project needs Node 20 or later. It uses Vite 5 and jsdom 25, because the newest versions of both need a newer Node than the 21.7 on the development machine; raise them together with Node.

| Path in `frontend/` | Content |
| --- | --- |
| `src/api` | `client.ts`, the one place that calls the backend, with `readEventStream` for the event stream; `auth.ts` and `users.ts`, the endpoints of Authentication and Users; `paging.ts`, the page and the cursors of every list |
| `src/auth` | The stored token (`session.ts`), the login state (`AuthProvider.tsx`, `context.ts`; it also keeps the event stream open and checks the account when the tab is shown again), the route guards (`guards.tsx`, with `RequirePermission`), and `permissions.ts`, the permission names and `can` |
| `src/pages` | The four account pages, the placeholder of the product page, Not allowed and Page not found (`StatusPages.tsx`), what they share (`useEmailCode.ts`, `failure.ts`), and `users/`: the user page, the Change roles dialog, the tags, and `userRules.ts`, which decides from the permissions which controls a row shows and where a refusal is shown |
| `src/components` | The frame of the application (`AppLayout.tsx`, `navigation.tsx` with every page of the Page Map and its permission, `TopBar.tsx`, `PageHeader.tsx`), the frame of the account pages, the logo, the shared `ConfirmDialog.tsx` and `CursorPager.tsx`, and `SessionReplacedDialog.tsx` |
| `src/roles.ts` | The four roles as the interface names them, and which are protected |
| `src/text.ts`, `src/validation.ts` | Every text of the interface, and the checks a page makes before it sends a value |
| `src/test` | The set-up of the tests, the fake server, the screen width (`screen.ts`), and the users of the user page tests (`users.ts`) |
| `vite.config.ts` | The development proxy and the test configuration |

The tests are next to the code they test, in files that end in `.test.ts` or `.test.tsx`: `validation.test.ts`, `api/client.test.ts`, one for each of the four account pages, `components/AppLayout.test.tsx` for the navigation of each role and the pages that cannot be opened, `pages/users/UsersPage.test.tsx` and `UserChanges.test.tsx` for the user page and its four changes, and `auth/SessionReplaced.test.tsx` for one signed-in device. They open the whole application at a path and set the answers of the server themselves, so they need no backend. The answer of `GET /auth/me` in a test carries the permissions that the backend's `Role` gives, from a copy kept in `test/server.tsx`; the application itself has no such table. A test of a small screen sets the width in `test/screen.ts`. The fake server answers the event stream with one that stays open, and a test sends an event on it. The latest run, on 2026-11-15, had 143 tests in 10 files, all passed. GitHub Actions runs `npm ci`, the lint, the tests, and the build on every push, with Node 22, in [`.github/workflows/frontend-tests.yml`](../../.github/workflows/frontend-tests.yml). The job needs no backend and no secret.

Every page of a user with a role sits in the frame of the application. The navigation shows the entries of the user's permissions only, and an entry whose page is not built yet is shown disabled. A page whose permission the user lacks says Not allowed, and an unknown address says Page not found. The root route shows the placeholder of the product page. A response of `ACCOUNT_NO_ROLE` from a protected API call sends the signed-in user to Pending and keeps the token.

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
- gives or takes away a protected role (Administrator or Manager), or leaves a user with no role.

It is not asked for an action that is frequent and easily reversed, or where the dialog already makes the person type a reason or a quantity and shows what will change. Too many confirmations teach people to click through them.

How it looks: a dialog that names the target and says what will happen, with a confirm button that repeats the verb ("Delete", "Disable", never "OK"), in the danger style when the action removes something, and Cancel as the safe choice. While the request runs, both buttons are disabled.

| Action | Second confirmation | Why |
| --- | --- | --- |
| Delete a user | Yes | It cannot be undone |
| Disable a user | Yes | The person loses access at once |
| Enable a user | No | It gives back what was there, and Disable undoes it |
| Change roles, when the new set adds or removes Administrator or Manager | Yes, after Save, naming the change | It hands out or takes away the highest authority |
| Change roles, when the new set is empty | Yes, after Save | The user is sent to the pending page and can use nothing |
| Change roles, any other change | No | Save in the dialog is the confirmation |
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

### Logs

The two pages that keep logs work the same way. The product page has the product log, and the stock page has the warehouse log. Everyone who can see the page can read its log, and nothing in a log can be changed. See [Logs](../database/database-design.md#logs).

**Three ways in, on each page:**

1. **All records.** The View records button asks which of two kinds to read, then shows that kind for all products (or all items), newest first.
   - Product page: **listing records** (a product was listed or delisted) or **order records** (every attempt to order, placed or refused).
   - Stock page: **item records** (an item was added or deleted) or **stock movements** (orders, stock in, write-offs, and manual corrections), or the **Deleted items** module: a list of the deleted items, newest deletion first, with one box that searches by name or SKU. A row opens the own log of that item (item records, stock movements, and the listing and order records of its product, if it had one). It is the only place where a deleted item can be searched for.
2. **One chosen.** Inside that view, a searchable picker (see below) narrows the records to one product or item, and the time filter (see below) narrows them by time. The order records and the stock movements also have a box for one whole order number, which finds that order's lines or its stock movements. See [Finding by order number](../database/database-design.md#logs).
3. **The own log of one.** The View button on a row opens the log of that product or item alone, with both kinds of record in two parts. It has the time filter and no other filter. A delisted product and a deleted item are no longer rows on their pages, and a deleted item is not offered by the picker either, so the records of a deleted item are found in the lists of all records and in the Deleted items module.

**Columns:**

| Record | Columns |
| --- | --- |
| Listing record | Time, product, listed or delisted, the name and price at that moment, who did it |
| Order record | Time, product, order number, who ordered, quantity, result (placed, delisted, changed, out of stock, invalid quantity, busy), stock deducted |
| Item record | Time, item, added or deleted, name and SKU at that moment, quantity, who did it |
| Stock movement | Time, item, type, order number (for an order), reason (empty for an order), amount (plus or minus), who did it |

An operator who needs the number of an order that failed opens the order records, filters by the product and the day, and copies the number into the ticket form (see Create ticket). Every list is paged with two buttons, 10 rows a page.

**The time filter.** One template is used wherever a list is filtered by time: the two logs, the own log of one product or item, the search on the ticket screens, and the audit log page. The person picks either a start day and an end day (both included, the end day today at the latest), or a start day alone, which means from that day until now. Beside it, the person chooses newest first (the default) or oldest first. A **quick range** fills both days at once: Last 1 day, 3 days, 7 days, 15 days, 1 month, or 3 months. The end day is today in `Australia/Sydney`. For N days the start day is today minus N−1 days, so Last 1 day is today from midnight. For months, the start day is the same day 1 or 3 months earlier (the last day of that month when it is shorter), plus one day: on 15 November, Last 1 month starts on 16 October. Changing either day by hand clears the quick range. Days after today cannot be picked. The quick range is worked out in the page and sent as the two days, so the server needs nothing new. See [The time filter](../database/database-design.md#the-time-filter).

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
- **A list row** shows the ticket number, the type, the title, the creator, the creation time, and the status label. The rest is in the detail view.
- **Order.** The newest first by default, switchable to the oldest first. Tickets opened by the system are always shown before tickets opened by a person.
- **Search** (manager, administrator, viewer; not operator). Each screen searches only its own status. The filters are the kind (system or manual), the creator, the assignee, and the creation days (the time filter of Logs), and a ticket can also be found by its number, or by an order number it names, and nothing else. The creator and the assignee are chosen from a picker that shows usernames only. See [Ticket search](../database/database-design.md#ticket-search) and [The user picker](../database/database-design.md#the-user-picker).
- **What an operator sees:** the whole pool, and on the other screens only their own tickets, so an operator has no search. The operator has no archive screen. See [Ticket screens and who sees what](../database/database-design.md#ticket-screens-and-who-sees-what).

### Ticket detail

Opened by clicking a row. It shows the number, the type, the title, the description, the status, the creator, the assignee, the order if there is one, the linked items, the ticket it follows and the tickets that follow it, the history of assignees, and the resolution or the cancel reason when there is one. There are no comments.

The buttons depend on the status and on the role:

| Button | Who | Needs |
| --- | --- | --- |
| Take | An operator, a manager, or an administrator, on an `OPEN` ticket | Under 99 tickets in progress |
| Assign | A manager or an administrator, on an `OPEN` ticket | An eligible assignee, chosen from the user picker: an operator or a manager, and an administrator only when an administrator assigns |
| Reassign | A manager or an administrator, on an `IN_PROGRESS` ticket | A new assignee from the same picker, and a reason |
| Release | The assignee, or a manager or an administrator | A reason |
| Resolve | The assignee, or a manager or an administrator | A resolution text |
| Cancel | A manager or an administrator for any ticket, an operator for their own | A reason |
| Archive | A manager or an administrator, on a `RESOLVED` ticket, singly or in a batch | |

### Create ticket

A Create ticket button on the pool screen opens a form, in this order:

1. choose zero or more warehouse items from a list searched by name;
2. choose the type, and write the title and the description;
3. optionally type the number of an earlier ticket to make this one a follow-up;
4. optionally type the number of an order (required for an order exception; the number comes from the result the person saw when placing the order, or from the order records of the product log, and it may be the number of a refused order);
5. confirm.

The result is shown on the form: the ticket was opened (with its number), no ticket or order attempt has that number, the earlier ticket cannot be followed yet, an item no longer exists, the person already has 99 open tickets, not allowed, or busy. On failure the typed details are kept. See [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items).

When a product is delisted or an item is deleted, the tickets linked to it are cancelled by the system. The cancelled screen shows a message built from the reason, for example "Widget has been delisted, please open the ticket again".

### User page

For managers and administrators. It lists only the users the viewer can manage: all users for an administrator, and for a manager the users who hold neither the manager nor the administrator role. Users with no role and disabled users are listed. The list has a search box for the username and a row of role buttons, of which several can be chosen: one for each role the viewer may manage, and **No role** for the users who are waiting for one. An administrator has Administrator, Manager, Operator, Viewer, and No role. A manager has Operator, Viewer, and No role. A user can have roles granted or revoked, be disabled or enabled, and be deleted. The roles on offer are those the viewer may grant, and a user may hold several. Nobody can disable or delete their own account or change their own roles: an administrator sees their own row without those controls, and a manager does not see their own row. The four roles are fixed, so there is no page for creating or changing roles. See [User page](../database/database-design.md#user-page).

### Audit log page

For managers and administrators. The page opens on a menu of seven areas: Accounts, Roles and permissions, Tickets, Stock, Warehouse items, Products, and Orders. Choosing one shows a read-only list of its records, newest first, 10 to a page, filtered by three independent things: the action (only the actions of that area; for Orders, the result), the time (the time filter of Logs), and the user, chosen from the user picker, which also offers people whose accounts are disabled or deleted, or as System. The Orders area shows the same records as the order records of the product log, so a role that cannot open the audit page, such as `VIEWER`, still finds them on the product page. A system action shows the user as System. See [Audit log page](../database/database-design.md#audit-log-page). How a row is laid out is decided later.

## Open Details

These are not designed yet. Each is a question for the UI design, or for the owner.

- The visual design of the pages after Phase 3: products, stock, the logs, the ticket screens, and the audit log. The frame, the navigation, and the shared components they will use are drawn (see Frontend Stack And Design Files).
- Where the page for the log of one item opens (a dialog or a page), and what the dialog for changing a quantity looks like in detail.
- The look of the audit log filters, and how each log row is shown (Q-34). The user picker is drawn as a select with a typed search of usernames.
- The empty states and the error messages of the pages after Phase 3. Those of the user page are drawn and built.
- How the choice between the two kinds of record, the picker, the day range, and the two parts of the own log are laid out.
- Whether an operator can filter the order records by who ordered, to find their own orders faster.
- Whether the stock page also shows whether an item is listed.

Settled on 2026-11-15: the font is the system font, the navigation is a menu on the left that moves into a drawer below 768 px, and a list on a small screen is stacked (see [frontend-v1.5.md](../iterations/frontend-v1.5.md)).
