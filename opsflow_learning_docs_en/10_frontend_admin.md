# 10 Frontend Admin Interface

## Understanding To Build

The frontend does not need to be flashy. Its purpose is to help interviewers understand your backend work. A clear admin interface lets you demonstrate login, orders, inventory, tickets, and logs as a complete workflow.

## Questions To Consider

Start with page structure. You need a login page, Dashboard, product management, inventory management, order list, ticket list, and operation logs. Do not create too many pages. Every page should support your project demo.

Then think about API interaction. The frontend should send the token with backend requests. If it receives 401, it should return to login. If it receives 403, it should show an insufficient-permission message. List pages should support status filtering. Detail pages should show important fields.

Also think about admin UI style. Do not make it a marketing website. Enterprise admin systems should be clear, dense, stable, and easy to scan. Focus on tables, filters, status tags, and action buttons. Clean and usable is enough.

## What To Research

Useful keywords:

- admin dashboard UI order management
- Vue axios JWT interceptor
- React admin table filter status
- enterprise admin UI layout
- permission based frontend routes

You can look at Ant Design or Element Plus examples to understand common admin layouts.

## How To Ask AI

Ask:

"I am building an enterprise admin system, not a marketing website. Please design the frontend information architecture, including sidebar navigation, Dashboard metrics, table filters, and detail-page content."

Another good prompt:

"Here is my backend API list. Please help me plan which frontend pages should call which APIs, which pages need filters, and which actions need permission messages."

## Expected Output

You should complete a frontend that can demonstrate the workflow:

- Login
- Dashboard metrics
- Product list
- Inventory management
- Order list
- Ticket list
- Operation logs

It does not need to be visually fancy, but it must be clear, usable, and demo-friendly.

## Review Questions

- Does the frontend help me explain the project?
- Which actions require permission control?
- Are Dashboard metrics coming from real backend data?
- Can I demonstrate the complete workflow in three minutes?
