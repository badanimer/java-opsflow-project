# 05 Authentication And Role-Based Access Control

## Understanding To Build

Permissions are not there to make the project look advanced. They are a basic security requirement for enterprise admin systems. Orders, inventory, tickets, and logs should not be editable by everyone. You need to understand the difference between authentication and authorization. Authentication answers "who are you?" Authorization answers "what are you allowed to do?"

## Questions To Consider

Think about the login flow. The user submits username and password. The backend verifies them and returns a JWT. Later requests include the token. The backend parses the token and identifies the user and roles.

Then think about role permissions. Admin, Operator, and Viewer should do different things. Design permission boundaries for each module: who can modify inventory? Who can cancel orders? Who can view audit logs? Who can assign tickets?

Also consider security details. Passwords must not be stored in plain text. What happens when a token expires? Requests without a token should return 401. Requests with insufficient permission should return 403. You do not need a perfect enterprise security system, but these concepts should be clear.

## What To Research

Useful keywords:

- Spring Security JWT authentication flow
- RBAC role based access control
- password hashing BCrypt Spring Security
- 401 vs 403 difference
- JWT expiration refresh token simple design

Focus on understanding the flow. Spring Security configuration can be confusing, so ask AI to explain it using your project structure.

## How To Ask AI

Ask:

"Please explain the complete Spring Security + JWT request flow using my project scenario, from login to accessing the order creation API. What does the backend do at each step?"

Another good prompt:

"My roles are Admin, Operator, and Viewer. Please design a permission matrix for product, inventory, order, ticket, and audit log modules, and point out which permission mistakes would create business risk."

## Expected Output

You should implement:

- Login API
- JWT generation and validation
- Password hashing
- Protected endpoints
- Role-based permission control
- 401 and 403 error handling

You should also create a permission matrix, even if it is only a Markdown table.

## Review Questions

- What is the difference between authentication and authorization?
- What should be stored in a token, and what should not?
- Why should Viewer not modify inventory?
- What risks appear if a token is stolen?
