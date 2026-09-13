# 06 Core Order Creation Flow

## Understanding To Build

Order creation is the most important backend logic in this project. It is not simply inserting an order row. It is a group of business actions that must remain consistent: validate product, check inventory, deduct inventory, create order, create order items, and record logs.

## Questions To Consider

Start with the success flow. When an order is created successfully, the order must exist, order items must exist, inventory must decrease, and logs should be recorded. If any part fails, the system should not leave half-successful data.

Then think about failure flows. What if the product does not exist? What if the product is delisted? What if inventory is insufficient? What if the database write fails? Treat these as normal business cases, not surprises.

The most important concept is transaction control. Inventory deduction and order creation should be in the same transaction to avoid "order created but inventory not deducted" or "inventory deducted but order not created."

Also think about concurrency. If two users buy the last unit of the same product at the same time, checking inventory first and deducting later may not be safe enough. You should learn database locks, optimistic locking, pessimistic locking, and conditional updates.

## What To Research

Useful keywords:

- Spring transaction order inventory consistency
- inventory deduction concurrent orders
- optimistic lock inventory version
- pessimistic lock select for update
- order status machine design

Do not rush to implement every solution. First understand the trade-offs. For a junior project, a transaction plus conditional inventory update can be a clear and reasonable option.

## How To Ask AI

Ask:

"My current order creation flow is: check inventory, deduct inventory, then create order. Please review it from the perspective of concurrency and transaction safety. What problems might happen, and what solution is suitable for a Spring Boot junior project?"

Another good prompt:

"Please design an order state machine for CREATED, PAID, CANCELLED, and FAILED. Which transitions should be allowed, and which should be rejected?"

## Expected Output

You should implement the create order API and verify:

- Normal order creation
- Order creation fails when inventory is insufficient
- Order creation fails when product is delisted
- Inventory is not incorrectly deducted when creation fails
- Order, order items, inventory, and logs remain consistent when creation succeeds

You should also write a short explanation of why order creation needs a transaction.

## Review Questions

- What does data consistency mean?
- Why should order and inventory changes be in one transaction?
- What can go wrong under concurrent order creation?
- If an interviewer asks how to prevent overselling, can I explain it clearly?
