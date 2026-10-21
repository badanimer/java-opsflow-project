# Database Iteration v1.4

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.4 |
| Date | 2026-10-21 |
| Baseline | v1.3, see [database-v1.3.md](database-v1.3.md) |
| Type | Design rules and one schema change (one column on `users`) |
| Table count | 12 (unchanged) |
| Trigger | Open question Q-02: whether role grants to users with status `DISABLED` should also be refused. Working on it showed that creating, updating, deleting roles and replacing a user's roles were not defined either |

## Summary

Version 1.4 gives account status a defined meaning and completes the role lifecycle, without changing any table, field, or constraint.

- A user is a usable account only when `status` is `ACTIVE` and `deleted` is false. A role cannot be granted to a user who is not a usable account, and such a user has no effective permissions.
- A role can be created, deleted, and have its `name` and `description` updated. Roles themselves have no status.
- A user's roles can be replaced as a whole in one transaction.
- A user with no role is a valid state. New users start in it.
- At least one usable account must always hold `ADMIN`, with no override.
- The first administrator is created at startup from environment variables, and is forced to change the password at first login (`users.must_change_password`).
- Each of these operations writes an `operation_logs` row in the same transaction as the change, following the pattern of grants and revocations from v1.3.

## Changes

### Meaning of `status` and `deleted`

- The document previously described soft deletion as "`status` plus `deleted`", which suggested both fields decide whether a user is deleted. It now says that `deleted` marks soft deletion, and `status` is a separate field that says whether the account can be used now.
- When a user is soft-deleted, the service layer also sets `status` to `DISABLED`. This avoids a user who is deleted but still shows `ACTIVE`.
- A usable account is defined as `status = 'ACTIVE'` and `deleted = FALSE`.

### Role grants and effective permissions

- The v1.3 rule covered soft-deleted users only. The service layer must now refuse a grant to any user who is not a usable account, so `DISABLED` users are refused as well.
- A permission check counts a user's roles only when the user is a usable account.
- Disabling a user does not delete rows in `user_roles`. When the user is set back to `ACTIVE`, the existing roles take effect again.
- Revoking a grant is always allowed, including a grant held by a disabled user.
- A login token issued before an account was disabled must not keep working. The backend must either check the account on every request or keep tokens short-lived.

### Role management

- **Create**: inserts a `roles` row. `code` is unique.
- **Update**: only `name` and `description` can change. `code` never changes, because logs and permission checks refer to it.
- **Delete**: physical. Holders are not an obstacle: in the same transaction the service layer deletes every `user_roles` row of the role, writes one `ROLE_REVOKED` row for each (`role deleted` in `remark`), then deletes the role and writes `ROLE_DELETED`. Holders lose only this role; those left with no role get no default. The administrator sees the number of affected users before confirming. The foreign key stays `RESTRICT`, and the service layer deletes the grants itself, because a cascade would skip the log rows. The `ADMIN` role can never be deleted. Roles have no status.
- **Replace a user's roles**: one transaction. The user must be a usable account and every added role must exist. Unchanged roles are left alone and not logged. Each removed role writes `ROLE_REVOKED` and each added role writes `ROLE_GRANTED`, with `replace roles` in `remark`. An identical list changes nothing.

### Authority over roles and accounts

- Purpose of the two top roles: `ADMIN` is for the team that builds and maintains the system and is used only for major changes; `MANAGER` is the role handed to the customer's own staff to manage employees.
- Added the `MANAGER` role. Four roles are seeded: `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`. `ADMIN` is the highest authority and is meant for major changes; `MANAGER` does the daily work. This is a convention, not a restriction.
- `ADMIN` and `MANAGER` are protected roles: fixed codes, never deleted by anyone, `code` never changed. Every other role is below manager.
- Only `ADMIN` can create, update, or delete roles, and appoint or dismiss managers and administrators. `MANAGER` has no authority over role definitions.
- `MANAGER` can grant, revoke, and replace roles below manager, and manage users who hold neither `ADMIN` nor `MANAGER`. It cannot change any user who holds `ADMIN` or `MANAGER`, including its own account, so it cannot disable an administrator or another manager.
- Nobody can delete or edit audit logs. This was already true: the application account has only `SELECT` and `INSERT` on `operation_logs`, and the database enforces it.
- No schema change: "below manager" is decided by the two protected role codes, not by a rank column.

### Permission matrix

- Added a Permission Matrix to the design document: for each module (products, inventory, orders, tickets, audit logs, users, roles, own account) it says which of `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER` may do what. Deny by default; `VIEWER` is read-only; `OPERATOR` runs orders, stock receipts, and tickets; `MANAGER` adds master data, manual stock correction, user and role assignment, and audit logs; `ADMIN` adds what Authority over roles and accounts reserves for it.
- The matrix is a first version and is expected to be refined before the permission module is built.
- `VIEWER` is kept. It costs one seeded row and one matrix column, and it matches real read-only staff such as finance, management, and auditors. The line between it and `OPERATOR` is that `OPERATOR` makes things happen (orders, stock receipts, tickets) while `VIEWER` only reads. The "no role" state is not a substitute, because it can see nothing.
- The permissions of the four seeded roles are fixed in code, keyed by role code. A role created later has no permissions until a mapping is added, together with the code that needs it.
- Several entries are working defaults to review before the permission module is built: `OPERATOR` can cancel any order, can restock but not correct stock, and cannot reassign or close tickets.

