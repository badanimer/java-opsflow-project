# 09 Operation Logs And Audit Trail

## Understanding To Build

Operation logs are highly valued in enterprise systems. They answer: who did what, to which object, when, and what changed before and after the operation. For orders, inventory, and tickets, traceability is extremely important when something goes wrong.

## Questions To Consider

First distinguish system logs and audit logs. System logs are usually for developers, such as error stack traces and API latency. Audit logs are for business and operational tracking. They record important business actions.

Then decide which actions must be recorded. Login can be recorded. Inventory adjustment must be recorded. Order status changes must be recorded. Ticket handling must be recorded. Ordinary queries do not always need audit logs, otherwise the log table becomes noisy.

Also think about log content. Recording only "update successful" is not very useful. A better log includes operator, operation type, object type, object ID, before status, after status, timestamp, IP address, and notes.

## What To Research

Useful keywords:

- audit log table design
- business audit trail system
- operation log Spring Boot AOP
- audit log before after value
- system log vs audit log

You can learn about AOP, but you do not have to use it immediately. Manually logging key business actions is acceptable for a first version.

## How To Ask AI

Ask:

"I want to add operation logs to my order and inventory system. Please help me distinguish which actions need audit logs and which only need system logs. Also help me design the log fields."

Another good prompt:

"I am not sure whether to write audit logs manually or use AOP. Please compare both approaches based on learning value and complexity for a junior Java project."

## Expected Output

You should implement:

- Operation log table
- Logging for key business actions
- Log query API
- Filtering by user, operation type, and time
- No normal delete API for logs

Prepare a demo scenario: adjust inventory, then open the log page to show who changed what.

## Review Questions

- What is the difference between audit logs and system logs?
- Why must inventory adjustment be recorded?
- Should audit logs be editable?
- If an order exception happens, can I reconstruct the process from logs?
