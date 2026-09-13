# 07 Redis Idempotency And Rate Limiting

## Understanding To Build

Redis is not included to make the project look fancy. It solves fast temporary state problems. In this project, Redis is most useful for duplicate submission prevention, rate limiting, short-lived tokens, and hot data caching.

## Questions To Consider

First understand idempotency. If a user clicks create order once or twice, the system should not create two duplicate orders. Duplicate submissions can come from repeated clicks, network retries, page refreshes, or repeated API calls.

Then understand the Redis token approach. The frontend first requests an order token. When creating the order, the frontend sends that token. The backend checks whether the token exists. If it exists, the backend deletes it and continues. If it does not exist, the request is rejected. The important point is that check-and-delete should be as atomic as possible.

Rate limiting also needs business context. Not every endpoint needs rate limiting. But order creation, login, and token generation are good candidates. Think about what response the user should receive when rate limited.

## What To Research

Useful keywords:

- idempotency key order API
- Redis setnx idempotency
- Redis Lua check and delete token
- rate limiting Redis fixed window
- Spring Boot RedisTemplate best practice

Separate the concepts clearly. Caching, idempotency, distributed locks, and rate limiting are different problems.

## How To Ask AI

Ask:

"I want to use Redis to prevent duplicate submissions in my order creation API. Please explain the full idempotency-token flow and point out where atomicity matters."

Another good prompt:

"I used Redis for duplicate submission prevention and rate limiting in my project. Please help me prepare an interview explanation of the real business problems Redis solves here."

## Expected Output

You should implement:

- API to request an order token
- Token validation during order creation
- Token invalidation after use
- Clear error response for duplicate submission
- Simple per-user rate limiting

Ideally, add a Redis usage note to your README explaining key patterns and expiration times.

## Review Questions

- What is idempotency?
- Why is Redis suitable for short-lived tokens?
- Why does token check-and-delete need atomicity?
- Who or what is rate limiting protecting?
