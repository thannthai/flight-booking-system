# 0001. Modular monolith with an enforced module graph

**Status:** Accepted

## Context
Booking, payment and seat inventory must change together: a booking saves legs, tickets and seat claims atomically, and a payment confirmation updates payment, booking and tickets at once. The project has one developer and no need to release parts independently.

## Decision
One Spring Boot application with packages per module (`auth`, `flight`, `booking`, `payment`, ...) sharing one PostgreSQL database and transaction manager. Module boundaries are not left to convention: `ArchitectureTest` (ArchUnit) lists the dependencies each module may have and fails the build when a new one appears. It also forbids controllers from using repositories and from taking or returning entities.

## Consequences
- Local transactions keep reservation consistency simple; no distributed transaction or saga is needed.
- Shared entities and repositories mean modules cannot be released or scaled separately. `booking` and `payment` depend on each other, as do `flight` and the catalog modules; the test records these cycles rather than hiding them.
- A new cross-module dependency must be justified and added to the allowed map on purpose.

## Evidence
[`ArchitectureTest`](../../backend/src/test/java/com/thanh/backend/ArchitectureTest.java); module overview in [System Architecture, section 3](../02-system-architecture.md#3-backend-module-architecture).
