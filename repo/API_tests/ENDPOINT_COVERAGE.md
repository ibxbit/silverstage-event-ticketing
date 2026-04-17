# Endpoint Coverage Map

This document maps every REST endpoint exposed by the SilverStage application to the test file(s)
and specific test methods that exercise it.

Legend:
- Integration Test = Spring MockMvc test in `src/test/java/.../api/`
- API Functional Test = black-box test in `API_tests/`
- Auth Check = 401/403 boundary tested
- Validation = request-body or param validation tested

---

## Security (`/api/security`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/security/accounts` | `SecurityApiIntegrationTest` | `SecurityApiFunctionalTests#testRegisterSuccess` | — | Yes |
| POST | `/api/security/login` | `SecurityApiIntegrationTest` | `SecurityApiFunctionalTests#testLoginSuccess` | — | Yes |
| GET | `/api/security/menu` | `SecurityApiIntegrationTest` | `SecurityApiFunctionalTests#testMenuReturnsMenus` | — | — |
| POST | `/api/security/verification` | `SecurityApiIntegrationTest` | `SecurityApiFunctionalTests#testSubmitVerificationAuthenticated` | Yes (401 without token) | Yes |
| GET | `/api/security/verification/pending` | `SecurityApiIntegrationTest` | `SecurityApiFunctionalTests#testPendingVerificationsRequiresAdmin` | Yes (401 without token, admin required) | — |
| PATCH | `/api/security/verification/{verificationId}` | `SecurityApiIntegrationTest` | — | Yes | Yes |

---

## Discovery (`/api/discovery`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| GET | `/api/discovery/suggestions` | `DiscoveryApiIntegrationTest` | `DiscoveryApiFunctionalTests#testSuggestionsReturnsResults` | — | — |
| GET | `/api/discovery/search` | `DiscoveryApiIntegrationTest` | `DiscoveryApiFunctionalTests#testSearchReturnsItems` | — | — |
| GET | `/api/discovery/browse/seasons` | `DiscoveryApiIntegrationTest` | `DiscoveryApiFunctionalTests#testBrowseSeasons` | — | — |
| GET | `/api/discovery/browse/sessions` | `DiscoveryApiIntegrationTest` | `DiscoveryApiFunctionalTests#testBrowseSessions` | — | — |
| GET | `/api/discovery/browse/announcements` | `DiscoveryApiIntegrationTest` | `DiscoveryApiFunctionalTests#testBrowseAnnouncements` | — | — |

---

## Events (`/api/events`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| GET | `/api/events` | `EventsApiIntegrationTest` | `EventsApiFunctionalTests#testListEvents` | — | — |
| POST | `/api/events` | `EventsApiIntegrationTest` | `EventsApiFunctionalTests#testCreateEventWithAdminToken` | Yes (401 without token) | Yes |
| GET | `/api/events/{eventId}/hierarchy` | `EventsApiIntegrationTest` | `EventsApiFunctionalTests#testGetEventHierarchy` | — | — |

---

## Ticketing (`/api/events/{eventId}/ticket-types`, `/api/tickets`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/events/{eventId}/ticket-types` | `TicketingApiIntegrationTest` | `TicketingApiFunctionalTests#testCreateTicketTypeWithAdmin` | Yes (401 without token, admin required) | Yes |
| GET | `/api/events/{eventId}/ticket-types` | `TicketingApiIntegrationTest` | `TicketingApiFunctionalTests#testListTicketTypes` | — | — |
| POST | `/api/tickets/reservations` | `TicketingApiIntegrationTest` | `TicketingApiFunctionalTests#testReserveTicketsWithAuth` | Yes (401 without token) | Yes |

---

## Seat Reservation (`/api/sessions`, `/api/seat-orders`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| GET | `/api/sessions/{sessionId}/seat-map` | `SeatReservationApiIntegrationTest` | `ApiFunctionalTests#seatReservationFlow` | — | — |
| POST | `/api/seat-orders` | `SeatReservationApiIntegrationTest` | `ApiFunctionalTests#seatReservationFlow` | Yes (role required) | Yes |
| POST | `/api/seat-orders/{orderId}/pay` | `SeatReservationApiIntegrationTest` | — | Yes (role required) | — |

---

