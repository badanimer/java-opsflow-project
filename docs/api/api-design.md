# OpsFlow API

## Purpose

This document describes the conventions of the OpsFlow HTTP API as they are decided now: the paths, the data formats, the error response, the result codes, the status codes, and the paging. It is the current-state document of the API area. It does not list the endpoints one by one: the endpoints follow the modules in [database-design.md](../database/database-design.md) and the pages in [page-design.md](../frontend/page-design.md), and each module adds its own when it is built. The backend that serves the API is described in [backend-design.md](../backend/backend-design.md).

## Change Log

This document always describes the current API conventions. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
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
| Authentication | The header `Authorization: Bearer <token>`. The token carries only the user id and its issue time (see T-20 in [project-todo.md](../project-todo.md)). |
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
| 401 | `UNAUTHENTICATED` | No token, an invalid or expired token, or a token issued before `password_changed_at` | General |
| 403 | `FORBIDDEN` | The user is logged in and the Permission Matrix does not allow the action | General |
| 403 | `NOT_ALLOWED` | The account is not usable or its role does not allow orders or tickets | Order and ticket results |
| 403 | `ACCOUNT_DISABLED` | The account is disabled. The pending page shows it | [Users without roles](../database/database-design.md#users-without-roles) |
| 403 | `ACCOUNT_NO_ROLE` | The account has no role yet. The pending page shows it | [Users without roles](../database/database-design.md#users-without-roles) |
| 404 | `NOT_FOUND` | The resource does not exist, or the user may not know that it exists | General |
| 404 | `ORDER_NOT_FOUND` | The order number named on a ticket is not in `order_attempts` | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 404 | `FOLLOW_NOT_FOUND` | The ticket to follow does not exist | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 405 | `METHOD_NOT_ALLOWED` | The path exists but not for this HTTP method | General |
| 409 | `OUT_OF_STOCK` | An item is short | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `NO_STOCK` | An item with no stock cannot be listed | [Listing and delisting](../database/database-design.md#listing-and-delisting) |
| 409 | `PRODUCT_DELISTED` | The product is not on sale | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `PRODUCT_CHANGED` | The name or price differs from the one the user saw | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 409 | `ITEM_CHANGED` | A chosen item no longer exists. The body names them | [Creating a ticket on items](../database/database-design.md#creating-a-ticket-on-items) |
| 409 | `FOLLOW_NOT_ALLOWED` | The ticket to follow is not `RESOLVED` or `CLOSED` | [Follow-up tickets](../database/database-design.md#follow-up-tickets) |
| 409 | `LIMIT_REACHED` | The person already has 99 open tickets, or 99 in progress | [Ticket limits](../database/database-design.md#ticket-limits) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The body is not JSON | General |
| 429 | `LOGIN_LOCKED` | The account is locked after failed logins | [Login security and account recovery](../database/database-design.md#login-security-and-account-recovery) |
| 429 | `TOO_MANY_REQUESTS` | A code was requested again within 60 seconds | General |
| 503 | `BUSY` | The retries on a deadlock or a lock wait were used up. Nothing was created | [Order and Inventory Flow](../database/database-design.md#order-and-inventory-flow) |
| 500 | `INTERNAL_ERROR` | An unexpected error. The body carries no detail | General |

Rules for choosing a status:

- `409` is for a conflict with the current state that a refresh and a new attempt can fix. It is not used for a bad request.
- The login endpoint answers a wrong username and a wrong password with the same `401` and never says which one was wrong. A locked account gets `LOGIN_LOCKED` without the password being checked (see T-28).
- A new module that needs a code adds it to this table in the same change.

## Paging

Every list is paged by key and not by offset (see [Home page and paging](../database/database-design.md#home-page-and-paging)).

Request:

| Parameter | Meaning |
| --- | --- |
| `limit` | The page size. The default and the most is 10 (an operating parameter) |
| `cursor` | An opaque string from an earlier response. Absent for the first page |
| `direction` | `next` (the default) or `prev` |
| `order` | `desc` (the default) or `asc`, only on the lists whose direction the user may choose (the ticket lists, see T-37) |

Response:

```json
{ "items": [ ... ], "nextCursor": "...", "prevCursor": "..." }
```

- A parameter that is out of range is refused with `VALIDATION_FAILED` and is never corrected silently: a `limit` below 1 or above the page size, an unknown `direction` or `order`, a `direction` of `prev` without a `cursor`, an `order` on a list whose order is fixed, and a `cursor` that does not decode. The values are read without regard to case.
- A cursor is `null` when there is no page in that direction.
- There is no total count.
- The cursor holds the sort key of the row at the edge of the page and its `id`. The client never builds or reads one.
- A list that is filtered or searched keeps the same filters on every request. Changing a filter starts again without a cursor.