### Users without roles

- A user with no `user_roles` row is valid, and the database does not require at least one role. OpsFlow is an internal program: apart from the first administrator, accounts are created through the normal sign-up flow. The user sets their own password, can log in at once, and starts with no role. Nothing is allowed until an administrator grants one.
- A permission check returns no permissions for such a user.
- After login, every user passes a pending page: a disabled account sees that it is disabled, an account with no role sees that it is waiting for permissions, and an account with a role skips the page. The check runs again on refresh and on the next login (T-22).

### Administrator protection

- At least one usable account must always hold `ADMIN`. The rule is fixed and has no override.
- Covered: disabling or soft-deleting a user, revoking `ADMIN`, replacing roles so that `ADMIN` is removed, and deleting a role. The `ADMIN` role itself can never be deleted, and its `code` never changes.
- The service layer locks the `ADMIN` row in `roles`, applies the change, counts usable holders, and rolls back if the count is zero. The lock stops two administrators from removing each other at the same moment.
- The rule is in the service layer, so direct SQL as `root` or the migration account bypasses it. A database trigger as a second layer was considered and rejected; see Decisions.

### First administrator and forced password change

- A new user has no role and only an administrator or a manager can grant one, and the first manager is appointed by an administrator, so the first administrator cannot be created through the program's normal flow. When no usable `ADMIN` exists, the program creates one at startup from the environment variables `OPSFLOW_BOOTSTRAP_ADMIN_USERNAME`, `OPSFLOW_BOOTSTRAP_ADMIN_EMAIL`, and `OPSFLOW_BOOTSTRAP_ADMIN_PASSWORD`. A missing variable stops the startup with a clear message. When an administrator already exists, the variables are ignored. The password is never written to a file.
- The creation is one transaction: hash the password, create the user, grant `ADMIN`, and write `ROLE_GRANTED` with an empty `operator_id`, a system action. It runs after the roles are seeded.
- Added `users.must_change_password`: `BOOLEAN NOT NULL DEFAULT FALSE`. Only the bootstrap administrator starts with it true. While it is true, the user can only change the password, and the application clears it afterwards. Other accounts set their own password at sign-up, so no password change is forced for them.
- `V1__initial_schema.sql` was edited in place, because it has not been applied to any database yet (T-01 is still open). After Flyway applies it, this is no longer allowed.

### Diagrams and repository settings

- The Business Process Flowchart now draws the idempotency check (closes Q-05): after Submit Order, a key already used by the same user returns the original order, and a duplicate-key race rolls back and returns it too.
- Added `.gitattributes` with `* text=auto eol=lf` (closes Q-06), so line endings no longer depend on each person's Git settings. The repository already stores LF, so no renormalization is needed.

### Audit logs

New actions, with `target_type` `ROLE` and `target_id` set to `roles.id`: `ROLE_CREATED`, `ROLE_UPDATED`, and `ROLE_DELETED`. Deleting a role also writes a `ROLE_REVOKED` row for each holder. The update log stores only the changed fields and puts `roleCode` in `remark`. The delete log stores the whole row in `before_data`. Replacing a user's roles reuses the v1.3 formats. The exact formats are in the design document.

### Privileges

