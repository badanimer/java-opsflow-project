# OpsFlow Future Ideas

## Purpose

This file keeps rough ideas for features that can be added after OpsFlow is complete. It is not a plan, and it is not a task list. Nothing here is in the current scope, and nothing here is promised. The open work of the current scope is in [project-todo.md](project-todo.md).

## Rules

- **Only features for after the program is complete** belong here. A change to the current design goes through the normal documents, not through this file.
- **An idea is added only after the owner approves it.** An assistant may suggest an idea, but does not write it into this file until the owner says yes.
- **Keep each idea short**: what it is, why it might help, and which roles it concerns. Details are worked out when the owner decides to build it.
- **When an idea is chosen for building**, it leaves this file. It becomes a confirmed task in [project-todo.md](project-todo.md) and enters the design documents through an iteration, as described in [documentation-sync-rules.md](documentation-sync-rules.md).
- This file is not recorded in the Project Log, because it is a working list and not a design iteration.
- All documentation is written in English.

## Ideas

### Approval module for managers and administrators

- **What:** a separate module where a `MANAGER` or an `ADMIN` handles approvals of different types, and other things that are waiting for them.
- **Why it might help:** today the actions that could need an approval are direct decisions by a manager or an administrator, for example archiving, reassigning, or cancelling a ticket. A module would give those decisions one place, and a record of who approved what.
- **Roles:** `MANAGER` and `ADMIN`.
- **Where it came from:** moved here from the open question Q-21 in [project-todo.md](project-todo.md). The ticket design does not depend on it.

### To-do list for each role

- **What:** a to-do list that shows each identity what it needs to handle next, so that different roles are reminded of different things.
- **Why it might help:** the first version has no notification system, because the flow is simple and each person is expected to know the next step. A to-do list would be the first reminder mechanism, and it would work well together with the approval module above.
- **Roles:** all roles that work in the system. The content differs by role.
- **Examples, not decided:** an `OPERATOR` sees the tickets they hold. A `MANAGER` or an `ADMIN` sees the approvals waiting for them and the tickets waiting to be archived.

### Comments on tickets

- **What:** a way for people to write notes on a ticket while it is worked on: investigation notes, questions, and corrections of earlier notes.
- **Why it might help:** in the first version a ticket carries only its description and the resolution written at the end, so a handler cannot record progress or ask a question in the program.
- **Roles:** the roles that work on tickets write; everyone who can see the ticket reads.
- **Where it came from:** the owner removed comments from the first version. The comment table of the earlier design (append-only, with a correction pointing at the original note) was dropped from the schema, and would come back with a new migration.

### Notification system

- **What:** a way to tell a person that something concerns them, for example that a ticket they opened or held was cancelled by the system.
- **Why it might help:** the first version has no notification, so a person finds out only by looking at the screen (Q-28).
- **Roles:** all roles that work in the system.
- **Where it came from:** requested by the owner. It would work together with the to-do list below.

### A paging system

- **What:** page numbers, a jump to a page, a total count, and a choice of page size on the lists.
- **Why it might help:** the first version has two buttons, previous and next, with 10 rows a page, which is enough while the lists are short.
- **Roles:** every role that reads a list.
- **Where it came from:** requested by the owner while the page design was written.

### A fuller user system

- **What:** a richer account system on top of the first version: changing the username or the email address, a profile page, and similar account features.
- **Why it might help:** in the first version a user cannot change the username or the email, and the only account change is a new password through Forgot password. Real use will probably want more.
- **Roles:** every user for their own account; `ADMIN` and `MANAGER` for the accounts they manage.
- **Where it came from:** requested by the owner while the account rules were reviewed. The database already reserves `users.username_changed_at` and `users.email_changed_at` for it.

### Tags for warehouse items and products

- **What:** a tag system for warehouse items and products. A `MANAGER` or an `ADMIN` attaches short labels to an item, and a user can search and filter by them.
- **Why it might help:** the first version finds an item only by its name. Tags would let people group and find items without a fixed category or a fixed unit, and they are lighter than a category tree.
- **Roles:** `MANAGER` and `ADMIN` set tags. Every role that can see the product page can search by them.
- **Where it came from:** requested by the owner while the products part was designed. Categories, units of measure, and low-stock alerts were set aside for the first version and are not recorded here, because the owner has not approved them as ideas.

### A human check before a code is mailed

- **What:** a check that the request comes from a person and not from a script, such as Cloudflare Turnstile, on the sign-up page and on Forgot password, before the server sends an email code. The page shows the check, and the server confirms its result with the provider before it stores a code.
- **Why it might help:** the first version limits code requests per client address (10 in an hour) and for the whole system (200 in a day). That bounds the damage, but a script can still use up the limit of the day, and then nobody gets a code until the next day. A human check stops scripts before they are counted.
- **Roles:** everyone who signs up or recovers a password, before they are logged in.
- **Where it came from:** approved by the owner on 2026-11-13, as the third of four answers to Q-07 for code requests; the other three (the limit per client address, the limit of the whole system, and no mail for an address that cannot use the code) are built. It needs a site key in the frontend, a secret key in the environment, and a call to the provider that must stay outside every transaction. It becomes worth building when OpsFlow is open to people outside the company.
