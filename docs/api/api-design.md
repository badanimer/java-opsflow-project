# OpsFlow API

## Purpose

This document describes the conventions of the OpsFlow HTTP API as they are decided now: the paths, the data formats, the error response, the result codes, the status codes, and the paging. It is the current-state document of the API area. It does not list the endpoints one by one: the endpoints follow the modules in [database-design.md](../database/database-design.md) and the pages in [page-design.md](../frontend/page-design.md), and each module adds its own when it is built. The backend that serves the API is described in [backend-design.md](../backend/backend-design.md).

## Change Log

This document always describes the current API conventions. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-11-19 | 1.12 | A row of `GET /tickets` carries `person` and `time`, the person and the time of the step its screen is about, in place of `createdBy`, `createdAt`, `closedAt`, and `cancelledAt`; the list is ordered and filtered by that time. `GET /audit-records` takes `ticketNo` in the area `TICKET` | [api-v1.12.md](../iterations/api-v1.12.md) |
| 2026-11-18 | 1.11 | Who sees which order records (Q-34): `GET /order-records` returns everyone's attempts with the new permission `ORDER_VIEW_ALL`, which may narrow them with `userId`; without it only the caller's own, and a `userId` is `FORBIDDEN`. `userId` with `orderNo` is `VALIDATION_FAILED`. `GET /user-options?use=filter` is open to `ORDER_VIEW_ALL` too | [api-v1.11.md](../iterations/api-v1.11.md) |
| 2026-11-18 | 1.10 | The login counts by client address (Q-07): `LOGIN_LOCKED` also means that this address is locked out of the account, `TOO_MANY_REQUESTS` also refuses a login from an address with 50 failures in 15 minutes, and a password reset clears the locks of every address. No path or format changed | [api-v1.10.md](../iterations/api-v1.10.md) |
| 2026-11-16 | 1.9 | Phase 6: the ticket endpoints (the five screens and the search, the detail, opening a ticket, take, assign, reassign, release, resolve, cancel, and the single and batch archive), the user picker `GET /user-options`, the codes `TICKET_CHANGED` and `NOT_ELIGIBLE`, the meaning of `NOT_ALLOWED`, and the limits of the two `TEXT` fields: 4,000 characters for a description and 2,000 for a resolution; the audit log page, `GET /audit-records` and `GET /audit-order-records`; for a change of roles, `ticketsReturned` in the answer and the warning `GET /users/{id}/role-change`; and from the click-through of 2026-11-17, `inventoryId` in the ticket search, `status` and `ticketsInProgress` on the user page with deleted users, `ticketsReturned` of a disabling, `aboutUserId` on the audit page, and a `remark` that is never empty | [api-v1.9.md](../iterations/api-v1.9.md) |
| 2026-11-16 | 1.8 | Placing an order, `POST /orders`, with its number in every recorded refusal and the original order for a key used before; the three manual stock changes, `POST /items/{id}/stock-in`, `/write-off`, and `/correction`; the code `STOCK_CHANGED`; the two logs (`/listing-records`, `/order-records`, `/item-records`, `/stock-movements`) with the time filter and the search by one order number, `/deleted-items`, and the product picker `/product-options`. An order refused by its permissions gets `FORBIDDEN` and not `NOT_ALLOWED` | [api-v1.8.md](../iterations/api-v1.8.md) |
| 2026-11-15 | 1.7 | The endpoints of warehouse items, item images, and products for Phase 4, and the codes `ITEM_NAME_TAKEN`, `ITEM_LISTED`, `ALREADY_LISTED`, `ALREADY_DELISTED`, `IMAGE_TOO_LARGE`, and `IMAGE_TYPE_NOT_ALLOWED` | [api-v1.7.md](../iterations/api-v1.7.md) |
| 2026-11-15 | 1.6 | One signed-in device per account: the code `SESSION_REPLACED` for a token of a login that a newer login of the account ended, and the event stream `GET /auth/session-events`, which tells an open page at once | [api-v1.6.md](../iterations/api-v1.6.md) |
| 2026-11-15 | 1.5 | The first endpoints of the user module: the user page, `GET /users`, the first list of the API, with a search and role buttons; `PUT /users/{id}/roles`, which replaces the roles of a user and carries the roles the sender saw, `POST /users/{id}/disable`, `POST /users/{id}/enable`, and `DELETE /users/{id}` for a soft deletion, and three codes for their refusals: `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN` | [api-v1.5.md](../iterations/api-v1.5.md) |
| 2026-11-15 | 1.4 | `GET /auth/me` also returns `permissions`, what the user may do, so that the frontend shows a control by its permission and never by a role | [api-v1.4.md](../iterations/api-v1.4.md) |
| 2026-11-12 | 1.3 | The answers of the login endpoint and of the token check for a disabled, soft-deleted, or just-reset account: `ACCOUNT_DISABLED` at login with no token, `ACCOUNT_DISABLED` on the next request after the account is disabled, and `UNAUTHENTICATED` for a soft-deleted account and for a login whose password was replaced while it was checked. The first endpoints: `POST /auth/login` and `GET /auth/me`. Extended on 2026-11-13 with the email codes, sign-up, and the password reset: `POST /auth/email-codes`, `POST /auth/sign-up`, and `POST /auth/password-reset`, the codes `CODE_INVALID`, `USERNAME_TAKEN`, and `EMAIL_TAKEN`, and a wider meaning of `TOO_MANY_REQUESTS` | [api-v1.3.md](../iterations/api-v1.3.md) |
| 2026-11-12 | 1.2 | Every response carries a request id in the header `X-Request-Id`, and every error body carries it as `requestId` | [api-v1.2.md](../iterations/api-v1.2.md) |
| 2026-11-09 | 1.1 | The conventions are written into code: the format of the idempotency key, the rules that refuse a bad paging parameter, and two codes for a wrong method and a wrong media type | [api-v1.1.md](../iterations/api-v1.1.md) |
| 2026-11-04 | 1.0 | The first conventions: paths, formats, the error response, result codes, status codes, and key-based paging | [api-v1.0.md](../iterations/api-v1.0.md) |

## General Rules

