# 0002. Seat holds: fenced Redis lease plus a conditional database update

**Status:** Accepted

## Context
A seat must be reserved for a limited time while a customer enters details, and two customers must never end up with the same seat. A hold must expire on its own, retries must not extend it, and a slow or stale request must not release a hold that now belongs to someone else.

## Decision
Holding a seat does two things in one transaction. Redis stores an expiring lease whose value is the user id and a random token; the lease is acquired atomically with `SET NX`, and a hold that is still live is reused with its original deadline instead of being extended. PostgreSQL then performs a conditional `UPDATE` that succeeds only for an available seat, an expired hold, or the same user's identical live hold. The database row decides ownership; Redis provides fast admission and a fencing token. Leases are released only by compare-and-delete on the token, and a hold is rechecked after all updates and again before the booking commits.

## Consequences
- Double allocation is prevented by the row update even if Redis misbehaves.
- Cleanup from an older request cannot delete a newer lease, because the token must match.
- Redis is an operational dependency for holding seats; an outage blocks new holds even while PostgreSQL is up.
- Redis and PostgreSQL do not commit together. After a rollback, the candidate lease is cleaned up best-effort and otherwise expires, so a lease can briefly outlive the database state.

## Evidence
[`SeatHoldLeaseStore`](../../backend/src/main/java/com/thanh/backend/booking/infrastructure/redis/SeatHoldLeaseStore.java), [`SeatHoldTransactionLifecycle`](../../backend/src/main/java/com/thanh/backend/booking/service/SeatHoldTransactionLifecycle.java), `acquireHoldIfAvailable` and `claimHeldSeatForBooking` in [`FlightSeatRepository`](../../backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java); real-service concurrency tests `RedisLeaseConcurrencyIT`, `SeatTransitionConcurrencyIT`, `BookingConcurrencyIT`. Walkthrough in [Critical Flows](../05-critical-flows.md#1-seat-hold-and-booking-creation).
