# OpsFlow API

## Purpose

This document describes the conventions of the OpsFlow HTTP API as they are decided now: the paths, the data formats, the error response, the result codes, the status codes, and the paging. It is the current-state document of the API area. It does not list the endpoints one by one: the endpoints follow the modules in [database-design.md](../database/database-design.md) and the pages in [page-design.md](../frontend/page-design.md), and each module adds its own when it is built. The backend that serves the API is described in [backend-design.md](../backend/backend-design.md).

## Change Log

This document always describes the current API conventions. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
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
| Limits | The length limit of every text field equals the length of its database column, so that over-long input is refused with `VALIDATION_FAILED` and never truncated (T-13). |

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
| 403 | `NOT_ALLOWED` | The account is not usable or its role does not allow orders or tickets | Order and ticket results |
| 403 | `ACCOUNT_DISABLED` | The account is disabled. At login it is the answer to the right password, with no token. On any other request it means the account was disabled after login. The frontend clears its login state and shows the message on the login page | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 403 | `ACCOUNT_NO_ROLE` | The account has no role yet, and the endpoint needs one. The pending page shows it | [Users without roles](../database/database-design.md#users-without-roles) |
| 404 | `NOT_FOUND` | The resource does not exist, or the user may not know that it exists. In a change to a user: the user does not exist, is soft-deleted, or is the SYSTEM identity | General |
| 404 | `ORDER_NOT_FOUND` | The order number named on a ticket is not in `order_attempts` | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 404 | `FOLLOW_NOT_FOUND` | The ticket to follow does not exist | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 405 | `METHOD_NOT_ALLOWED` | The path exists but not for this HTTP method | General |
| 409 | `OUT_OF_STOCK` | An item is short | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `NO_STOCK` | An item with no stock cannot be listed | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ALREADY_LISTED` | The item is listed now, so it cannot be listed again | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ALREADY_DELISTED` | The product is not listed now, so it cannot be delisted | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `ITEM_NAME_TAKEN` | Another item that is not deleted has this name, without regard to case | [Warehouse items](../database/database-design.md#warehouse-items) |
| 409 | `ITEM_LISTED` | The item is on sale, so its name and image cannot be changed; delist it first | [Warehouse items](../database/database-design.md#warehouse-items) |
| 409 | `PRODUCT_DELISTED` | The product is not on sale | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `PRODUCT_CHANGED` | The name or price differs from the one the user saw | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `ITEM_CHANGED` | A chosen item no longer exists. The body names them | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 409 | `FOLLOW_NOT_ALLOWED` | The ticket to follow is not `RESOLVED` or `CLOSED` | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 409 | `LIMIT_REACHED` | The person already has 99 open tickets, or 99 in progress | [Ticket limits](../database/database-design.md#ticket-limits) |
| 409 | `USER_CHANGED` | The user is not as the sender saw it: someone else changed the roles, or disabled or enabled the account, in which case the body carries the current status as `userStatus`. Nothing is changed, and the page reloads the user | [Steps of a change to a user](../database/database-design.md#steps-of-a-change-to-a-user) |
| 409 | `USER_NOT_ACTIVE` | A role is to be given to a disabled user. Roles can still be taken away. The user is enabled first | [Role grants](../database/database-design.md#role-grants) |
| 409 | `LAST_ADMIN` | The change would leave the system with no usable administrator | [Administrator protection](../database/database-design.md#administrator-protection) |
| 409 | `USERNAME_TAKEN` | At sign-up, the username belongs to an account. The code is not used up, so the person picks another username and sends the same code | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 409 | `EMAIL_TAKEN` | The address already has an account. It is the answer to a request for a sign-up code, and to a sign-up whose address got an account after the code was mailed. The page says that the address is registered | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 413 | `IMAGE_TOO_LARGE` | The uploaded image is larger than 2 MB | [Item images](../database/database-design.md#item-images) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The body is not JSON (not multipart, on the image upload) | General |
| 429 | `LOGIN_LOCKED` | The account is locked after failed logins. The body carries `lockedUntil`, the time the lock ends | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 429 | `TOO_MANY_REQUESTS` | A request for an email code was refused by a limit: the same address and purpose within 60 seconds, more than 10 requests of one client address in an hour, or more than 200 requests of the whole system in a day. The answer does not say which | General |
| 503 | `BUSY` | The retries on a deadlock or a lock wait were used up. Nothing was created | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 500 | `INTERNAL_ERROR` | An unexpected error. The body carries no detail | General |

Rules for choosing a status:

- `409` is for a conflict with the current state that a refresh and a new attempt can fix. It is not used for a bad request.
- The login endpoint answers a wrong username and a wrong password with the same `401` and never says which one was wrong. A locked account gets `LOGIN_LOCKED` without the password being checked.
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
| `POST /auth/password-reset` | Everyone | `{ "email": "...", "code": "123456", "newPassword": "..." }` | `204` with no body. Every token issued before it stops working, and the lock of failed logins is cleared. `CODE_INVALID` or `VALIDATION_FAILED` otherwise |

- `expiresAt` is 12 hours after the login. After it the token gets `UNAUTHENTICATED`, and the user logs in again.
- The refusals of the login are `UNAUTHENTICATED` (a wrong username or password), `LOGIN_LOCKED` with `lockedUntil`, and `ACCOUNT_DISABLED`, as in the rules above.
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
| `PUT /users/{id}/roles` | A user with the permission `ROLE_ASSIGN` (an administrator or a manager), on another user | `{ "roles": ["OPERATOR", "VIEWER"], "expectedRoles": ["VIEWER"] }`. `roles` is the whole list the user is to hold afterwards, and an empty list leaves the user with no role. `expectedRoles` is the list the sender saw on the screen | `200` with `{ "id": 9, "username": "bob", "roles": ["OPERATOR", "VIEWER"] }`, the roles the user holds now, in the order of the codes |
| `POST /users/{id}/disable` | A user with the permission `USER_MANAGE`, on another user | None | `200` with `{ "id": 9, "username": "bob", "status": "DISABLED" }` |
| `POST /users/{id}/enable` | The same | None | `200` with the same fields and `"status": "ACTIVE"` |
| `DELETE /users/{id}` | The same | None | `204`. The deletion is soft: the account is marked as removed and disabled, and its row, its roles, its username, and its email stay |

- `GET /users` lists only the users the caller may manage, as [User page](../database/database-design.md#user-page) says: an administrator sees every user, their own account included, and a manager the users who hold neither `ADMIN` nor `MANAGER`, not their own account. Disabled users and users with no role are listed; soft-deleted users and the SYSTEM identity never are. The rows are ordered by username from A to Z, and then by `id`. The order is fixed, so `order` is refused.
- `search` is a part of the username, matched without regard to case; it is at most 64 characters, and an empty or blank value is no search. `roles` holds the chosen role buttons, as role codes and `NONE` for No role, comma-separated (`roles=OPERATOR,NONE`) or repeated; the codes are read without regard to case, an unknown one is `VALIDATION_FAILED`, and an empty value is no button. With buttons chosen, a user who holds any one of the chosen roles is listed, and with `NONE` also a user who holds none. The two filters combine, and neither widens what the caller may see.
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

Built on 2026-11-15 for Phase 4 ([api-v1.7.md](../iterations/api-v1.7.md)). The rules behind them are in [Warehouse items](../database/database-design.md#warehouse-items), [Item images](../database/database-design.md#item-images), and [Listing and delisting](../database/database-design.md#listing-and-delisting). The stock page of Phase 4 lists the items and adds, edits, and deletes them; changing a quantity and the logs come in Phase 5, and so does the Order button.

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

## Paging

Every list is paged by key and not by offset (see [Home page and paging](../database/database-design.md#home-page-and-paging)).

Request:

| Parameter | Meaning |
| --- | --- |
| `limit` | The page size. The default and the most is 10 (an operating parameter) |
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
