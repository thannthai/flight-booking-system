# 0004. Backend-owned payment deadline and webhook-only confirmation

**Status:** Accepted

## Context
A customer has a limited time to pay, and seats stay allocated until then. Browser timers can be paused or changed, redirects after checkout are only navigation, and payment notifications can arrive twice, late or out of order.

## Decision
The deadline is set once when the booking is created (15 minutes) and copied to the payment's expiry and to the payOS link. Only a verified webhook can mark a payment successful, and it is accepted only if the payment time is within the deadline, the amount matches exactly, and both booking and payment are still pending. Anything else is recorded as `PAID_AFTER_TERMINAL` with `REFUND_REQUIRED` and never reopens the booking. An expiry job cancels overdue bookings after a 90-second grace period. The frontend only reads persisted status; a return or cancel redirect never decides the outcome.

## Consequences
- A late scheduler run, a client timer or a replayed callback cannot extend the deadline or issue a second set of tickets.
- The customer may wait while the webhook arrives and the status poll sees it.
- Money received outside the rules is not refunded by the application; `REFUND_REQUIRED` needs an operational process.
- Simulated demo payments use a separate provider value and an authenticated confirmation endpoint, and the real webhook rejects them.

## Evidence
`createBooking`, `processWebhookPayment` and `confirmDemoPayment` in [`BookingService`](../../backend/src/main/java/com/thanh/backend/booking/service/BookingService.java) and [`PaymentServiceImpl`](../../backend/src/main/java/com/thanh/backend/payment/service/impl/PaymentServiceImpl.java); [`BookingPaymentCleanupScheduler`](../../backend/src/main/java/com/thanh/backend/payment/scheduler/BookingPaymentCleanupScheduler.java); the redirect rule in [`app/payment/[result]/page.tsx`](../../frontend/app/payment/%5Bresult%5D/page.tsx); `WebhookIdempotencyIT`, `DemoPaymentIT`.