| Item | Rule |
| --- | --- |
| Base path | `/api/v1`. A breaking change gets `/api/v2`; a compatible one stays in `v1`. |
| Resource names | Plural nouns in lower case, for example `/orders`, `/tickets`, `/products`. |
| Actions | A `POST` on a sub-resource named after the action, for example `POST /tickets/{id}/take` and `POST /tickets/{id}/release`. |
| Format | JSON in both directions, field names in camelCase. |
| Time | ISO 8601 in UTC with a trailing `Z`, for example `2026-11-04T03:00:00Z`. The frontend converts it for display. See [Time zone](../database/database-design.md#time-zone). |
| Authentication | The header `Authorization: Bearer <token>`. The token comes from `POST /auth/login`, lasts 12 hours, and carries only the user id and its issue time. There is no refresh token. See Authentication. |
| Success | No envelope: the response body is the resource itself. `200` for a read or an update, `201` with the new resource for a create, `204` with no body when there is nothing to return. |
| Idempotency | An order request may carry the header `Idempotency-Key`. A repeated submission with the same key returns the original order and adds no row. The key is 1 to 128 visible ASCII characters (a UUID fits), because it is stored in a 128-character column; an empty, longer, or other key is refused with `VALIDATION_FAILED`, and `errors` names the field `Idempotency-Key`. |
| Request id | Every response, successful or not, carries the header `X-Request-Id`: 32 hexadecimal characters that the server makes new for each request. The same value is on every log line of the request, so the id from a report finds the request in the log. The server ignores an `X-Request-Id` sent by the client. |
| Limits | The length limit of every text field equals the length of its database column, so that over-long input is refused with `VALIDATION_FAILED` and never truncated (T-13). The two `TEXT` columns have no length, so their limits are chosen: a ticket description has at most 4,000 characters and a resolution at most 2,000. |

## Error Response

Every error uses the Problem Details format (RFC 9457, `application/problem+json`), which Spring supports directly, with two added fields: `code`, that a program can read, and `requestId`, that a person can quote:

```json
{
  "status": 409,
  "code": "OUT_OF_STOCK",
  "title": "Out of stock",
  "detail": "Widget has only 3 left",
  "requestId": "ddddc8f3b64942ff94a448cfbb1d7f9d",
  "orderNo": "ord-3f2a..."
}
```

- `code` is the stable, machine-readable result. The frontend decides what to do from `code`, never from `detail`.
- `title` and `detail` are for people and may change in wording.
- `requestId` is the value of the `X-Request-Id` header of the same response. The frontend shows it with an error message that the user may report, and never decides anything from it.
- A refused order and a refused ticket may carry extra fields that belong to the result, such as `orderNo`. A refused order always carries its `orderNo`, because every attempt has one and is recorded in `order_attempts`.
- A validation failure adds `errors`, a list of `{ "field": "...", "message": "..." }` for each field that was refused.
- The error body never contains a stack trace, an SQL message, a password, a code, or a hash.

## Result Codes And Status Codes

The codes already defined in [database-design.md](../database/database-design.md) keep their names. The general codes are new and cover what no module defines.

| Status | Code | Meaning | Defined in |
| --- | --- | --- | --- |
| 400 | `VALIDATION_FAILED` | A field is missing, malformed, or too long | General |
| 400 | `INVALID_QUANTITY` | A quantity is not a whole number in its range | [Valid quantities](../database/database-design.md#valid-quantities) |
| 400 | `CODE_INVALID` | The email code is wrong, has expired, was used, has had 5 wrong entries, or does not exist. The answer is the same in every case. The person requests a new code | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 400 | `IMAGE_TYPE_NOT_ALLOWED` | The uploaded file is not a JPEG, PNG, or WebP image, judged by its first bytes | [Item images](../database/database-design.md#item-images) |
| 401 | `UNAUTHENTICATED` | No token, an invalid or expired token, a token not issued after `password_changed_at`, a token that carries no login id (issued before logins were kept), or a token of a user who does not exist or is soft-deleted. At login: a wrong username or password | General |
| 401 | `SESSION_REPLACED` | The account logged in again, on this or another device, after this token was issued: only the newest login is signed in | [One signed-in device](../database/database-design.md#login-security-and-account-recovery) |
| 403 | `FORBIDDEN` | The user is logged in and holds a role, and the Permission Matrix does not allow the action for any of the user's roles. Also a request to an endpoint that declares nothing or does not exist, whatever the roles | General |
| 403 | `NOT_ALLOWED` | The permission is held, and a rule of the ticket refuses it: an operator who releases, resolves, or cancels a ticket that is not theirs. An order is refused by the permission check like every endpoint: `FORBIDDEN`, `ACCOUNT_DISABLED`, or `ACCOUNT_NO_ROLE` | [Status transitions](../database/database-design.md#status-transitions) |
| 403 | `ACCOUNT_DISABLED` | The account is disabled. At login it is the answer to the right password, with no token. On any other request it means the account was disabled after login. The frontend clears its login state and shows the message on the login page | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 403 | `ACCOUNT_NO_ROLE` | The account has no role yet, and the endpoint needs one. The pending page shows it | [Users without roles](../database/database-design.md#users-without-roles) |
| 404 | `NOT_FOUND` | The resource does not exist, or the user may not know that it exists. In a change to a user: the user does not exist, is soft-deleted, or is the SYSTEM identity | General |
| 404 | `ORDER_NOT_FOUND` | The order number named on a ticket is not in `order_attempts` | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 404 | `FOLLOW_NOT_FOUND` | The ticket to follow does not exist | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 405 | `METHOD_NOT_ALLOWED` | The path exists but not for this HTTP method | General |
| 409 | `OUT_OF_STOCK` | An item is short: for an order, or for a write-off larger than the stock | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow), [Manual stock changes](../database/database-design.md#manual-stock-changes) |
| 409 | `NO_STOCK` | An item with no stock cannot be listed | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ALREADY_LISTED` | The item is listed now, so it cannot be listed again | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ALREADY_DELISTED` | The product is not listed now, so it cannot be delisted | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ITEM_NAME_TAKEN` | Another item that is not deleted has this name, without regard to case | [Warehouse items](../database/database-design.md#warehouse-items) |
| 409 | `ITEM_LISTED` | The item is on sale, so its name and image cannot be changed; delist it first | [Warehouse items](../database/database-design.md#warehouse-items) |
| 409 | `PRODUCT_DELISTED` | The product is not on sale | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `PRODUCT_CHANGED` | The name or price differs from the one the user saw | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `ITEM_CHANGED` | A chosen item no longer exists. The body names them | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 409 | `STOCK_CHANGED` | A correction was made from a stock that is no longer the stock of the item: someone changed it in the meantime. Nothing is changed, and the page reloads the item | [Manual stock changes](../database/database-design.md#manual-stock-changes) |
| 409 | `FOLLOW_NOT_ALLOWED` | The ticket to follow is not `RESOLVED` or `CLOSED` | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 409 | `LIMIT_REACHED` | The person already has 99 open tickets, or 99 in progress | [Ticket limits](../database/database-design.md#ticket-limits) |
| 409 | `TICKET_CHANGED` | The ticket is not as the sender saw it: its status no longer allows the change, or its assignee is not the one the sender saw. The body carries the status now as `ticketStatus`. Nothing is changed, and the page reloads the ticket | [Status transitions](../database/database-design.md#status-transitions), [Reassigning and releasing a ticket](../database/database-design.md#reassigning-and-releasing-a-ticket) |
| 409 | `NOT_ELIGIBLE` | The chosen assignee may not hold the ticket: not a usable account, no role that may take tickets, the SYSTEM identity, an administrator chosen by someone who is not one, or the person who holds it now | [Who may take a ticket](../database/database-design.md#who-may-take-a-ticket) |
| 409 | `USER_CHANGED` | The user is not as the sender saw it: someone else changed the roles, or disabled or enabled the account, in which case the body carries the current status as `userStatus`. Nothing is changed, and the page reloads the user | [Steps of a change to a user](../database/database-design.md#steps-of-a-change-to-a-user) |
| 409 | `USER_NOT_ACTIVE` | A role is to be given to a disabled user. Roles can still be taken away. The user is enabled first | [Role grants](../database/database-design.md#role-grants) |
| 409 | `LAST_ADMIN` | The change would leave the system with no usable administrator | [Administrator protection](../database/database-design.md#administrator-protection) |
| 409 | `USERNAME_TAKEN` | At sign-up, the username belongs to an account. The code is not used up, so the person picks another username and sends the same code | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 409 | `EMAIL_TAKEN` | The address already has an account. It is the answer to a request for a sign-up code, and to a sign-up whose address got an account after the code was mailed. The page says that the address is registered | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 413 | `IMAGE_TOO_LARGE` | The uploaded image is larger than 2 MB | [Item images](../database/database-design.md#item-images) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The body is not JSON (not multipart, on the image upload) | General |
| 429 | `LOGIN_LOCKED` | The login is locked after failed logins: this client address is locked out of the account, or the account is locked for every address after the failures of the day. The body carries `lockedUntil`, the time the lock ends, and does not say which of the two it is | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 429 | `TOO_MANY_REQUESTS` | A request was refused by a limit of its client address or of the system. For an email code: the same address and purpose within 60 seconds, more than 10 requests of one client address in an hour, or more than 200 requests of the whole system in a day. For a login: 50 failures of the client address in 15 minutes, on any username. The answer does not say which | General |
| 503 | `BUSY` | The retries on a deadlock or a lock wait were used up. Nothing was created | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 500 | `INTERNAL_ERROR` | An unexpected error. The body carries no detail | General |

Rules for choosing a status:

- `409` is for a conflict with the current state that a refresh and a new attempt can fix. It is not used for a bad request.
- The login endpoint answers a wrong username and a wrong password with the same `401` and never says which one was wrong. A locked login gets `LOGIN_LOCKED` without the password being checked. A client address with too many failures in its window gets `TOO_MANY_REQUESTS` before the username is read, so that answer says nothing about the username either.
- The login endpoint answers a disabled account with `ACCOUNT_DISABLED` only when the password is right, and issues no token. A wrong password on a disabled account gets the same `401` as on any account, so the answer does not tell a stranger that the account is disabled. A soft-deleted account is answered like an unknown username.
- A login whose password was replaced by a reset while it was being checked gets the same `401`, whether the old password matched or not (see [Login steps](../database/database-design.md#login-security-and-account-recovery)).
- On every other endpoint, `ACCOUNT_DISABLED` tells the frontend to clear its login state and go back to the login page, `UNAUTHENTICATED` tells it to ask for a login, and `SESSION_REPLACED` tells it to say that the account signed in on another device and then ask for a login. `ACCOUNT_NO_ROLE` sends the user to the pending page and keeps the login.
- A request for a sign-up code for an address that has an account is refused with `EMAIL_TAKEN`: the owner chose the clear message. A request for a recovery code is answered with the same `204` whether or not a mail is sent, so Forgot password never shows whether an address is registered. Every problem with a code is the one `CODE_INVALID`, also for an address that never had a code.
- `USERNAME_TAKEN` and `EMAIL_TAKEN` are `409`: the request was valid, and another choice fixes it. A username that breaks the username rule, or a password that breaks the password rule, is `VALIDATION_FAILED` with the field in `errors`, and so is a new password that equals the current one.
- A change to a user that the sender has no authority for is `FORBIDDEN` with a detail for the person: a change to one's own account, a manager who changes a user who holds `ADMIN` or `MANAGER`, and a manager who gives one of those roles. The user page offers none of these controls. An account that changed after the request arrived gets the answer of the token check: `UNAUTHENTICATED`, `ACCOUNT_DISABLED`, or `ACCOUNT_NO_ROLE`.
- A new module that needs a code adds it to this table in the same change.

## Authentication

The first endpoints of the API. The rules behind them are in [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery). None of them needs a token except `GET /auth/me` and `GET /auth/session-events`.

| Endpoint | Who | Request | Response |
| --- | --- | --- | --- |
| `POST /auth/login` | Everyone | `{ "username": "...", "password": "..." }`. The username has at most 64 characters and the password at most 24; a longer or empty value is refused with `VALIDATION_FAILED` before any check | `200` with `{ "token": "...", "expiresAt": "2026-11-13T13:00:00Z" }` |
| `GET /auth/me` | A logged-in user, with or without a role | None | `200` with `{ "id": 7, "username": "alice", "roles": ["VIEWER"], "permissions": ["PRODUCT_VIEW", "STOCK_VIEW", "DELETED_ITEMS_VIEW", "TICKET_VIEW", "TICKET_VIEW_ALL", "TICKET_SEARCH"] }`. `roles` holds the role codes and is empty while the account waits for a role, which is how the frontend knows to show the pending page. `permissions` holds what the user may do: the permissions of all the roles together, by the names of the enum `Permission` in the backend, and empty for a user with no role |

| `POST /auth/email-codes` | Everyone | `{ "email": "...", "purpose": "REGISTER" }`. `purpose` is `REGISTER` or `RESET_PASSWORD` | `204` with no body. For `RESET_PASSWORD` it is the same whether or not a mail was sent. For `REGISTER`, `EMAIL_TAKEN` when the address has an account. `TOO_MANY_REQUESTS` when a limit refuses the request |
| `POST /auth/sign-up` | Everyone | `{ "username": "...", "email": "...", "password": "...", "code": "123456" }`. The code is 6 digits | `201` with `{ "id": 7, "username": "alice", "token": "...", "expiresAt": "2026-11-13T13:00:00Z" }`. The account is active and has no role, and the new user is logged in: the token is the same kind as the one of `POST /auth/login`, and the frontend goes to the pending page. `CODE_INVALID`, `USERNAME_TAKEN`, `EMAIL_TAKEN`, or `VALIDATION_FAILED` otherwise |
| `POST /auth/password-reset` | Everyone | `{ "email": "...", "code": "123456", "newPassword": "..." }` | `204` with no body. Every token issued before it stops working, and the locks of failed logins are cleared, at every address. `CODE_INVALID` or `VALIDATION_FAILED` otherwise |

- `expiresAt` is 12 hours after the login. After it the token gets `UNAUTHENTICATED`, and the user logs in again.
- The refusals of the login are `UNAUTHENTICATED` (a wrong username or password), `LOGIN_LOCKED` with `lockedUntil`, `TOO_MANY_REQUESTS` (the client address had too many failures), and `ACCOUNT_DISABLED`, as in the rules above.
- `GET /auth/me` reads the account at each call, so a role that was granted or revoked shows at the next call.
- **One signed-in device.** Each login, and each sign-up, ends the earlier logins of the account: their tokens get `SESSION_REPLACED`. `GET /auth/session-events` (any logged-in user, with or without a role) is a stream of Server-Sent Events (`text/event-stream`) that a signed-in page keeps open. It sends the event `session-replaced` (data `{}`) and closes as soon as a newer login of the account commits. It also sends a comment line every 25 seconds, and closes after 15 minutes, after which the page opens it again. A token that is already ended gets `SESSION_REPLACED` instead of a stream. The stream needs the `Authorization` header like every other request, so the page reads it with `fetch` and not with `EventSource`, which cannot send a header.
- The frontend shows or hides a control by a name in `permissions`, and never by a role, so a new role needs no change in the frontend (see [Permissions](../backend/backend-design.md#permissions)). The list only decides what is shown: the server checks every request again.
- Every other request needs the token. Without one the answer is `UNAUTHENTICATED`, and a request that no rule allows is `FORBIDDEN`.
- A `401` carries the header `WWW-Authenticate: Bearer`. The body never says why a token was refused.
- A code is requested first and sent with the sign-up or the reset afterwards. It is valid for 10 minutes, and only the latest code of an address and purpose works. A wrong entry is counted, and after 5 the code is dead.
- A username is 3 to 64 letters and digits, with no space and no other character. A password is 8 to 24 characters with at least one letter and one digit. Both are checked before the code is looked at, so a refused value does not count as a wrong entry.
- When `USERNAME_TAKEN` comes back, the same code can be sent again with another username.

## Users

The endpoints of the user page. The rules behind them are in [Authority over roles and accounts](../database/database-design.md#authority-over-roles-and-accounts) and [Steps of a change to a user](../database/database-design.md#steps-of-a-change-to-a-user). 
| Endpoint | Who | Request | Answer |
| --- | --- | --- | --- |
| `GET /users` | A user with the permission `USER_VIEW` (an administrator or a manager) | The parameters of Paging, and `search` and `roles` (below) | `200` with a page: `{ "items": [ { "id": 9, "username": "bob", "status": "ACTIVE", "roles": ["OPERATOR"] } ], "nextCursor": "...", "prevCursor": null }` |
| `PUT /users/{id}/roles` | A user with the permission `ROLE_ASSIGN` (an administrator or a manager), on another user | `{ "roles": ["OPERATOR", "VIEWER"], "expectedRoles": ["VIEWER"] }`. `roles` is the whole list the user is to hold afterwards, and an empty list leaves the user with no role. `expectedRoles` is the list the sender saw on the screen | `200` with `{ "id": 9, "username": "bob", "roles": ["OPERATOR", "VIEWER"], "ticketsReturned": 0 }`, the roles the user holds now, in the order of the codes, and the number of tickets in progress that went back to the pool because the user can no longer take tickets |
| `GET /users/{id}/role-change` | `ROLE_ASSIGN` | `roles`, the whole new list, as for the replacement (comma-separated or repeated) | `200` with `{ "ticketsReturned": 3 }`: the tickets in progress that a replacement with this list would return to the pool, all of them when the user may take tickets now and could not with the list, otherwise 0. It changes nothing, reads without a lock, and is the warning of the Change roles dialog; the replacement decides again. `NOT_FOUND` for a user who does not exist, is soft-deleted, or is the SYSTEM identity, and `VALIDATION_FAILED` for a code that is not a role |
| `POST /users/{id}/disable` | A user with the permission `USER_MANAGE`, on another user | None | `200` with `{ "id": 9, "username": "bob", "status": "DISABLED" }` |
| `POST /users/{id}/enable` | The same | None | `200` with the same fields and `"status": "ACTIVE"` |
| `DELETE /users/{id}` | The same | None | `204`. The deletion is soft: the account is marked as removed and disabled, and its row, its roles, its username, and its email stay |

- `GET /users` lists only the users the caller may manage, as [User page](../database/database-design.md#user-page) says: an administrator sees every user, their own account included, and a manager the users who hold neither `ADMIN` nor `MANAGER`, not their own account. Disabled users and users with no role are listed; soft-deleted users and the SYSTEM identity never are. The rows are ordered by username from A to Z, and then by `id`. The order is fixed, so `order` is refused.
- `search` is a part of the username, matched without regard to case; it is at most 64 characters, and an empty or blank value is no search. `roles` holds the chosen role buttons, as role codes and `NONE` for No role, comma-separated (`roles=OPERATOR,NONE`) or repeated; the codes are read without regard to case, an unknown one is `VALIDATION_FAILED`, and an empty value is no button. With buttons chosen, a user who holds any one of the chosen roles is listed, and with `NONE` also a user who holds none. The two filters combine, and neither widens what the caller may see.
- `status` holds the chosen status buttons, `ACTIVE`, `DISABLED`, and `DELETED`, comma-separated or repeated and read without regard to case; an unknown one is `VALIDATION_FAILED`. With none, the users who are not deleted are listed. A soft-deleted user is listed only with `DELETED`, as `"status": "DELETED"`, and every change to such a user is still `NOT_FOUND`. Each row carries `ticketsInProgress`, the tickets assigned to the user that are in progress now. `POST /users/{id}/disable` answers `ticketsReturned`, the tickets that went back to the pool.
- The order of the codes in a list does not matter, and a code named twice counts once.
- A list equal to the roles of the user is answered `200` and changes nothing.
- A role is given or taken away only as part of the whole list, in one transaction: either every difference is applied, or none.
- The refusals: `VALIDATION_FAILED` (a missing list, or a code in `roles` that is not a role), `FORBIDDEN`, `NOT_FOUND`, `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN`, as in the table of result codes. The table of each refusal is in [Steps of a change to a user](../database/database-design.md#steps-of-a-change-to-a-user).
- Giving or taking away `ADMIN` or `MANAGER`, and changing the roles of a user who holds one of them, needs the permission `ROLE_ASSIGN_PROTECTED`. Disabling, enabling, or deleting such a user needs `USER_MANAGE_PROTECTED`. The frontend finds both in `permissions` of `GET /auth/me`.
- Disabling starts from an active account and enabling from a disabled one. A request that finds the other status is refused with `USER_CHANGED` and `userStatus`, and changes nothing. A deletion accepts both.
- A disabled user keeps the roles, which count for nothing until the account is enabled. The next request of a disabled user is answered `ACCOUNT_DISABLED`, and that of a deleted user `UNAUTHENTICATED`.
- A user who was deleted does not exist for any later call: the answer is `NOT_FOUND`. There is no way to bring a deleted account back.
- The refusals of the three: `FORBIDDEN`, `NOT_FOUND`, `USER_CHANGED`, and `LAST_ADMIN`.

## Warehouse Items, Images, and Products

Built on 2026-11-15 for Phase 4 ([api-v1.7.md](../iterations/api-v1.7.md)). The rules behind them are in [Warehouse items](../database/database-design.md#warehouse-items), [Item images](../database/database-design.md#item-images), and [Listing and delisting](../database/database-design.md#listing-and-delisting). The stock page of Phase 4 lists the items and adds, edits, and deletes them. Changing a quantity and placing an order are in Orders and Stock Changes, and the logs in Logs.

| Endpoint | Who | Request | Answer |
| --- | --- | --- | --- |
| `GET /items` | `STOCK_VIEW` (every role) | The parameters of Paging, and `search`, a part of the name | `200` with a page of `{ "id": 3, "sku": "K7Q2M9XA", "name": "A4 paper", "imageUrl": "/api/v1/images/items/3f2b...webp", "availableQuantity": 40, "listed": true }`. Items that are not deleted, ordered by name and then `id`. `imageUrl` is null without an image. `listed` says whether the item's product is `LISTED`: an item on sale cannot be edited |
| `POST /item-images` | `ITEM_MANAGE` (administrator, manager) | `multipart/form-data` with one part `file` | `201` with `{ "path": "items/3f2b8c1e-....webp", "url": "/api/v1/images/items/3f2b8c1e-....webp" }`. The file is stored under a random name; `path` is then sent with the item |
| `POST /items` | `ITEM_MANAGE` | `{ "name": "A4 paper", "imagePath": "items/3f2b...webp", "startingQuantity": 40 }`. `imagePath` and `startingQuantity` may be left out | `201` with the item as in `GET /items`. The SKU is generated |
| `PUT /items/{id}` | `ITEM_MANAGE` | `{ "name": "A4 paper", "imagePath": "items/9a1c...png" }`: the whole editable state. A null `imagePath` removes the image | `200` with the item |
| `DELETE /items/{id}` | `ITEM_DELETE` (administrator, manager) | None | `204`. The deletion is soft: the item leaves every list, its product is delisted, and its name is free again. The stock is left as it was |
| `GET /images/items/{file}` | Everyone, with no token | None | `200` with the image, its content type, and `Cache-Control: public, max-age=31536000, immutable`. `404` for any other name |
| `GET /products` | `PRODUCT_VIEW` (every role) | The parameters of Paging, and `search`, a part of the name | `200` with a page of `{ "id": 5, "name": "A4 paper", "price": "12.50", "imageUrl": "/api/v1/images/items/3f2b...webp" }`. Only `LISTED` products, ordered by name and then `id`. Nothing else is shown, the stock included |
| `GET /products/listable` | `PRODUCT_LIST` (administrator, manager) | The parameters of Paging, and `search`, a part of the name | `200` with a page of `{ "inventoryId": 3, "sku": "K7Q2M9XA", "name": "A4 paper", "imageUrl": null, "availableQuantity": 40 }`: the catalog, the items that are not deleted and not listed now, ordered by name and then `id` |
| `POST /products` | `PRODUCT_LIST` | `{ "inventoryId": 3, "name": "A4 paper", "price": "12.50" }` | `201` with `{ "id": 5, "inventoryId": 3, "name": "A4 paper", "price": "12.50", "status": "LISTED" }`. The first listing of an item creates its product; a later one lists the same product again, with the same `id` |
| `POST /products/{id}/delist` | `PRODUCT_DELIST` (administrator, manager) | None | `200` with the product, `"status": "UNLISTED"` |

- **Names.** An item name is 1 to 200 characters, trimmed, and unique among the items that are not deleted without regard to case; a taken one is `ITEM_NAME_TAKEN`. A product name follows the same length rule and may repeat. A blank or too long name is `VALIDATION_FAILED`.
- **Prices** are sent and returned as strings with at most two decimals (`"12.50"`), so no rounding happens on the way. A price must be above 0 and at most `9999999999.99`, the largest the column holds. Anything else is `VALIDATION_FAILED` on `price`.
- **The starting quantity** follows [Valid quantities](../database/database-design.md#valid-quantities): a whole number from 0 to 999,999, and empty means 0. Anything else is `INVALID_QUANTITY`.
- **Images.** The upload accepts JPEG, PNG, and WebP up to 2 MB, and the type is read from the first bytes of the file, not from the name or the header. A larger file is `IMAGE_TOO_LARGE` (413); any other type is `IMAGE_TYPE_NOT_ALLOWED` (400); a body that is not multipart is `UNSUPPORTED_MEDIA_TYPE`, and a multipart body without the part `file` is `VALIDATION_FAILED`. A name of an image that holds an encoded slash is refused with `400` before any controller. The file is stored before any row refers to it, so a failed save leaves an unused file and never a row that points to nothing. An `imagePath` must be a path that `POST /item-images` returned and whose file exists; anything else is `VALIDATION_FAILED` on `imagePath`. Image files are never deleted.
- **Why the images need no token.** A browser loads an `<img>` without the `Authorization` header, so the image address is open, as product images usually are. The file name is a random UUID and cannot be guessed, so only a person who was shown the page knows the address. The images are product pictures and not secrets.
- **The refusals of an item.** `NOT_FOUND` when the item does not exist or is deleted, also for a second deletion; `ITEM_LISTED` (409) when an edit finds the item on sale (delist it first); `ITEM_NAME_TAKEN` (409).
- **The refusals of a listing.** `NOT_FOUND` when the item does not exist or is deleted; `ALREADY_LISTED` (409) when it is listed now, by someone else meanwhile or before; `NO_STOCK` (409) when its `available_quantity` is 0.
- **The refusals of a delisting.** `NOT_FOUND` when there is no such product; `ALREADY_DELISTED` (409) when it is not listed now.
- Deleting an item and delisting a product also cancel the linked tickets. There are no tickets before Phase 6, so that step is added with the ticket module (T-21, T-33).

## Orders and Stock Changes

Built on 2026-11-16 for Phase 5 ([api-v1.8.md](../iterations/api-v1.8.md)). The rules behind them are in [Create order](../database/database-design.md#create-order), [Order attempts](../database/database-design.md#order-attempts), and [Manual stock changes](../database/database-design.md#manual-stock-changes). There is no order list and no order page: an order is an action, and its record is read in the product log.

| Endpoint | Who | Request | Answer |
| --- | --- | --- | --- |
| `POST /orders` | `ORDER_PLACE` (administrator, manager, operator) | The header `Idempotency-Key` (optional, see General Rules) and `{ "lines": [ { "productId": 5, "quantity": 2, "name": "A4 paper", "price": "12.50" } ], "remark": null }`. `name` and `price` are the ones the user saw on the product page. `remark` is optional, at most 500 characters | `201` with `{ "id": 41, "orderNo": "ORD-3f2b...", "totalAmount": "25.00", "itemCount": 1, "createdAt": "...", "lines": [ { "productId": 5, "sku": "K7Q2M9XA", "name": "A4 paper", "unitPrice": "12.50", "quantity": 2, "subtotal": "25.00" } ] }`. A key the user already used answers `200` with the original order and writes nothing |
| `POST /items/{id}/stock-in` | `STOCK_IN` (administrator, manager, operator) | `{ "quantity": 5, "reason": "delivery 42" }` | `200` with the item as in `GET /items`, with its new `availableQuantity` |
| `POST /items/{id}/write-off` | `STOCK_WRITE_OFF` (the same) | `{ "quantity": 3, "reason": "damaged" }` | `200` with the item |
| `POST /items/{id}/correction` | `STOCK_CORRECT` (the same) | `{ "quantity": 20, "expectedQuantity": 12, "reason": "stock count" }`: `quantity` is the number that was counted, and `expectedQuantity` the stock the person saw | `200` with the item |

**An order.**

- **The lines.** 1 to 20 lines, each product once. The product page sends one. `itemCount` is the number of lines.
- **The order number** is made for each attempt before it starts. Every refusal that is recorded carries it as `orderNo`, so the person can name it in a ticket: `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, `OUT_OF_STOCK` (409), `INVALID_QUANTITY` (400), and `BUSY` (503). Each is recorded in `order_attempts`, one row for each line, and the whole order is refused: no line is placed.
- **What is not an attempt.** A request whose shape is wrong is `VALIDATION_FAILED` and is not recorded: no lines, more than 20, a product named twice, a missing `productId`, a `name` or `price` that is missing or not in the form of the product page, an empty or too long `Idempotency-Key`, or a remark over 500 characters. A product that does not exist is `NOT_FOUND` and is not recorded, because there is no product to point at. A user whose roles do not allow orders gets `FORBIDDEN`, like every endpoint.
- **The quantity** is a whole number from 1 to 9,999 (see [Valid quantities](../database/database-design.md#valid-quantities)). Anything else is `INVALID_QUANTITY`, checked before the order transaction, so it wins over a refusal that the transaction would give.
- **The name and the price** must be the ones of the product now. The price is compared as a number, so `"12.5"` and `"12.50"` are the same. A product delisted since the page was read is `PRODUCT_DELISTED`; one listed again under another name or price is `PRODUCT_CHANGED`.
- **The stock is never shown.** `OUT_OF_STOCK` says that some line is short, not how much is left.
- **The idempotency key.** The page makes one key for each order dialog and sends it again when it retries after no answer. The original order is returned however the body of the repeat looks. A refused attempt does not use the key up, so the same key can be sent again after a refusal.

**A stock change.**

- **The quantity** of a stock-in or a write-off is a whole number from 1 to 999,999; the counted quantity of a correction is from 0 to 999,999; `expectedQuantity` is a whole number from 0. Anything else is `INVALID_QUANTITY` with the field.
- **The reason** is required: 1 to 255 characters after the spaces around it are removed. Anything else is `VALIDATION_FAILED` on `reason`.
- **The refusals.** `NOT_FOUND` when the item does not exist or is deleted. `OUT_OF_STOCK` (409) for a write-off larger than the stock. `INVALID_QUANTITY` for a stock-in that would pass the most an `INT` holds. `STOCK_CHANGED` (409) for a correction whose `expectedQuantity` is not the stock under the lock: the page reloads the item and the person counts again. A correction to the number the person saw changes nothing and is `VALIDATION_FAILED` on `quantity`.
- Every change writes a movement with the stock before and after, and a log row with the reason (see [Stock logs](../database/database-design.md#stock-logs)).

## Logs

Built on 2026-11-16 for Phase 5 ([api-v1.8.md](../iterations/api-v1.8.md)). The rules are in [Logs](../database/database-design.md#logs), [The time filter](../database/database-design.md#the-time-filter), and [The searchable picker](../database/database-design.md#the-searchable-picker). Every endpoint here only reads, and every list is paged as in Paging.

| Endpoint | Who | Filters | A row |
| --- | --- | --- | --- |
| `GET /listing-records` | `PRODUCT_VIEW` (every role) | `productId`, the time filter, `order` | `{ "id": 7, "at": "...", "productId": 5, "inventoryId": 3, "action": "LISTED", "name": "A4 paper", "price": "12.50", "by": "mary" }`: the name and price at that moment |
| `GET /order-records` | `PRODUCT_VIEW` | `productId`, `userId` (who ordered, from `GET /user-options?use=filter`; only with `ORDER_VIEW_ALL`), the time filter, `order`; or `orderNo` alone | `{ "id": 12, "at": "...", "productId": 5, "productName": "A4 paper", "orderNo": "ORD-3f2b...", "orderedBy": "olive", "quantity": 3, "result": "COMPLETED", "stockDeducted": 3 }`: one line of every attempt, placed or refused. Without `ORDER_VIEW_ALL`, only the caller's own attempts |
| `GET /item-records` | `STOCK_VIEW` (every role) | `inventoryId`, the time filter, `order` | `{ "id": 4, "at": "...", "inventoryId": 3, "action": "CREATED", "sku": "K7Q2M9XA", "name": "A4 paper", "quantity": 10, "by": "mary" }`: the starting stock, or the stock the item showed when it was deleted |
| `GET /stock-movements` | `STOCK_VIEW` | `inventoryId`, the time filter, `order`; or `orderNo` alone | `{ "id": 20, "at": "...", "inventoryId": 3, "sku": "K7Q2M9XA", "itemName": "A4 paper", "type": "SALE", "orderNo": "ORD-3f2b...", "reason": null, "amount": -3, "by": "olive" }`. `amount` is `+` for `RESTOCK` and `ADJUST_IN` and `-` for the others; `orderNo` is set for a `SALE` only, and `reason` for a manual change only |
| `GET /deleted-items` | `DELETED_ITEMS_VIEW` (every role) | `search`, a part of the name or the SKU | `{ "id": 3, "sku": "K7Q2M9XA", "name": "Pens", "deletedAt": "...", "deletedBy": "mary", "productId": 6 }`. Newest deletion first; the order is fixed. `productId` is the product of the item if it ever had one, so the page can read the four parts of its own log |
| `GET /product-options` | `PRODUCT_VIEW` | `search`, a part of the name | `{ "id": 5, "name": "A4 paper", "sku": "K7Q2M9XA" }`: the product picker, every product ever listed except those of deleted items, by name from A to Z. The page shows the SKU beside a name that appears twice |

**The audit log page** (T-48, `AUDIT_VIEW`: administrator, manager). The rules are in [Audit log page](../database/database-design.md#audit-log-page).

| Endpoint | Filters | A row |
| --- | --- | --- |
| `GET /audit-records` | `area` (required: `ACCOUNT`, `ROLE`, `TICKET`, `STOCK`, `ITEM`, or `PRODUCT`), `action` (one action of that area), the time filter, `order`, either `userId` (from `GET /user-options?use=filter`) or `system=true`, and in the areas `ACCOUNT` and `ROLE` also `aboutUserId`, the user the rows are about, and in the area `TICKET` also `ticketNo`, the ticket the rows are about | `{ "id": 30, "at": "...", "area": "ITEM", "action": "ITEM_DELETED", "userId": 4, "by": "mary", "targetType": "ITEM", "targetId": 3, "before": { "name": "Pens", "availableQuantity": 9 }, "after": { "deleted": true, "cancelledTicketIds": [12], "cancelledTicketNos": ["K7Q2M9XA"], "sku": "K7Q2M9XA", "name": "Pens" }, "ipAddress": "10.0.0.5", "remark": null }`. `userId` and `by` are null for an action of the system, which the page shows as System. `before` and `after` are the data of the row as written, with its labels (see [Labels in audit rows](../database/database-design.md#labels-in-audit-rows)) |
| `GET /audit-order-records` | `result` (`COMPLETED`, `PRODUCT_DELISTED`, `PRODUCT_CHANGED`, `OUT_OF_STOCK`, `INVALID_QUANTITY`, or `BUSY`), `userId`, the time filter, `order` | The row of `GET /order-records`: the Orders area reads the same attempts |

- An action of another area, an unknown area or result, `system=true` with a `userId`, and a bad day are `VALIDATION_FAILED` with the field. The values are read without regard to case. Newest first unless `order=asc`.
- `aboutUserId` in any other area is `VALIDATION_FAILED`, and so is `ticketNo` outside `TICKET`. `ticketNo` is 8 letters and digits, read without regard to case and the spaces around it; any other form is `VALIDATION_FAILED`, and a number that no ticket has answers an empty page. It combines with `action`, the user, and the time filter, so one step of one ticket can be found. `remark` is never empty: it is the reason of the change, or the fixed remark of the action when the change asked for none. A row of a disabling, a deletion, or a revoked role that returned tickets to the pool carries `returnedTicketNos` in `after`.
- A row is never edited or deleted: the page only reads.

- **Three ways to read a log.** All records of one kind (no `productId` or `inventoryId`), the records of one chosen product or item, and the own log of one, which the page reads as its two kinds with the same id. An id that has no records gives an empty page. The ids of a deleted item and of its product are accepted, so the Deleted items module can show their records.
- **The item picker** is `GET /items`: every item that is not deleted, by name, with the SKU.
- **The time filter.** `from` and `to` are days written `yyyy-MM-dd`, in Sydney time. `from` alone reads from the start of that day until now; `from` and `to` read both days and every day between. No day is no filter. `to` without `from`, a day after today, and `to` before `from` are `VALIDATION_FAILED`.
- **The direction.** `order` is `desc` (newest first, the default) or `asc`, on the four lists of records only. The Deleted items list and the picker have a fixed order and refuse `order`.
- **One order number.** `orderNo` finds the lines of one attempt in the order records, or the `SALE` rows of one placed order in the stock movements. It is the whole number, matched exactly, whatever case is typed: `ORD-` and the rest in lower case, as the numbers are made. It is used alone: with `productId`, `inventoryId`, `userId`, `from`, or `to` it is `VALIDATION_FAILED`.
- **Who sees which order records** (Q-34). With `ORDER_VIEW_ALL` (`ADMIN`, `MANAGER`, `VIEWER`) the order records are every attempt, and `userId` narrows them to one person; a user id with no attempts gives an empty page, and an id below 1 is `VALIDATION_FAILED`. Without it (`OPERATOR`) they are the caller's own attempts in every form of the request, the search by order number included, and a request with `userId` is `FORBIDDEN`. The stock movements are not narrowed for anyone.

## Tickets

Designed on 2026-11-16 for Phase 6 ([api-v1.9.md](../iterations/api-v1.9.md)) and built in [backend-v1.11.md](../iterations/backend-v1.11.md). The rules behind it are in [Tickets](../database/database-design.md#tickets), [Ticket search](../database/database-design.md#ticket-search), [The user picker](../database/database-design.md#the-user-picker), [Ticket logs](../database/database-design.md#ticket-logs), and the ticket rows of [Concurrency by Module](../database/database-design.md#concurrency-by-module). The audit log page is under Logs.

**Reading.**

| Endpoint | Who | Request | Answer |
| --- | --- | --- | --- |
| `GET /tickets` | `TICKET_VIEW` (every role) | `status` (required: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, or `CANCELLED`, one screen), the parameters of Paging with `order`, and the search below | `200` with a page of `{ "id": 12, "ticketNo": "K7Q2M9XA", "type": "INVENTORY_EXCEPTION", "source": "MANUAL", "title": "Count of A4 paper is short", "status": "IN_PROGRESS", "person": { "id": 8, "username": "bob", "system": false }, "time": "..." }`. `person` and `time` belong to the step the screen is about (below) |
| `GET /tickets/{id}` | `TICKET_VIEW` | None | `200` with the ticket (below) |
| `GET /user-options` | `use=filter`: `TICKET_SEARCH`, `AUDIT_VIEW`, or `ORDER_VIEW_ALL`. `use=assignee`: `TICKET_ASSIGN` | `use` (required), `search` (a part of the username, at most 64 characters), the parameters of Paging | `200` with a page of `{ "id": 9, "username": "bob" }`, by username from A to Z and then `id`. The order is fixed |

The ticket of `GET /tickets/{id}`, and of every answer that changes a ticket:

```json
{
  "id": 12, "ticketNo": "K7Q2M9XA", "type": "ORDER_EXCEPTION", "source": "MANUAL", "status": "IN_PROGRESS",
  "title": "Order refused though stock was there", "description": "...",
  "orderNo": "ORD-3f2b...",
  "items": [ { "inventoryId": 3, "sku": "K7Q2M9XA", "name": "A4 paper", "deleted": false } ],
  "follows": { "id": 8, "ticketNo": "P2X9..." },
  "followUps": [ { "id": 15, "ticketNo": "Q4M1..." } ],
  "createdBy": { "id": 7, "username": "olive", "system": false }, "createdAt": "...",
  "assignee": { "id": 9, "username": "bob", "system": false },
  "history": [ { "from": null, "to": { "id": 9, "username": "bob", "system": false }, "by": { "id": 9, "username": "bob", "system": false }, "reason": null, "at": "..." } ],
  "resolution": null, "resolvedBy": null, "resolvedAt": null,
  "closedBy": null, "closedAt": null,
  "cancelReason": null, "cancelCause": null, "cancelledBy": null, "cancelledAt": null
}
```

- **A person** is `{ "id", "username", "system" }`. `system` is true only for the SYSTEM identity, which the page shows as System: the creator of a `SYSTEM` ticket, the one who closed a ticket by the archive job, cancelled it by a deletion or a delisting, or released it when its assignee was disabled.
- **Who sees which ticket.** A user with `TICKET_VIEW_ALL` sees every ticket. Any other user sees the whole pool, and on the other screens only the tickets of Ticket screens and who sees what: `IN_PROGRESS` assigned to them, `RESOLVED` resolved by them, `CANCELLED` created or last held by them, and no `CLOSED` ticket. The list filters on the user id in the query, and `GET /tickets/{id}` answers `NOT_FOUND` for a ticket the user cannot see, the same as for a ticket that does not exist. `status=CLOSED` without `TICKET_VIEW_ALL` is `FORBIDDEN`.
- **The history and the follow-ups.** `history` is the assignment history, oldest first; `reason` is null for a take. `follows` and `followUps` name every linked ticket by its number; a ticket the user cannot see has `id` null, and the page shows the number without a link.
- **The cancel cause.** `cancelCause` is null for a ticket a person cancelled. For a ticket the system cancelled it is `{ "kind": "PRODUCT_DELISTED", "name": "Widget" }` or `{ "kind": "ITEM_DELETED", "name": "Widget" }`, with the name of the product (for a delisting) or of the item (for a deletion), so the page can say "Widget has been delisted, please open the ticket again". `cancelReason` keeps the code the system wrote (`PRODUCT_DELISTED:5`).
- **The person and the time of a row** (api v1.12). Each screen shows the step it is about: on `OPEN` the creator and the creation, on `IN_PROGRESS` the assignee and when they got the ticket (taken, assigned, or reassigned to them), on `RESOLVED` who resolved it and when, on `CLOSED` who archived it and when, and on `CANCELLED` who cancelled it and when. `person.system` is true for the SYSTEM identity, which the page shows as System. Before api v1.12 a row carried `createdBy`, `createdAt`, `closedAt`, and `cancelledAt`.
- **The list order.** `SYSTEM` tickets first, then `MANUAL`, and inside each the `time` of the rows in the chosen direction (see [Ticket list order](../database/database-design.md#ticket-list-order)). `order` is `desc` (the default) or `asc`.
- **The search** needs `TICKET_SEARCH`; without it any of these parameters is `FORBIDDEN`. The filters combine: `source` (`SYSTEM` or `MANUAL`), `createdBy` and `assigneeId` (user ids from `GET /user-options?use=filter`), `inventoryId` (the tickets linked to one warehouse item, an id from `GET /items`), and the time filter `from` and `to` on the day of the screen's `time`, as in Logs. The other two ways are used alone, like `orderNo` in Logs: `ticketNo` (8 characters, turned into upper case, matched exactly) or `orderNo` (written as order numbers are made, then matched exactly). With any other filter they are `VALIDATION_FAILED`. Every search stays inside the `status` of its screen.
- **The user picker.** `use=filter` returns every user, disabled and soft-deleted users included. `use=assignee` returns the eligible assignees the caller may assign to: usable accounts that hold `TICKET_TAKE`, and among them the users who hold `ADMIN` only for a caller who holds `TICKET_ASSIGN_PROTECTED`. Neither returns the SYSTEM identity. The list is a help: the change checks again under the lock.

**Opening a ticket.**

| Endpoint | Who | Request | Answer |
| --- | --- | --- | --- |
| `POST /tickets` | `TICKET_CREATE` (administrator, manager, operator) | `{ "type": "INVENTORY_EXCEPTION", "title": "...", "description": "...", "inventoryIds": [3, 5], "orderNo": null, "followsTicketNo": null }` | `201` with the ticket |

- **The fields.** `type` is required. `title` is 1 to 200 characters and `description` 1 to 4,000, both after the spaces around them are removed (T-13). `inventoryIds` is 0 to 20 ids of items, each once; an `INVENTORY_EXCEPTION` needs at least one. `orderNo` is required for an `ORDER_EXCEPTION` and optional for the others. `followsTicketNo` is optional. A field that breaks these rules is `VALIDATION_FAILED` with the field, before any transaction. The item list of the form is `GET /items`.
- **The refusals**, in the order the transaction meets them: `LIMIT_REACHED` (409) when the creator already has 99 `OPEN` tickets they created; `FOLLOW_NOT_FOUND` (404) and `FOLLOW_NOT_ALLOWED` (409) for the follow-up number; `ORDER_NOT_FOUND` (404) when no order attempt has the number; `ITEM_CHANGED` (409) when a chosen item does not exist or is deleted, with `items`, the ids and names of those items (`[ { "inventoryId": 5, "name": "Widget" } ]`), so the page can say which; and `BUSY` (503). Nothing is written on a refusal, and the page keeps what the person typed.
- `source` is `MANUAL`, `createdBy` is the caller, and the number is made by the server.

**Changing a ticket.** Each is a `POST` on the ticket, and each answers `200` with the ticket as it is now.

| Endpoint | Who | Request | What it does |
| --- | --- | --- | --- |
| `POST /tickets/{id}/take` | `TICKET_TAKE` (administrator, manager, operator) | None | `OPEN` to `IN_PROGRESS`, the caller as assignee |
| `POST /tickets/{id}/assign` | `TICKET_ASSIGN` (administrator, manager) | `{ "assigneeId": 9, "reason": "..." }` | `OPEN` to `IN_PROGRESS`, with the chosen assignee |
| `POST /tickets/{id}/reassign` | `TICKET_ASSIGN` | `{ "assigneeId": 12, "expectedAssigneeId": 9, "reason": "..." }` | Another assignee for an `IN_PROGRESS` ticket |
| `POST /tickets/{id}/release` | `TICKET_RELEASE` (administrator, manager, operator) | `{ "expectedAssigneeId": 9, "reason": "..." }` | `IN_PROGRESS` back to `OPEN`, with no assignee |
| `POST /tickets/{id}/resolve` | `TICKET_RESOLVE` (administrator, manager, operator) | `{ "resolution": "..." }` | `IN_PROGRESS` to `RESOLVED` |
| `POST /tickets/{id}/cancel` | `TICKET_CANCEL` (administrator, manager, operator) | `{ "reason": "..." }` | `OPEN` or `IN_PROGRESS` to `CANCELLED` |
| `POST /tickets/{id}/archive` | `TICKET_ARCHIVE` (administrator, manager) | None | `RESOLVED` to `CLOSED` |
| `POST /tickets/archive` | `TICKET_ARCHIVE` | `{ "ids": [12, 13] }`, 1 to 100 ids, each once | `200` with `{ "archived": ["K7Q2M9XA"], "skipped": [ { "id": 13, "ticketNo": "P2X9...", "status": "CLOSED" } ] }`; `ticketNo` and `status` are null for an id that does not exist or that the caller cannot see. Each ticket in its own transaction; one that is no longer `RESOLVED`, or that the caller cannot see, is skipped and the rest go on |

- **Texts.** A `reason` is 1 to 255 characters and a `resolution` 1 to 2,000, after the spaces around them are removed. Anything else is `VALIDATION_FAILED` with the field.
- **`expectedAssigneeId`** is the assignee the person saw on the screen. Under the lock of the ticket row it must still be the assignee, so a release cannot take the ticket from someone who got it a moment ago, and two reassignments cannot overwrite each other.
- **`NOT_ALLOWED` (403)** is the refusal of a rule that depends on the ticket, when the permission itself is held: a user without `TICKET_HANDLE_ANY` (an operator) who releases, resolves, or cancels a ticket that is not theirs (theirs is the one assigned to them, or for a cancel one they created while it is `OPEN`). A manager and an administrator hold `TICKET_HANDLE_ANY`. A permission that is not held is `FORBIDDEN`, as on every endpoint. A ticket the caller cannot see is `NOT_FOUND` before any of this.
- **`TICKET_CHANGED` (409)** means the ticket is no longer as the person saw it: its status does not allow the change any more (taken, released, resolved, cancelled, or archived by someone else in the meantime), or its assignee is not `expectedAssigneeId`. The body carries `ticketStatus`, the status now. Nothing is changed, and the page reloads the ticket. A `CLOSED` or `CANCELLED` ticket always answers `TICKET_CHANGED` to a change.
- **`NOT_ELIGIBLE` (409)** means the new assignee may not hold the ticket: not a usable account, no role that may take tickets, the SYSTEM identity, an administrator chosen by a caller without `TICKET_ASSIGN_PROTECTED`, or, on a reassignment, the person who holds it now. A user id that does not exist is `NOT_ELIGIBLE` too.
- **`LIMIT_REACHED` (409)** when the person who would hold the ticket (the caller on a take, the chosen assignee on an assign or a reassign) already has 99 tickets in progress.
- **`NOT_FOUND`** when the ticket does not exist or the caller cannot see it. **`BUSY`** when the retries are used up.
- Every change writes its `ticket_assignment_history` row, if the assignee changes, and its `operation_logs` row in the same transaction (see [Ticket logs](../database/database-design.md#ticket-logs)).

## Paging

Every list is paged by key and not by offset (see [Home page and paging](../database/database-design.md#home-page-and-paging)).

Request:

| Parameter | Meaning |
| --- | --- |
| `limit` | The page size. The default and the most is 10. On the product page `GET /products` the default is 16 and the most 24, because the person chooses 8, 16, or 24 (operating parameters) |
| `cursor` | An opaque string from an earlier response. Absent for the first page |
| `direction` | `next` (the default) or `prev` |
| `order` | `desc` (the default) or `asc`, only on the lists whose direction the user may choose (the ticket lists, see T-37). A list whose order is fixed reads in its own order, which is not always `desc`: the user page goes from A to Z |

Response:

```json
{ "items": [ ... ], "nextCursor": "...", "prevCursor": "..." }
```

- A parameter that is out of range is refused with `VALIDATION_FAILED` and is never corrected silently: a `limit` below 1 or above the page size, an unknown `direction` or `order`, a `direction` of `prev` without a `cursor`, an `order` on a list whose order is fixed, and a `cursor` that does not decode. The values are read without regard to case.
- A cursor is `null` when there is no page in that direction.
- There is no total count.
- The cursor holds the sort key of the row at the edge of the page and its `id`. The client never builds or reads one.
- A list that is filtered or searched keeps the same filters on every request. Changing a filter starts again without a cursor.
