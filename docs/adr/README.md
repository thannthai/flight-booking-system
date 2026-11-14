# Architecture Decision Records

Short records of the decisions that shape this system: the context, what was chosen, what it costs, and where the code and tests prove it. Behavior is described in [System Architecture](../02-system-architecture.md) and [Critical Flows](../05-critical-flows.md).

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-modular-monolith-with-enforced-boundaries.md) | Modular monolith with an enforced module graph | Accepted |
| [0002](0002-seat-holds-redis-lease-and-database-update.md) | Seat holds: fenced Redis lease plus a conditional database update | Accepted |
| [0003](0003-lock-order-and-no-locks-across-provider-calls.md) | Booking-then-payment lock order and no locks across provider calls | Accepted |
| [0004](0004-backend-owned-payment-deadline-and-webhook-confirmation.md) | Backend-owned payment deadline and webhook-only confirmation | Accepted |
| [0005](0005-refresh-cookie-via-same-origin-proxy.md) | Refresh cookie through a same-origin proxy with single-use rotation | Accepted |
| [0006](0006-redis-rate-limiting.md) | Redis fixed-window rate limiting that fails open | Accepted |
| [0007](0007-frontend-proxy-forwards-visitor-address.md) | The frontend proxy forwards the visitor address with a shared secret | Accepted |

Each file records the decision as made and is not rewritten when the code changes. A reversed decision gets a new record that supersedes the old one.