- Deleting a role needs `DELETE` on `roles`. It is not granted now. [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) and [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) say to grant it, for that table only, when the permission feature is built, next to `DELETE` on `user_roles`.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Keeping `users.status` | Keep it, as the "can be used now" flag, separate from `deleted` | Removing it and using `deleted` only, which cannot express a temporary, reversible suspension |
| Grants to `DISABLED` users (Q-02) | Refuse them, in the service layer | Allowing them so roles can be prepared before activation; loosening the rule later is easier than tightening it |
| Permissions of a disabled user | Keep the `user_roles` rows and ignore them while the account is not usable | Deleting the roles on disable, which loses the configuration, needs `DELETE` on `user_roles` (T-06), and leaves no grant history (Q-01) |
| Soft deletion and `status` | Set `status` to `DISABLED` when a user is soft-deleted | Leaving `status` unchanged, which allows a deleted user to look active |
| What can change on a role | Only `name` and `description`; no role status | A role status like `users.status`, which was designed first and then dropped because roles are meant to stay fixed apart from their label |
| Role deletion | Physical delete. Holders lose the role, each loss is logged, and nobody gets a default role | Requiring every grant to be revoked first, which is slower; giving holders a default role, which silently grants permissions |
| Users with no role | A valid state; new users start in it | Giving new users a default role, which does not fit an internal program where an administrator grants everything |
| First administrator | Created at startup from environment variables when no usable `ADMIN` exists | A seed migration, which would put a password hash in git; a first-registered-user-becomes-admin rule, which anyone could win; a command-line tool, left as a later option |
| Initial password | The bootstrap administrator must change it at first login, tracked by `users.must_change_password` | Trusting the initial password, which may remain in a shell history |
| Line endings (Q-06) | LF for every text file, set by `.gitattributes` | CRLF, which the tools in this project (SQL, PowerShell, Java, frontend) handle less consistently |
| Old password in Git history (Q-08) | Leave the history as it is; no rewrite, no force-push | Rewriting history, which cannot reach clones or hosting caches and costs more than it gains for a password that is already rotated |
| Idempotency check in the flowchart (Q-05) | Drawn, as a separate section before order creation | Leaving it only in the text |
| Who may manage roles and accounts | `ADMIN` manages role definitions and appoints managers and administrators. `MANAGER` assigns only roles below manager, to users who hold neither protected role | Letting `MANAGER` create, edit, or delete roles, which was rejected because it could reshape the permission set; a rank column on `roles` to allow several tiers, which adds a schema change before a second tier is needed |
| Permissions of the seeded roles (Q-11) | A fixed matrix in the design document, kept in code by role code. New capabilities are added by the backend developers, so a new role gets its permissions with the code | Permission tables so that an administrator can configure new roles, which adds tables, changes both ERDs, and needs a configuration screen the project does not need |
| Keeping `VIEWER` | Keep it as the read-only role | Dropping it and adding a read-only role later, which would need a migration and code when the first customer asks |
| Administrator protection | A fixed service-layer rule with a row lock, covering every operation that could remove the last usable `ADMIN`; the `ADMIN` role cannot be deleted | Protecting only role deletion, which leaves disabling and revoking open |
| Database trigger behind Administrator protection (Q-10) | Not added. Direct SQL as `root` or the migration account can still remove the last `ADMIN`, and that is accepted: the startup bootstrap (T-24) creates a new administrator when none exists | A trigger on `user_roles` and `users`, which would stop even direct SQL but hides a business rule in the database and changes the schema, the Physical ERD, and the verification script |
| Changing `code` | Not allowed after creation | Allowing it, which would leave old log rows pointing at a code that no longer exists |
| Replacing a user's roles | Revoke what is removed and grant what is added, in one transaction, reusing the v1.3 log formats | A separate `ROLE_REPLACED` action, which would need its own format and could drift from the grant and revoke logs |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | Updated in place (`users.must_change_password`); not yet applied anywhere |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Comment updated; the active grants did not change |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated: `must_change_password` (boolean, default false) added to `users`, after `password_hash` |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn) | Updated: new Idempotency Check section, wired between Submit Order and Validate Products |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Not changed; no table or relationship changed |
| [Verification script](../../db/verification/run-db-verification.ps1) | One check added for `users.must_change_password`. Re-run on 2026-10-22 against the updated migration: 63 checks passed, 0 failed |

## Lessons From The Password Exposure

An early commit (`7096b88`) put a real database password into `ENVIRONMENT_SETUP.md`, and that commit was pushed to GitHub. It was removed in a later commit, the password was rotated, and the history was deliberately not rewritten. What this taught:

- **A pushed secret is public for good.** Deleting it in a later commit only hides it from the current files. The old commit, every clone, and any copy kept by the hosting service still contain it. Treat any secret that reached a remote as known to others.
- **Rotate first.** Changing the password is the only step that actually removes the risk, and it should happen the moment the secret is noticed, before anything else is cleaned up. Here it was done, which is why the exposed value is harmless now.
- **Rewriting history is a weak second step.** It changes every later commit, needs a force-push, and still cannot reach clones or cached copies. For a value that is already rotated, the cost is higher than the benefit, so the history was left as it is.
- **Documents count as code.** Setup guides are where passwords slip in, because writing the real value feels like helping the reader. Documents and examples hold placeholders only, and real values come from environment variables (T-08).
- **Limit what one leak can do.** Separate MySQL accounts for administration, migrations, and the application, and per-table privileges for the application account, mean a leaked application password cannot change the schema or rewrite the audit trail.
- **Keep secrets out of tools and chat too.** The verification script asks for the root password in a masked dialog and never prints or stores it, and passwords are not pasted into chat or logs.
- **Check before every commit.** Reading the staged diff for credentials, keys, and connection strings before committing is cheap, and it is the only point where a secret can still be stopped before it enters history.

## Open Items

- Implement the grant rules, the permission checks, and role management in the permission module (T-15, T-19, T-21), and grant `DELETE` on `roles` and `user_roles` with it (T-06).
- Create the first administrator at startup (T-24) and enforce the forced password change (T-25).
- Handle users with no role at login, in the API, and in the frontend (T-22), and implement Administrator protection (T-23).
- Review and refine the Permission Matrix, then enforce it (T-27) together with Authority over roles and accounts (T-26).
- Make sure a token issued before an account was disabled stops working (T-20).
- The open items of [database-v1.3.md](database-v1.3.md) and [database-v1.2.md](database-v1.2.md) still apply.
