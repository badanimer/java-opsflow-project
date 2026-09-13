# 11 RabbitMQ Asynchronous Tasks

## Understanding To Build

RabbitMQ is useful because it moves non-immediate work out of the main request flow. The order creation API should focus on creating the order and deducting inventory quickly and reliably. Notifications, follow-up tasks, inventory synchronization, timeout cancellation, and failure handling can be processed asynchronously.

## Questions To Consider

First distinguish synchronous and asynchronous work. Synchronous work is for things that must be known immediately, such as whether inventory is sufficient. Asynchronous work is for tasks that can happen later, such as sending notifications, creating operations reminders, and handling recovery tasks.

Then think about failure. What if message sending fails? What if the consumer fails? What if the same message is consumed twice? You do not need an industrial-grade MQ system, but you should know these problems exist.

Also control project complexity. Do not make everything asynchronous just because you added RabbitMQ. Order creation and inventory deduction should remain clear and reliable. MQ supports the process. It does not replace transactions.

## What To Research

Useful keywords:

- RabbitMQ order system async processing
- message queue retry dead letter queue
- Spring Boot RabbitMQ producer consumer
- order timeout cancel message queue
- idempotent message consumer

Focus on use cases and failure handling, not only producer/consumer sample code.

## How To Ask AI

Ask:

"In my order and inventory system, which actions should be synchronous and which actions should be handled asynchronously through RabbitMQ? Please classify them by business importance."

Another good prompt:

"What problems can happen if a consumer processes the same order message twice? Please explain how to avoid duplicate processing in a way suitable for a junior project."

## Expected Output

You should implement:

- Send a message after order creation
- Consumer for follow-up order tasks
- Simple failure retry or failure recording
- Create ticket or write log when processing fails
- README explanation of why MQ is used

If time is limited, implement one clear MQ scenario instead of many shallow ones.

## Review Questions

- What problem does RabbitMQ solve?
- Which logic should not be asynchronous?
- Does message failure affect the main order?
- Can I explain the difference between MQ and Redis?
