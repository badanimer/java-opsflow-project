# 02 Database Design

## Understanding To Build

Database design is not just about creating tables. It is where business rules become structure. Whether orders, inventory, tickets, and logs can work reliably depends heavily on whether the database model is reasonable.

You need to understand why orders are usually split into an order table and an order item table, why inventory should not simply live inside the product table, why audit logs should not be casually modified, and why status fields affect later code complexity.

## Questions To Consider

Start with entity relationships. A user can create an order. An order contains multiple products. A product has inventory. An order exception can create a ticket. A user handling a ticket should leave operation logs. You should be able to draw these relationships.

Then think about field meaning:

- Does an order need a business order number?
- Should inventory distinguish available, locked, and sold quantity?
- Should tickets have priority?
- Should operation logs record both before and after values?
- Should deletion be physical deletion or soft deletion?

Also think about statuses. Order statuses should not be too many, but they cannot be too few. CREATED, PAID, CANCELLED, and FAILED are enough for the core flow. Ticket statuses can be OPEN, IN_PROGRESS, RESOLVED, and CLOSED. Think carefully about which status transitions are allowed.

## What To Research

Do not only search for "Spring Boot CRUD database design." Search for business modeling.

Useful keywords:

- order table order item table design
- inventory table design available locked quantity
- audit log table design
- ticket system database schema
- soft delete created_at updated_at best practice

After researching, compare other people's designs with yours. Which fields are necessary for the business? Which fields are only needed in larger systems?

## How To Ask AI

You can send your table draft to AI and ask:

"This is my MySQL schema for an order, inventory, and ticket management system. Please review it from the perspective of a real business system. Are the relationships reasonable? Am I missing any important fields? Is anything over-designed? Which fields will affect order and inventory consistency later?"

Another good prompt:

"I am not sure whether the inventory table should only have stock_quantity or whether I should split it into available_quantity, locked_quantity, and sold_quantity. Please explain the trade-off for a junior Java portfolio project."

## Expected Output

Create an SQL schema draft that includes:

- users
- roles
- user_roles
- products
- inventory
- orders
- order_items
- tickets
- operation_logs

Each table should have created_at and updated_at. For data that may be disabled, such as users or products, consider status or deleted fields.

## Review Questions

- Can I explain the difference between an order table and an order item table?
- Why is inventory better as a separate table?
- Which fields exist for querying and which exist for business safety?
- If order creation fails, can my schema support troubleshooting?
