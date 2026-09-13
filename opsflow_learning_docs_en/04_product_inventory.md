# 04 Product And Inventory Module

## Understanding To Build

Product and inventory are the foundation of the order system. Product describes what is being sold. Inventory describes how much can still be sold. These two concepts should not be merged, otherwise order creation, inventory deduction, out-of-stock handling, and inventory adjustment will become messy.

## Questions To Consider

Think about the product lifecycle. A product is not only created, read, updated, and deleted. In real systems, products can be active, inactive, listed, or delisted. Ask yourself: can a product that already has orders be deleted? If not, how should you handle it?

Think about inventory safety. Inventory increases may come from restocking. Inventory decreases may come from orders, loss, or manual adjustments. Even if your project does not implement a full warehouse system, you should understand why inventory changes should be traceable.

Think about validation. Price cannot be below zero. Inventory cannot be below zero. Product name cannot be empty. Delisted products cannot be ordered. These are business rules, not just coding details.

## What To Research

Useful keywords:

- product inventory management system design
- stock adjustment audit trail
- Spring Boot validation annotation
- inventory deduction transaction
- soft delete product after orders

Many tutorials only teach CRUD. Pay extra attention to status, validation, and error handling.

## How To Ask AI

Ask:

"I am building product and inventory modules. Product and inventory are separate tables. Please list the business rules this module should enforce. Which rules must be enforced in the backend, and which can also be shown in the frontend?"

Another good prompt:

"If a product already has orders, what problems happen if I delete it? Please explain from an enterprise admin system perspective and suggest a solution suitable for a graduate project."

## Expected Output

You should finish product and inventory APIs and test them through Swagger:

- Create product
- Update product
- List products
- Get product details
- List and delist product
- Query inventory
- Increase inventory
- Deduct inventory

Also create a short business-rules note in README or docs.

## Review Questions

- Why separate product and inventory?
- Which inventory actions should be logged?
- Can a delisted product still be queried?
- What error should the system return when inventory is insufficient?
