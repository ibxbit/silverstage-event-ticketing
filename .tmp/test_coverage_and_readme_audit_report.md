# Test Coverage Audit

## Project Type Detection

- Declared project type at top of README: `fullstack`. `repo/README.md:1`
- Confirmed by structure:
  - backend controllers under `repo/src/main/java/com/eaglepoint/venue/api/*.java`
  - browser frontend assets under `repo/src/main/resources/static/js/*.js`
  - frontend unit tests under `repo/frontend-tests/*.test.js`
  - browser E2E tests under `repo/e2e-tests/portal-flows.spec.js`

## Backend Endpoint Inventory

| Endpoint | Source |
|---|---|
| `GET /api/events` | `repo/src/main/java/com/eaglepoint/venue/api/VenueController.java` |
| `POST /api/events` | `repo/src/main/java/com/eaglepoint/venue/api/VenueController.java` |
| `GET /api/events/{eventId}/hierarchy` | `repo/src/main/java/com/eaglepoint/venue/api/VenueController.java` |
| `POST /api/events/{eventId}/ticket-types` | `repo/src/main/java/com/eaglepoint/venue/api/TicketingController.java` |
| `GET /api/events/{eventId}/ticket-types` | `repo/src/main/java/com/eaglepoint/venue/api/TicketingController.java` |
| `POST /api/tickets/reservations` | `repo/src/main/java/com/eaglepoint/venue/api/TicketingController.java` |
| `GET /api/sessions/{sessionId}/seat-map` | `repo/src/main/java/com/eaglepoint/venue/api/SeatReservationController.java` |
| `POST /api/seat-orders` | `repo/src/main/java/com/eaglepoint/venue/api/SeatReservationController.java` |
| `POST /api/seat-orders/{orderId}/pay` | `repo/src/main/java/com/eaglepoint/venue/api/SeatReservationController.java` |
| `GET /api/discovery/suggestions` | `repo/src/main/java/com/eaglepoint/venue/api/DiscoveryController.java` |
| `GET /api/discovery/search` | `repo/src/main/java/com/eaglepoint/venue/api/DiscoveryController.java` |
| `GET /api/discovery/browse/seasons` | `repo/src/main/java/com/eaglepoint/venue/api/DiscoveryController.java` |
| `GET /api/discovery/browse/sessions` | `repo/src/main/java/com/eaglepoint/venue/api/DiscoveryController.java` |
| `GET /api/discovery/browse/announcements` | `repo/src/main/java/com/eaglepoint/venue/api/DiscoveryController.java` |
| `POST /api/files/upload` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `POST /api/files/{documentId}/versions` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `GET /api/files` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `GET /api/files/{documentId}/history` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `POST /api/files/{documentId}/download-links` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `GET /api/files/download/{token}` | `repo/src/main/java/com/eaglepoint/venue/api/FileManagementController.java` |
| `POST /api/moderation/reports` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `GET /api/moderation/reports` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `POST /api/moderation/reports/{reportId}/decision` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `GET /api/moderation/users/{username}/penalties` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `GET /api/moderation/users/{username}/notifications` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `PATCH /api/moderation/notifications/{notificationId}/read` | `repo/src/main/java/com/eaglepoint/venue/api/ModerationController.java` |
| `POST /api/publishing/content` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `GET /api/publishing/content` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/update` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/submit` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/review` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/publish` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/appeals` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/appeals/{appealId}/decision` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/corrections` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `GET /api/publishing/content/{contentId}/versions` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `GET /api/publishing/content/{contentId}/diff` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/publishing/content/{contentId}/rollback` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `GET /api/publishing/content/{contentId}/audit` | `repo/src/main/java/com/eaglepoint/venue/api/PublishingWorkflowController.java` |
| `POST /api/security/accounts` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `POST /api/security/login` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `GET /api/security/menu` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `POST /api/security/verification` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `GET /api/security/verification/pending` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `PATCH /api/security/verification/{verificationId}` | `repo/src/main/java/com/eaglepoint/venue/api/SecurityController.java` |
| `POST /api/payments/tenders` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |
| `POST /api/payments/callbacks` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |
| `POST /api/payments/settlements/import` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |
| `POST /api/payments/refunds` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |
| `GET /api/payments/reconciliation/report` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |
| `GET /api/payments/reconciliation/traces` | `repo/src/main/java/com/eaglepoint/venue/api/PaymentController.java` |

