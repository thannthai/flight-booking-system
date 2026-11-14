# Flight Booking System — Project Specification

## 1. Overview

Flight Booking System is a portfolio application for searching flights, reserving seats, paying through payOS and managing personal bookings. The customer interface is branded **Skyway**. It supports domestic and international airport data, one-way journeys and round trips.

The central challenge is keeping reservations reliable when users compete for seats and payment confirmation arrives asynchronously. The backend determines availability, price, ownership and payment outcome; browser navigation alone cannot confirm a reservation.

## 2. Objectives and scope

Its scope includes flight discovery, passenger details, seat holds, checkout, payment status, personal booking history and a passenger directory. Administrators manage catalog and flight inventory through APIs; an admin dashboard is outside the current frontend scope.

The application manages its own inventory. Flights labelled `DEMO` are simulated; there is no live airline reservation or airport check-in integration. Printed e-tickets are demo booking confirmations, not airline boarding passes.

## 3. Actors and permissions

| Actor | Responsibilities and access |
| --- | --- |
| Guest | Search flights and prepare checkout choices; sign in before holding seats or creating a booking. |
| Customer (`ROLE_USER`) | Manage own passengers, bookings and payments; view tickets and cancel eligible unpaid reservations. |
| Administrator (`ROLE_ADMIN`) | Manage catalog/flights; view other users' bookings and cancel them while pending. Payment actions still require booking ownership. |
| payOS | Provide checkout links and send payment notifications verified by the backend. |

Registration creates a customer account. Resource ownership and administrative permissions are enforced by the backend.

## 4. Functional scope and implementation status

Status describes the current implementation. Deployed provider integration requires separate verification.

| Area | Functionality | Status |
| --- | --- | --- |
| Authentication | Register, login, logout and restore sessions through refresh. | Implemented |
| Flight search | Preview the selected route on a map; search routes/dates; filter by airline, maximum base fare and departure period; sort by price, departure or duration. | Implemented |
| Journey selection | Choose one-way or round-trip flights and economy/business cabin. | Implemented |
| Passenger details | Enter contact/traveler information, assign infant guardians and choose add-ons. | Implemented |
| Seat selection | Select or auto-assign seats on each leg; hold seats after sign-in. | Implemented |
| Booking | Confirm a reservation with a PNR, backend-calculated total and pending tickets. | Implemented |
| Payment | Display the returned QR/checkout link, recover payment after reload and show backend-confirmed status. | Implemented; deployed provider integration Not Verified |
| History and tickets | Review personal bookings, resume payment, display/print demo e-tickets and cancel pending reservations. | Implemented |
| Passenger directory | Save, edit, quickfill and remove own passenger entries. Document-only removal is unsupported. | Implemented within this scope |
| Demo | Try the booking flow through one shared account and simulated payment. | Implemented; enabled explicitly on demo/dev backends |
| Administration | Maintain airports, airlines, aircraft and flights through APIs. | Implemented |
| Notifications | Email and in-app notifications; confirmation is shown in the web app instead. | Planned; not implemented |
| Voucher/refund workflows | Voucher validation, completed-booking cancellation approval and refund execution. | Planned; no complete workflow |

## 5. Main user flows

### Sign in and manage a session

A guest registers or signs in; Try demo uses one shared demo account. The application restores the session when possible after reload. Logout and account changes clear private checkout data so another account's information is not displayed.

### Search, reserve and pay

1. Select airports, dates, passenger counts and cabin.
2. Compare flights and select an outbound flight plus a return flight when applicable.
3. Enter passenger/contact details and optional add-ons.
4. Select seats, sign in if needed and obtain a hold.
5. Explicitly confirm the booking and receive its reference and final total.
6. Pay using the supplied QR or payOS checkout, then view confirmation and tickets after backend verification.

The shared demo account confirms a simulated payment instead of transferring money through payOS; ordinary registered accounts keep the live payment flow. The route map illustrates the airport connection, not a live aircraft position or operational flight path.

If a booking request's outcome is unknown, the application directs the user to My Bookings instead of automatically submitting it again. Payment status can be recovered after reload; returning from payOS does not itself confirm payment.

### Manage bookings and saved passengers

Customers can view booking history, resume an eligible pending payment, print confirmed tickets or cancel an unpaid reservation. The passenger directory reduces repeated entry and holds only travelers a customer chose to save; removing or editing an entry never changes who an issued ticket belongs to.

## 6. Key business rules

| Area | Current rule |
| --- | --- |
| Journey | One-way bookings contain one flight; round trips contain two distinct flights. The UI searches the reversed route for return travel. |
| Passenger category | Age is calculated on the first flight's serialized departure date: adult ≥12, child 2–11, infant <2. |
| Infant guardian | Each infant requires a distinct accompanying adult and does not occupy a seat. |
| Documents | Adults need an identity document. Children/infants may omit it; an infant's supplied document must match the guardian's. Duplicate adult/child documents within a booking are rejected. |
| Party size | The customer UI supports at least one adult and at most nine seated passengers, excluding lap infants. |
| Seat ownership | Each adult/child needs one seat per leg, with a valid hold owned by the authenticated user. |
| Hold expiry | Holds last 15 minutes under current configuration; retrying a live hold does not extend its deadline. |
| Fare | Adults pay selected seat price; children 75%; infants 10% of flight base price per leg. Tax is 10% of fares plus add-ons; no voucher discount is currently applied. |
| Add-ons | Baggage packages, meals and insurance use backend-defined prices. The frontend applies selected add-ons to outbound adult/child tickets. |
| Payment deadline | Payment is due 15 minutes after booking creation, separately from the earlier seat hold. |
| Confirmation | A verified, eligible payment receipt completes the booking and issues tickets. Duplicate notifications must not duplicate issuance. |
| Cancellation | Customer cancellation applies to pending unpaid bookings. A late or mismatched receipt enters refund review without reopening the reservation. |

Prices shown in search are adult base fares; checkout uses the backend-confirmed total. Departure/arrival times use their respective airport timezones. Passport/visa eligibility rules are outside the implemented validation scope.

## 7. References

See [System Architecture](02-system-architecture.md) for component interaction and [Database Design](03-database-design.md) for stored relationships. Exact request contracts are defined by the [backend DTOs](../backend/src/main/java/com/thanh/backend/booking/dto/request/).
