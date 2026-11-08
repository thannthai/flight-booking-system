# Run and try the portfolio demo

This guide covers backend demo setup and the visitor flow. On an already configured site, visitors can select **Try demo** without creating an account.

## How the demo works

The shared demo account uses simulated payment without transferring money. Ordinary registered accounts still use live payOS, even when demo mode is enabled.

## Run the demo

Complete the database, Redis and JWT setup in [README](../README.md), then configure these backend environment variables:

```dotenv
SPRING_PROFILES_ACTIVE=demo
PAYMENT_MODE=DEMO
DEMO_PAYMENT_ALLOWED_JDBC_URL=<exact value of DB_URL>
DEMO_ACCOUNTS_ENABLED=true
DEMO_ACCOUNT_PASSWORD=<unique random secret of at least 16 characters>
```

Set these variables on the backend, then start the application as described in README. Keep the demo password in environment configuration rather than in source code.

Flight data must already be available; enabling demo payment does not create flights.

## Try the demo

1. Select **Try demo** to sign into the shared account.
2. Search and select a flight.
3. Use **Fill sample details** or enter fictional passenger information. Sample fill preserves existing values.
4. Choose seats, review the reservation and create the booking.
5. Select **Confirm Demo Payment**, then view tickets or My Bookings.

The demo QR is non-payable. Pending reservations can resume payment from My Bookings while their original deadline remains valid; cancelled or expired bookings cannot be completed.

Demo history is shared and is not reset on login. Use fictional details only. Generated flights and printed demo tickets are simulated records, not official airline inventory or boarding passes.