## Moderation (`/api/moderation`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/moderation/reports` | `ModerationApiIntegrationTest` | `ModerationApiFunctionalTests#testSubmitReport` | Yes (401 without token) | Yes |
| GET | `/api/moderation/reports` | `ModerationApiIntegrationTest` | `ModerationApiFunctionalTests#testGetReportsWithAdmin` | Yes (401 without token, admin/moderator required) | — |
| POST | `/api/moderation/reports/{reportId}/decision` | `ModerationApiIntegrationTest` | `ModerationApiFunctionalTests#testDecideReport` | Yes (admin/moderator required) | Yes |
| GET | `/api/moderation/users/{username}/penalties` | `ModerationApiIntegrationTest` | `ModerationApiFunctionalTests#testGetPenalties` | Yes (self or admin) | — |
| GET | `/api/moderation/users/{username}/notifications` | `ModerationApiIntegrationTest` | `ModerationApiFunctionalTests#testGetNotifications` | Yes (self or admin) | — |
| PATCH | `/api/moderation/notifications/{notificationId}/read` | `ModerationApiIntegrationTest` | — | Yes (authenticated) | — |

---

## File Management (`/api/files`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/files/upload` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testUploadDocument` | Yes (staff/admin required) | Yes |
| POST | `/api/files/{documentId}/versions` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testUploadVersion` | Yes (staff/admin required) | — |
| GET | `/api/files` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testListDocuments` | Yes (authenticated) | — |
| GET | `/api/files/{documentId}/history` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testGetHistory` | Yes (authenticated) | — |
| POST | `/api/files/{documentId}/download-links` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testGenerateDownloadLink` | Yes (authenticated) | — |
| GET | `/api/files/download/{token}` | `FileManagementApiIntegrationTest` | `FilesApiFunctionalTests#testDownloadByToken` | Yes (authenticated) | — |

---

## Publishing (`/api/publishing`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/publishing/content` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testCreateDraft` | Yes (authenticated) | Yes |
| GET | `/api/publishing/content` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testListContent` | Yes (authenticated) | — |
| POST | `/api/publishing/content/{contentId}/update` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testUpdateDraft` | Yes (owner or admin) | Yes |
| POST | `/api/publishing/content/{contentId}/submit` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testSubmitContent` | Yes (owner or admin) | — |
| POST | `/api/publishing/content/{contentId}/review` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testReviewContent` | Yes (moderator/admin required) | — |
| POST | `/api/publishing/content/{contentId}/publish` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testPublishContent` | Yes (moderator/admin required) | — |
| POST | `/api/publishing/content/{contentId}/appeals` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testRequestAppeal` | Yes (authenticated) | Yes |
| POST | `/api/publishing/appeals/{appealId}/decision` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testDecideAppeal` | Yes (moderator/admin required) | Yes |
| POST | `/api/publishing/content/{contentId}/corrections` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testApplyCorrection` | Yes (owner or admin) | Yes |
| GET | `/api/publishing/content/{contentId}/versions` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testGetVersions` | Yes (owner or admin) | — |
| GET | `/api/publishing/content/{contentId}/diff` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testGetDiff` | Yes (owner or admin) | — |
| POST | `/api/publishing/content/{contentId}/rollback` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testRollback` | Yes (moderator/admin required) | — |
| GET | `/api/publishing/content/{contentId}/audit` | `PublishingApiIntegrationTest` | `PublishingApiFunctionalTests#testGetAudit` | Yes (owner or admin) | — |

---

## Payments (`/api/payments`)

| Method | Path | Integration Test | API Functional Test | Auth Check | Validation |
|--------|------|-----------------|---------------------|-----------|-----------|
| POST | `/api/payments/tenders` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testRecordTender` | Yes (staff/admin required) | Yes |
| POST | `/api/payments/callbacks` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testCallbackProcessing` | Yes (admin required) | Yes |
| POST | `/api/payments/settlements/import` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testSettlementImport` | Yes (admin required) | — |
| POST | `/api/payments/refunds` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testRefund` | Yes (admin required) | Yes |
| GET | `/api/payments/reconciliation/report` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testReconciliationReport` | Yes (admin required) | — |
| GET | `/api/payments/reconciliation/traces` | `PaymentsApiIntegrationTest` | `PaymentsApiFunctionalTests#testTraces` | Yes (admin required) | — |

---

## Auth Coverage Matrix