- Total endpoints: `51`

## API Test Mapping Table

All 51 endpoints have direct HTTP coverage by static evidence.

Primary evidence sources:
- external HTTP functional tests: `repo/API_tests/*.java`
- Spring boot HTTP integration tests: `repo/src/test/java/com/eaglepoint/venue/api/*ApiIntegrationTest.java`

Representative coverage mapping:
- events endpoints: `repo/API_tests/EventsApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/EventsApiIntegrationTest.java`
- ticketing endpoints: `repo/API_tests/TicketingApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/TicketingApiIntegrationTest.java`
- discovery endpoints: `repo/API_tests/DiscoveryApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/DiscoveryApiIntegrationTest.java`
- file endpoints: `repo/API_tests/FilesApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/FileManagementApiIntegrationTest.java`
- moderation endpoints: `repo/API_tests/ModerationApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/ModerationApiIntegrationTest.java`
- publishing endpoints: `repo/API_tests/PublishingApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/PublishingApiIntegrationTest.java`
- security endpoints: `repo/API_tests/SecurityApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/SecurityApiIntegrationTest.java`
- payments endpoints: `repo/API_tests/PaymentsApiFunctionalTests.java`, `repo/src/test/java/com/eaglepoint/venue/api/PaymentsApiIntegrationTest.java`
- seat reservation endpoints: `repo/src/test/java/com/eaglepoint/venue/api/SeatReservationApiIntegrationTest.java`

## API Test Classification

