# System Design - SilverStage Event Ticketing & Governance

## 1. Architectural Overview
SilverStage is a Spring Boot application utilizing a layered architecture for robustness and maintainability.

- **Presentation Layer**: REST Controllers handling HTTP requests and response mapping.
- **Service Layer**: Business logic implementation across 10+ core services (e.g., `TicketingService`, `ModerationService`).
- **Data Access Layer**: MyBatis SQL Mappers for atomic database operations.
- **Persistence Layer**: MySQL for transactional data and H2 for integration testing.

---

## 2. Venue & Inventory Hierarchy
The system models physical venues with a 6-tier rigid hierarchy:
`Event -> Season -> Session -> Stand -> Zone -> Seat`

### Inventory Governance
- **Atomic Units**: Individual `Seat` records are the atomic units of inventory.
- **Quota Management**: Seats are allocated between `ONLINE_PORTAL` and `BOX_OFFICE` channels.
- **Dynamic Capacity**: Ticket types (General, Senior, Family) are associated with events and validated against session-level inventory.

---

## 3. Ticketing Workflow & State Machine
- **Seat States**: `AVAILABLE` -> `HELD` (User selection) -> `RESERVED` (Paid).
- **Hold Logic**: A 15-minute hold is applied when a `TicketOrder` is created.
- **Payment Grace Period**: Users have 30 minutes to pay before an order is auto-cancelled.
- **Batch Processing**: `@Scheduled` tasks run every 60 seconds to release expired holds and return inventory from cancelled orders.

---

## 4. Content & Document Governance
- **Publishing Lifecycle**: `DRAFT -> SUBMISSION -> REVIEW -> PUBLISHED`.
- **Versioning**: Every update creates a new `ContentVersion`. The system supports side-by-side diffing and rollback to any previous version.
- **Appeals System**: Rejected or moderated content owners can submit appeals for manual review by Moderators.
- **Secure File Access**: `ManagedDocument` records use 72-hour expiring tokens for secure, audited downloads.

---

## 5. Security & Identity
- **RBAC**: Multi-tier permissions for `SENIOR`, `FAMILY_MEMBER`, `SERVICE_STAFF`, `ORG_ADMIN`, and `PLATFORM_ADMIN`.
- **Real-Name Verification**: `UserIdentityVerification` workflow requires manual approval before granting elevated privileges.
- **Authentication**: Token-based authentication via `X-Auth-Token`.
- **Auditability**: `OperationTrace` logs every critical action, including financial transactions and administrative changes.

---

## 6. Financial Reconciliation & Settlement
- **Tender Management**: Support for `CASH`, `CHECK`, and `TERMINAL_BATCH`.
- **Settlement Imports**: Idempotent processing of daily CSV settlement files from payment gateways.
- **Revenue Sharing**: Automated split calculation: **10% Platform / 90% Merchant**.
- **Traceability**: All payment-related anomalies (e.g., amount mismatches) are logged in the `ReconciliationException` registry for manual audit.
