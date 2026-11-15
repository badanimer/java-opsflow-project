# API Iteration v1.7

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.7 |
| Date | 2026-11-15 |
| Baseline | [api-v1.6.md](api-v1.6.md) |
| Type | New endpoints and result codes, designed and then built in Phase 4 |
| Trigger | Phase 4: warehouse items and products (T-39, T-41, T-42, and the first part of T-46) |

## Summary

- Ten endpoints: the item list, the image upload, adding, editing, and deleting an item, the image itself, the product page, the catalog, listing, and delisting.
- Six result codes: `ITEM_NAME_TAKEN`, `ITEM_LISTED`, `ALREADY_LISTED`, `ALREADY_DELISTED`, `IMAGE_TOO_LARGE`, and `IMAGE_TYPE_NOT_ALLOWED`.
- Prices travel as strings with two decimals. Images are served without a token, under random names.

## Changes

| Area | Change |
| --- | --- |
| Warehouse Items, Images, and Products | New section: the endpoints, the rules of names, prices, starting quantities, and images, and the refusals of each change |
| Result codes | The six codes above |
| `UNSUPPORTED_MEDIA_TYPE` | Also a body that is not multipart on the image upload |
| Images | A multipart body without the part `file` is `VALIDATION_FAILED`; a name with an encoded slash is refused with `400` before any controller |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How an image reaches an item | Uploaded first by itself (`POST /item-images`), and its path then sent in the JSON of the item | The item and the file in one multipart request: every item endpoint would leave JSON, and a refused name would throw the upload away |
| Who may load an image | Everyone, under a random UUID name, with a long cache (the owner asked for the common commercial practice) | Only with a token: a browser cannot send the token with an `<img>`, so each image would have to be fetched by script, without the browser's cache |
| How a price is written | A string with at most two decimals | A JSON number: a client could round it on the way |
| Listing the first time and again | One endpoint, `POST /products` with the item, which creates the product or lists it again | Two endpoints for one action that the person sees as the same |
| A second listing, or a listing of an item already on sale | `ALREADY_LISTED` | `PRODUCT_CHANGED`, which belongs to orders |
| An edit of an item on sale | `ITEM_LISTED`, so the page can say "delist it first" | `FORBIDDEN`: the person may edit items, only not this one now |
| An item deleted meanwhile, or a second deletion | `NOT_FOUND`, as for a user | A code of its own: the page reloads the list either way |
| Paths | `/items` for the warehouse and `/products` for the shop, with the catalog at `/products/listable` | One path for both: the stock page and the product page ask different questions and need different permissions |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: Result Codes And Status Codes, Warehouse Items, Images, and Products (new), and the Change Log |
| Backend | Built in [backend-v1.9.md](backend-v1.9.md) |
| Frontend | The stock page scope in [frontend-v1.6.md](frontend-v1.6.md); not built |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- The frontend of these endpoints, on the branch `phase-4-frontend` (T-39, T-42, and the first part of T-46).