1. True No-Mock HTTP
- `repo/API_tests/ApiFunctionalTestHelper.java` uses Java `HttpClient` against the running app.
- `repo/src/test/java/com/eaglepoint/venue/api/ApiTestBase.java` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate`.

2. HTTP with Mocking
- `repo/src/test/java/com/eaglepoint/venue/api/SecurityRegistrationResponseTest.java`
- `repo/src/test/java/com/eaglepoint/venue/api/SecurityControllerTest.java`
- `repo/src/test/java/com/eaglepoint/venue/api/AuthorizationHardeningControllerTest.java`

3. Non-HTTP (unit/integration without HTTP)
- service tests under `repo/src/test/java/com/eaglepoint/venue/service/*.java`
- frontend Jest tests under `repo/frontend-tests/*.test.js`

## Mock Detection Rules

| What is mocked | Where |
|---|---|
| `AccountSecurityService` in standalone `MockMvc` test | `repo/src/test/java/com/eaglepoint/venue/api/SecurityRegistrationResponseTest.java` |
| controller/service dependencies in standalone `MockMvc` tests | `repo/src/test/java/com/eaglepoint/venue/api/SecurityControllerTest.java` |
| multiple controller dependencies in standalone `MockMvc` tests | `repo/src/test/java/com/eaglepoint/venue/api/AuthorizationHardeningControllerTest.java` |
| frontend transport stubs via `$.ajax = jest.fn(...)` and `$.getJSON = jest.fn(...)` | `repo/frontend-tests/*.test.js` |

## Coverage Summary

- Total endpoints: `51`
- Endpoints with HTTP tests: `51`
- Endpoints with TRUE no-mock tests: `51`
- HTTP coverage: `100%`
- True API coverage: `100%`

## Unit Test Summary

### Backend Unit Tests

- Present.
- Covered modules include controllers, services, and authorization helpers.
- Important backend modules not directly tested:
  - repository/mapper layer remains lightly represented relative to service/API coverage

### Frontend Unit Tests

- Frontend unit tests: PRESENT
- Frontend test files:
  - `repo/frontend-tests/app.test.js`
  - `repo/frontend-tests/app-happy-paths.test.js`
  - `repo/frontend-tests/core.test.js`
  - `repo/frontend-tests/auth.test.js`
  - `repo/frontend-tests/events.test.js`
  - `repo/frontend-tests/orders.test.js`
  - `repo/frontend-tests/discovery.test.js`
  - `repo/frontend-tests/moderation.test.js`
  - `repo/frontend-tests/publishing.test.js`
- Frameworks/tools detected: Jest, jsdom, jQuery-based browser harness
- Components/modules covered:
  - `src/main/resources/static/app.js`
  - `src/main/resources/static/js/core.js`
  - `src/main/resources/static/js/auth.js`
  - `src/main/resources/static/js/events.js`
  - `src/main/resources/static/js/orders.js`
  - `src/main/resources/static/js/discovery.js`
  - `src/main/resources/static/js/moderation.js`
  - `src/main/resources/static/js/publishing.js`
- Important frontend components/modules not tested:
  - none found among the current browser JS modules

### Cross-Layer Observation

- Backend and frontend testing are balanced enough for a fullstack submission.
- Remaining weakness is quality depth and E2E realism, not missing frontend coverage.

## API Observability Check

- Strong on endpoint visibility and request construction.
- Moderate weakness remains in response assertion depth, especially in the external `API_tests` suite where some checks are limited to status and one or two keys.

## Test Quality & Sufficiency

- Success paths: strong
- Failure paths: strong for auth and permissions, moderate elsewhere
- Edge cases: moderate
- Validation: moderate to strong
- Auth/permissions: strong
- Integration boundaries: strong for backend HTTP
- Fullstack browser realism: partial

### `run_tests.sh` Check

- FLAG: local dependency workflow remains present in scripts.
- Evidence:
  - local Maven dependency in `repo/run_tests.sh:15-19`
  - local `npm ci` in `repo/run_tests.sh:34-38`
  - local `mvn spring-boot:run` in `repo/run_tests.sh:44-47`
  - local Maven dependency in `repo/unit_tests/run_unit_tests.sh:10-23`
  - local `javac` fast path in `repo/API_tests/run_api_tests.sh:11-16`

## End-to-End Expectations

- Fullstack expectation: real FE ↔ BE tests should exist.
- Evidence present:
  - Playwright suite exists: `repo/e2e-tests/portal-flows.spec.js`
  - UI-only auth helper exists: `repo/e2e-tests/portal-flows.spec.js:50-85`
- Remaining gap:
  - visible suites still largely rely on API seeding and auth token injection. `repo/e2e-tests/portal-flows.spec.js:18-48,135-161,215-249`

## Tests Check

- Endpoint inventory: complete
- API mapping: complete
- Frontend unit requirement: satisfied
- Over-mocking risk: limited to controller/unit layer, not the main HTTP coverage

## Test Coverage Score (0–100)

`90/100`

## Score Rationale

- Complete endpoint coverage with true no-mock HTTP tests.
- Frontend unit coverage is present and comprehensive across current browser modules.
- Playwright coverage exists and includes some browser-first authentication work.
- Score remains below top tier because script execution is still local-toolchain dependent and browser E2E still relies heavily on setup shortcuts.

## Key Gaps

- `run_tests.sh` and subordinate scripts still rely on local Maven/npm/JDK paths.
- Browser E2E still mostly uses API registration/login helpers and localStorage token injection.
- Some API response assertions remain shallow.

## Confidence & Assumptions

- Confidence: high
- Assumptions:
  - endpoint inventory is limited to Spring controllers under `repo/src/main/java/com/eaglepoint/venue/api`
  - method+path pairs are counted once regardless of query-string permutations

# README Audit

## High Priority Issues

- None.

## Medium Priority Issues

- README documents a compliant Docker startup flow, but the actual `run_tests.sh` script still uses local-toolchain paths; this is a repo execution mismatch, not a README hard-gate failure.
- Demo credentials are defined as role-specific self-registration examples rather than guaranteed pre-seeded accounts. This is acceptable because the README explicitly states how to create them before verification.

## Low Priority Issues

- The README remains fairly long and API-heavy.

## Hard Gate Failures

- None.

## README Verdict

`PASS`

## Final Verdicts

- Test Coverage Audit: `PASS WITH RESERVATIONS`
- README Audit: `PASS`
