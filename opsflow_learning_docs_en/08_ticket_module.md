# 08 Exception Ticket Module

## Understanding To Build

The ticket module turns your project from a normal order system into an operations system. Real companies do not assume systems always work perfectly. Failed orders, suspicious inventory changes, inconsistent payment status, and customer complaints all need follow-up. A ticket turns an exception into something that can be assigned, tracked, and closed.

## Questions To Consider

Think about where tickets come from. Tickets can be created manually, or they can be created automatically by the system. For example, if order creation fails and inventory state looks suspicious, the system can create an exception ticket for an Operator.

Then think about ticket states. OPEN means waiting for action. IN_PROGRESS means someone is handling it. RESOLVED means the issue has been solved. CLOSED means it has been confirmed and closed. Who can move a ticket from OPEN to IN_PROGRESS? Who can close it? Can a CLOSED ticket be reopened?

Also think about the relationship between tickets and orders. Not every ticket needs an order, but order-exception tickets should be traceable to a specific order. Tickets should include notes so others can understand the handling process.

## What To Research

Useful keywords:

- ticket management workflow
- incident ticket status lifecycle
- order exception handling workflow
- helpdesk ticket priority severity
- backend operations ticket system

Focus on state transitions and ownership, not only fields.

## How To Ask AI

Ask:

"I want to add an exception ticket module to my order system. From the perspective of a real backend operations team, please design ticket types, priority levels, state transitions, and permission control."

Another good prompt:

"Which order exceptions should automatically create tickets, and which should only return an error response to the user? Please help me distinguish them."

## Expected Output

You should implement:

- Create ticket
- Assign ticket owner
- Update ticket status
- Add handling notes
- Filter tickets by status, priority, and owner
- Automatically create a ticket for one simple order-exception scenario

You should also write a ticket state-transition note.

## Review Questions

- What business problem does a ticket solve?
- Why should ticket status not jump randomly?
- Can automatic ticket creation create too much noise?
- If an interviewer asks what value this feature has, how will I answer?
