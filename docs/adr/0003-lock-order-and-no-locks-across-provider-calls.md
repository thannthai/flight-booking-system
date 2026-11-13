# 0003. Booking-then-payment lock order and no locks across provider calls

**Status:** Accepted

## Context
Payment creation, a payOS webhook, user cancellation and the expiry job can all touch the same booking and payment at the same moment. Without a fixed order they can deadlock, and a database lock held during a slow provider call would block every other writer for that booking.

## Decision
Whenever a flow locks both rows it locks the booking first and the payment second, and re-reads both after locking so it never acts on a stale copy. Payment-link creation is split into three steps: a short transaction creates a pending payment, the payOS call runs with no database lock held, and a second short transaction attaches the URL and QR data only if the payment is still pending. Webhook signature verification and provider cancellation also happen outside transactions.

## Consequences
- Concurrent webhooks, cancellations and expiry serialize on the booking row, so exactly one transition wins and duplicates become no-ops.
- A payment can be left half-initialized (pending, without a URL) if the provider call fails or times out. It is handled by reuse or reconciliation on the next request.
- Every new flow that touches both rows has to follow the order; reviewers should check it.

## Evidence
[`PaymentServiceImpl`](../../backend/src/main/java/com/thanh/backend/payment/service/impl/PaymentServiceImpl.java), [`BookingService.cancelBooking`](../../backend/src/main/java/com/thanh/backend/booking/service/BookingService.java), [`BookingPaymentCleanupScheduler`](../../backend/src/main/java/com/thanh/backend/payment/scheduler/BookingPaymentCleanupScheduler.java); `WebhookIdempotencyIT`, `CancelPaymentLifecycleIT`. Walkthrough in [Critical Flows](../05-critical-flows.md#2-payment-webhook).
