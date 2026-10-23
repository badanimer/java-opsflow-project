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
- **Examples, not decided:** an `OPERATOR` sees the tickets they hold and the orders waiting for them. A `MANAGER` or an `ADMIN` sees the approvals waiting for them and the tickets waiting to be archived.

### Tags for warehouse items and products

- **What:** a tag system for warehouse items and products. A `MANAGER` or an `ADMIN` attaches short labels to an item, and a user can search and filter by them.
- **Why it might help:** the first version finds an item only by its name. Tags would let people group and find items without a fixed category or a fixed unit, and they are lighter than a category tree.
- **Roles:** `MANAGER` and `ADMIN` set tags. Every role that can see the product page can search by them.
- **Where it came from:** requested by the owner while the products part was designed. Categories, units of measure, and low-stock alerts were set aside for the first version and are not recorded here, because the owner has not approved them as ideas.
