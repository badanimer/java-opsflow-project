# OpsFlow Page Design

## Purpose

This document collects the page design of OpsFlow: which pages exist, who sees them, what each one shows, and what it does. It is the starting point for the UI design, and it records the owner's ideas while they are still rough. It is a first version. The visual design (layout, colour, components) is not started, and the frontend framework is not chosen (T-18).

The pages are designed backwards from the database: the screens decide what the database must be able to answer. The rules that reach into the database or the service layer are in [Screens and Lists](../database/database-design.md#screens-and-lists), and the rules of each flow are in [database-design.md](../database/database-design.md). This document does not repeat them in full. Where a rule matters to a page, it is summarized here with a link.

## Change Log

This document always describes the current page design. Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-10-26 | 1.0 | First version: the page map, what each role sees, and a first description of every page, built from the earlier design and from the owner's rough design of the pages | [frontend-v1.0.md](../iterations/frontend-v1.0.md) |

## Principles

- **The first page is the product page.** After login every user with a role lands on it, even when no product is listed.
- **An order is an action, not a page.** There is no order list and no order page. A person with the right orders from a product, chooses a quantity, and gets a result with an order number. Every attempt is recorded, successful or not, and is read in the product log of the item.
- **The warehouse comes first.** The stock page is where items are added, removed, and corrected. The product page only shows the items that are on sale.
- **Each role sees only what it needs.** An operator sees only its own tickets after the pool. A viewer reads everything that a manager reads, and changes nothing.
- **Lists have two buttons.** Previous and next, 10 rows by default, the page sized to the layout. No page numbers, no total (see [Home page and paging](../database/database-design.md#home-page-and-paging)).
- **Times are shown in the display time zone** (`Australia/Sydney`) and stored in UTC.
- **No notifications, no comments, no dashboard** in the first version. A person finds out about a change by looking at the screen.

## Page Map

| Page | Who sees it | What it is for |
| --- | --- | --- |
| Login | Everyone | Log in |
| Sign-up | Everyone | Create an account with an email code |
| Forgot password | Everyone | Set a new password with an email code. The only way to change a password |
| Pending page | A user with no role, or a disabled account | Shows why nothing else is open |
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

**Not planned for the first version:** an order list or order page, a page for role definitions (done through the backend), an account or profile page, a page for creating the first administrator (done with a console command on the server), comments on tickets, a notification centre, and a dashboard.

## What Each Role Sees

| Page or part | `ADMIN` | `MANAGER` | `OPERATOR` | `VIEWER` |
| --- | --- | --- | --- | --- |
| Product page | Yes, with List and Delist | Yes, with List and Delist | Yes, with Order | Yes, read only |
| Order button | Yes | Yes | Yes | No |
| Stock page and the warehouse log | Yes | Yes | Yes | Yes |
| Product log (on the product page) | Yes | Yes | Yes | Yes |
| Stock in and write-off (plus and minus) | Yes | Yes | Yes | No |
| Setting the quantity directly | Yes | Yes | No | No |
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

The full rules are in the [Permission Matrix](../database/database-design.md#permission-matrix).

## Pages

### Login, sign-up, and Forgot password

Ordinary pages, in the usual style of a web application. There are three functions and no more.

- **Login** takes a username and a password. An error never says whether the username exists. After five wrong passwords the account is locked for a time, and the page says when to try again (see [Login security](../database/database-design.md#login-security-and-account-recovery)). The SYSTEM identity cannot log in. The first administrator is created on the server with a console command, so no page exists for it (see [First administrator](../database/database-design.md#first-administrator)).
- **Sign-up** takes a username, an email, and a password. The page sends a 6-digit code to the email, and the account is created when the code is entered. The new account has no role.
- **Forgot password** takes the email, sends a code, and then takes the new password. The reply is the same whether or not the email is registered. A new code can be requested after 60 seconds.
- The email and the username cannot be changed. There is no account page. A person changes the password only through Forgot password on the login page.

### Pending page

Every user passes it after login. If the account is disabled, it says so. If the user has no role, it says the account is waiting for an administrator to assign permissions. Otherwise it is skipped. It is checked again on refresh and on the next login.

### Product page

The home page for every role.

- It lists the products that are `LISTED`, ordered by name. Each product shows its image (or nothing), its name, and its price, and nothing else. There is a search box for the name. When nothing is listed, it says so.
- **Order** (operator, manager, administrator): a button on each product opens a dialog with a quantity that starts at 1 and changes by typing or with plus and minus. Confirming shows the result, always with an order number, whether the order was placed or refused. The reasons for a refusal are: the product was delisted, it changed, not enough stock, the quantity is not valid, not allowed, or busy. The number can be read again in the order records of the product log, and is what a ticket about an order names, a refused order included. The stock is never shown.
- **View** (every role): a button on each product opens the own log of that product, with its listing records and its order records in two parts (see Logs).
- **View records** (every role): a button on the page that opens the product log of all products (see Logs).
- **List and Delist** (manager, administrator): List opens a catalog of the warehouse items that are not listed, searched by name only, then asks for a price above zero and shows the name, which can be changed. Delist takes no reason. See [Listing and delisting](../database/database-design.md#listing-and-delisting).

### Stock page

One row for each warehouse item that is not deleted. From left to right:

1. the image;
2. the name;
3. the stock quantity;
4. the button for changing the quantity;
5. the View button for the own log of that item.

A **View records** button at the top of the page opens the warehouse log of all items (see Logs). A manager or an administrator also has buttons to add an item and to delete an item.

- **Changing the quantity.** An operator sees a plus and a minus and a field for the reason. A manager or an administrator also gets a field to type the new quantity. The reason is required, and Save is enabled only when the quantity has changed and the reason is filled in. See [Manual stock changes](../database/database-design.md#manual-stock-changes).- **Adding an item** takes a name, an optional image, and an optional starting quantity. The SKU is generated. **Deleting an item** takes no reason and does not touch the stock. See [Warehouse items](../database/database-design.md#warehouse-items) and [Item images](../database/database-design.md#item-images).

### Logs

The two pages that keep logs work the same way. The product page has the product log, and the stock page has the warehouse log. Everyone who can see the page can read its log, and nothing in a log can be changed. See [Logs](../database/database-design.md#logs).

**Three ways in, on each page:**

1. **All records.** The View records button asks which of two kinds to read, then shows that kind for all products (or all items), newest first.
   - Product page: **listing records** (a product was listed or delisted) or **order records** (every attempt to order, placed or refused).
   - Stock page: **item records** (an item was added or deleted) or **stock movements** (orders, stock in, write-offs, and manual corrections), or the **Deleted items** module: a list of the deleted items, newest deletion first, with one box that searches by name or SKU. A row opens the own log of that item (item records, stock movements, and the listing and order records of its product, if it had one). It is the only place where a deleted item can be searched for.
2. **One chosen.** Inside that view, a searchable picker (see below) narrows the records to one product or item, and a range of days, from one day to another, narrows them by time. The order records and the stock movements also have a box for one whole order number, which finds that order's lines or its stock movements. See [Finding by order number](../database/database-design.md#logs).
3. **The own log of one.** The View button on a row opens the log of that product or item alone, with both kinds of record in two parts. A delisted product and a deleted item are no longer rows on their pages, and a deleted item is not offered by the picker either, so the records of a deleted item are found in the lists of all records and in the Deleted items module.

**Columns:**

| Record | Columns |
| --- | --- |
| Listing record | Time, product, listed or delisted, the name and price at that moment, who did it |
| Order record | Time, product, order number, who ordered, quantity, result (placed, delisted, changed, out of stock, invalid quantity, busy), stock deducted |
| Item record | Time, item, added or deleted, name and SKU at that moment, quantity, who did it |
| Stock movement | Time, item, type, order number (for an order), reason (empty for an order), amount (plus or minus), who did it |

An operator who needs the number of an order that failed opens the order records, filters by the product and the day, and copies the number into the ticket form (see Create ticket). Every list is paged with two buttons, 10 rows a page.

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
- **Search** (manager, administrator, viewer; not operator). Each screen searches only its own status. The filters are the kind (system or manual), the creator, the assignee, and a range of creation days, and a ticket can also be found by its number, or by an order number it names, and nothing else. See [Ticket search](../database/database-design.md#ticket-search).
- **What an operator sees:** the whole pool, and on the other screens only their own tickets, so an operator has no search. The operator has no archive screen. See [Ticket screens and who sees what](../database/database-design.md#ticket-screens-and-who-sees-what).

### Ticket detail

Opened by clicking a row. It shows the number, the type, the title, the description, the status, the creator, the assignee, the order if there is one, the linked items, the ticket it follows and the tickets that follow it, the history of assignees, and the resolution or the cancel reason when there is one. There are no comments.

The buttons depend on the status and on the role:

| Button | Who | Needs |
| --- | --- | --- |
| Take | An operator, a manager, or an administrator, on an `OPEN` ticket | Under 99 tickets in progress |
| Assign | A manager or an administrator, on an `OPEN` ticket | An eligible assignee |
| Reassign | A manager or an administrator, on an `IN_PROGRESS` ticket | A new assignee and a reason |
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

For managers and administrators. It lists only the users the viewer can manage: all users for an administrator, and for a manager the users who hold neither the manager nor the administrator role. Users with no role and disabled users are listed. A user can have roles granted or revoked, be disabled or enabled, and be deleted. The roles on offer are those the viewer may grant. There is no page for creating or changing roles. See [User page](../database/database-design.md#user-page).

### Audit log page

For managers and administrators. The page opens on a menu of seven areas: Accounts, Roles and permissions, Tickets, Stock, Warehouse items, Products, and Orders. Choosing one shows a read-only list of its records, newest first, 10 to a page, filtered by three independent things: the action (only the actions of that area; for Orders, the result), the time, and the user. The Orders area shows the same records as the order records of the product log, so a role that cannot open the audit page, such as `VIEWER`, still finds them on the product page. A system action shows the user as System. See [Audit log page](../database/database-design.md#audit-log-page). How a row is laid out is decided later.

## Open Details

These are not designed yet. Each is a question for the UI design, or for the owner.

- The visual design: layout, colour, components, and the navigation between pages.
- Where the page for the log of one item opens (a dialog or a page), and what the dialog for changing a quantity looks like in detail.
- How the user is picked in the ticket filters (a typed username is the first idea).
- The look of the audit log filters, and how each log row is shown (Q-34).
- The empty states and the error messages of each page.
- How the choice between the two kinds of record, the picker, the day range, and the two parts of the own log are laid out.
- Whether an operator can filter the order records by who ordered, to find their own orders faster.
- Whether the stock page also shows whether an item is listed.
- Mobile and small-screen behaviour.
- The frontend framework (T-18).