| Endpoint | Unauthenticated (401) | Wrong Role (403) | Own User (200) | Admin (200) |
|----------|----------------------|-----------------|---------------|------------|
| POST `/api/security/verification` | Yes | — | Yes | Yes |
| GET `/api/security/verification/pending` | Yes | Yes (non-admin) | — | Yes |
| PATCH `/api/security/verification/{id}` | Yes | Yes (non-admin) | — | Yes |
| POST `/api/events` | Yes | Yes (non-admin) | — | Yes |
| POST `/api/events/{id}/ticket-types` | Yes | Yes (non-admin) | — | Yes |
| POST `/api/tickets/reservations` | Yes | — | Yes | Yes |
| POST `/api/seat-orders` | Yes | Yes (unrecognised role) | Yes | Yes |
| POST `/api/seat-orders/{id}/pay` | Yes | Yes (unrecognised role) | Yes | Yes |
| POST `/api/moderation/reports` | Yes | — | Yes | Yes |
| GET `/api/moderation/reports` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/moderation/reports/{id}/decision` | Yes | Yes (SENIOR) | — | Yes |
| GET `/api/moderation/users/{username}/penalties` | Yes | Yes (different user, non-admin) | Yes | Yes |
| GET `/api/moderation/users/{username}/notifications` | Yes | Yes (different user, non-admin) | Yes | Yes |
| PATCH `/api/moderation/notifications/{id}/read` | Yes | — | Yes | Yes |
| POST `/api/files/upload` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/files/{id}/versions` | Yes | Yes (SENIOR) | — | Yes |
| GET `/api/files` | Yes | — | Yes | Yes |
| GET `/api/files/{id}/history` | Yes | — | Yes | Yes |
| POST `/api/files/{id}/download-links` | Yes | — | Yes | Yes |
| GET `/api/files/download/{token}` | Yes | — | Yes | Yes |
| POST `/api/publishing/content` | Yes | — | Yes | Yes |
| GET `/api/publishing/content` | Yes | — | Yes (own) | Yes (all) |
| POST `/api/publishing/content/{id}/update` | Yes | Yes (other user) | Yes (owner) | Yes |
| POST `/api/publishing/content/{id}/submit` | Yes | Yes (other user) | Yes (owner) | Yes |
| POST `/api/publishing/content/{id}/review` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/publishing/content/{id}/publish` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/publishing/content/{id}/appeals` | Yes | — | Yes | Yes |
| POST `/api/publishing/appeals/{id}/decision` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/publishing/content/{id}/corrections` | Yes | Yes (other user) | Yes (owner) | Yes |
| GET `/api/publishing/content/{id}/versions` | Yes | Yes (other user) | Yes (owner) | Yes |
| GET `/api/publishing/content/{id}/diff` | Yes | Yes (other user) | Yes (owner) | Yes |
| POST `/api/publishing/content/{id}/rollback` | Yes | Yes (SENIOR) | — | Yes |
| GET `/api/publishing/content/{id}/audit` | Yes | Yes (other user) | Yes (owner) | Yes |
| POST `/api/payments/tenders` | Yes | Yes (SENIOR) | — | Yes |
| POST `/api/payments/callbacks` | Yes | Yes (non-admin) | — | Yes |
| POST `/api/payments/settlements/import` | Yes | Yes (non-admin) | — | Yes |
| POST `/api/payments/refunds` | Yes | Yes (non-admin) | — | Yes |
| GET `/api/payments/reconciliation/report` | Yes | Yes (non-admin) | — | Yes |
| GET `/api/payments/reconciliation/traces` | Yes | Yes (non-admin) | — | Yes |

---

## Test Count Summary

| Layer | Count |
|-------|-------|
| E2E Browser Tests (Playwright) | 14 |
| Frontend Unit Tests (Jest) | 89 |
| Backend Integration Tests (SpringBootTest RANDOM_PORT) | 147 |
| Backend Controller Tests (MockMvc) | 28 |
| Backend Service Tests | 43 |
| API Functional Test Suites (standalone) | 9 |
| **Total** | **330+** |

## Coverage Notes

- All 8 controllers are covered by domain-specific functional test files in `API_tests/`.
- `AuthorizationHardeningControllerTest` provides cross-cutting 401/403 coverage for the most
  security-sensitive endpoints; see that file for exhaustive role-boundary assertions.
- All shipped frontend modules have dedicated test files: `events.test.js`, `core.test.js`,
  `auth.test.js`, `moderation.test.js`, `orders.test.js`, `publishing.test.js`, `discovery.test.js`.
- Negative paths covered: validation failures, duplicate keys, invalid IDs, cross-user ownership,
  expired tokens, invalid enum values, post-publish update rejection.
- Cross-check read-after-write: publishing versions/audit, file history after version upload,
  verification pending list after approval, moderation penalties/notifications after decision.
- Playwright E2E tests exercise critical portal flows against the live backend: login, discovery,
  hierarchy, draft creation, moderation report+decision, file management, payments, identity
  verification, full publishing lifecycle, and authorization gating.
- Fully containerized test execution via `docker compose -f docker-compose.test.yml up --build`.
- Endpoints marked `--` for Auth Check are public (no token required) by design.
