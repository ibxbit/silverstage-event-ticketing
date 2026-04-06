# Senior Venue Platform (Parts 1-8)

Core architecture for an offline-first senior community venue ticketing platform built with Spring MVC REST APIs, MyBatis, MySQL, and a local jQuery portal.

## Implemented scope

- Spring MVC REST endpoints for events and full hierarchy retrieval.
- Structured ticket type setup with tiered pricing, visibility scopes, and labeled sale windows.
- Per-channel inventory quotas (`ONLINE_PORTAL`, `BOX_OFFICE`) with quota-safe transactional deductions.
- Unique reservation code enforcement to prevent duplicate bookings under concurrent requests.
- Interactive session/zone/seat selection map with quota feedback for seniors and family booking.
- 15-minute seat hold release and 30-minute unpaid auto-cancel with inventory return via local scheduler.
- Discovery APIs with type-ahead suggestions, result highlighting, deduplication, and multi-filter search.
- Paginated browsing endpoints for seasons, sessions, and community announcements.
- Secure local file management with folder organization, tags, and full document version history.
- Expiring download links (default 72 hours) and role-based file access for `SERVICE_STAFF`/`ORG_ADMIN`.
- Content reporting with optional evidence attachments for any user.
- Moderator review flow with penalties: `MUTE_24H`, `POST_RESTRICT_7D`, `PERMANENT_BAN`, plus clear user notifications.
- Publishing workflow states: `DRAFT -> SUBMISSION -> REVIEW -> PUBLISH`.
- Post-publish corrections gated by appeal approval, side-by-side version diff, rollback within 30 days, and full audit trail.
- RBAC-enabled account security with local login, lockout policy, and role-based menu/API authorization.
- Offline real-name verification with AES-encrypted ID storage and masked display.
- Payment tender recording, settlement import/callback idempotency, refunds, revenue sharing, reconciliation exceptions, and operation traces.
- MyBatis mapper + XML data access layer.
- Local MySQL schema for the hierarchy model: `event -> season -> session -> stand -> zone -> seat`.
- Local SQL bootstrap scripts (`schema.sql`, `data.sql`) for on-prem deployment.
- Local file-based application logging under `logs/`.
- jQuery-powered web portal served from the same application (`/`).

## Start Command (How to Run)

Run the service directly with Maven:

```bash
mvn spring-boot:run
```

## Configuration

The application fails fast at startup if required secrets are missing or weak.

Required environment variables:

- `SPRING_DATASOURCE_URL`: JDBC URL for the MySQL database (e.g., `jdbc:mysql://localhost:3306/senior_venue_platform`)
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `APP_SECURITY_AES_KEY`: AES key with at least 32 characters (default/placeholder values are rejected)

Docker profile variables:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

A sample `.env` template is provided in the repository root as `.env.example`:

```
MYSQL_ROOT_PASSWORD=
MYSQL_USER=
MYSQL_PASSWORD=
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/senior_venue_platform
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
APP_SECURITY_AES_KEY=YOUR_32_CHAR_KEY_HERE
```

Copy `.env.example` to `.env` and fill in the appropriate values before running the application.

## Service Address (Services List)

- Application API + Web Portal: `http://localhost:8080`
- MySQL (for local/offline persistence): `localhost:3306`

## Verification method

Primary verification is the unified one-click runner:

```bash
bash ./run_tests.sh
```

Prerequisites for the one-click runner:

- Maven available in `PATH`
- Node.js/npm (for frontend Jest tests)
- JDK 17+ (`javac`) for local API functional test execution
- Docker is NOT required for local testing if JDK 17+ is present. It is only used for containerized deployment or as a fallback for API tests if a local Java toolchain is unavailable.

The runner executes in order:

- backend unit tests from `unit_tests/` (service-layer suite via Maven)
- frontend Jest tests (`npm test`)
- API functional tests from `API_tests/` against live HTTP endpoints (`javac` local path first, Docker fallback)

It prints a single summary with total/passed/failed counts across all suites.

Optional direct entry points:

```bash
bash ./unit_tests/run_unit_tests.sh
bash ./API_tests/run_api_tests.sh
npm test
```

`API_tests/run_api_tests.sh` behavior:

- uses local `javac` + `java` when available
- falls back to Docker (`maven:3.9.9-eclipse-temurin-17`) when local Java toolchain is unavailable

1. Confirm app is reachable:

```bash
curl http://localhost:8080/api/events
```

2. Register and login (RBAC + local auth):

```bash
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d "{\"username\":\"senior_demo\",\"password\":\"Passw0rd!23\",\"role\":\"SENIOR\"}"
curl -X POST http://localhost:8080/api/security/login -H "Content-Type: application/json" -d "{\"username\":\"senior_demo\",\"password\":\"Passw0rd!23\"}"
```

3. Verify payment reconciliation endpoint (with an admin token from login response):

```bash
curl http://localhost:8080/api/payments/reconciliation/report -H "X-Auth-Token: <TOKEN>"
```

4. Run unit tests:

```bash
mvn test
```

5. Run frontend happy-path tests (login + seat reservation UI):

```bash
npm install
npm run test:frontend
```

6. Verify seat-order auth and moderation ownership controls:

```bash
curl -X POST http://localhost:8080/api/seat-orders -H "Content-Type: application/json" -d '{"eventId":1,"sessionId":1,"ticketTypeId":1,"orderCode":"SO-DEMO","buyerReference":"forged","channel":"ONLINE_PORTAL","seatIds":[1]}'
curl -X PATCH http://localhost:8080/api/moderation/notifications/1/read
```

Both requests should return `401` without `X-Auth-Token`.

Requests with a valid token but insufficient role/ownership return `403`.

## Authorization model (security hardening)

- Privileged APIs do not trust client-supplied role headers.
- Controllers derive identity and role from `X-Auth-Token` via server-side session lookup.
- Role checks are centralized through `RequestAuthorizationService` + `AccountSecurityService.requireAnyRole(...)`.
- User-specific moderation reads enforce object-level authorization:
  - self access, or
  - privileged moderator/admin roles.
- Seat-order write endpoints require token-derived role checks (`SENIOR`, `FAMILY_MEMBER`, `SERVICE_STAFF`, `ORG_ADMIN`, `PLATFORM_ADMIN`).
- Protected endpoints use consistent auth semantics:
  - missing/blank `X-Auth-Token` -> `401 Unauthorized`
  - authenticated but role/ownership mismatch -> `403 Forbidden`
- `PATCH /api/moderation/notifications/{notificationId}/read` enforces notification ownership for non-privileged users.
- Publishing correction and rollback governance:
  - corrections require content owner or privileged role (`MODERATOR`, `ORG_ADMIN`, `PLATFORM_ADMIN`),
  - rollback requires privileged role.
- Publishing read access governance:
  - `GET /api/publishing/content` requires `X-Auth-Token`
  - privileged roles (`MODERATOR`, `ORG_ADMIN`, `PLATFORM_ADMIN`) can read broader publishing views
  - non-privileged users can read only their own publishing items/details (versions/diff/audit)
- Payment callback idempotency records reconciliation anomalies when duplicate callbacks conflict on amount/status.

## API contract updates

- `POST /api/security/accounts` now returns a safe registration payload (`id`, `username`, `role`, `active`) and never exposes `passwordHash`, failed attempts, or lockout internals.
- Moderation, publishing, and file-management privileged endpoints now require `X-Auth-Token`.
- `POST /api/moderation/reports` requires `X-Auth-Token`; `reporterUser` may be omitted and defaults to the authenticated username.
- `POST /api/seat-orders` and `POST /api/seat-orders/{orderId}/pay` require `X-Auth-Token`; buyer identity is server-derived from token user.
- `POST /api/tickets/reservations` requires `X-Auth-Token`; `buyerReference` is overwritten server-side from authenticated token identity.
- `PATCH /api/moderation/notifications/{notificationId}/read` now performs object-level ownership checks for non-privileged roles.
- `POST /api/publishing/content/{contentId}/corrections` requires content owner or privileged role.
- `POST /api/publishing/content/{contentId}/rollback` requires privileged role.
- `GET /api/publishing/content`, `GET /api/publishing/content/{contentId}/versions`, `GET /api/publishing/content/{contentId}/diff`, and `GET /api/publishing/content/{contentId}/audit` require `X-Auth-Token` and enforce owner/privileged-role authorization.

## File upload guardrails

- File management uploads enforce content-type allowlist and max size (`app.files.allowed-content-types`, `app.files.max-upload-bytes`).
- Moderation evidence uploads enforce content-type allowlist and max size (`app.moderation.allowed-evidence-content-types`, `app.moderation.max-evidence-bytes`).
- Invalid type/size requests return `400 Bad Request` with clear error messages.

## Core APIs

- `GET /api/events`
- `POST /api/events`
- `GET /api/events/{eventId}/hierarchy`
- `POST /api/events/{eventId}/ticket-types`
- `GET /api/events/{eventId}/ticket-types`
- `POST /api/tickets/reservations`
- `GET /api/sessions/{sessionId}/seat-map`
- `POST /api/seat-orders`
- `POST /api/seat-orders/{orderId}/pay`
- `GET /api/discovery/suggestions?q=...`
- `GET /api/discovery/search`
- `GET /api/discovery/browse/seasons`
- `GET /api/discovery/browse/sessions`
- `GET /api/discovery/browse/announcements`
- `POST /api/files/upload`
- `POST /api/files/{documentId}/versions`
- `GET /api/files`
- `GET /api/files/{documentId}/history`
- `POST /api/files/{documentId}/download-links`
- `GET /api/files/download/{token}`
- `POST /api/moderation/reports`
- `GET /api/moderation/reports`
- `POST /api/moderation/reports/{reportId}/decision`
- `GET /api/moderation/users/{username}/penalties`
- `GET /api/moderation/users/{username}/notifications`
- `PATCH /api/moderation/notifications/{notificationId}/read`
- `POST /api/publishing/content`
- `GET /api/publishing/content`
- `POST /api/publishing/content/{contentId}/update`
- `POST /api/publishing/content/{contentId}/submit`
- `POST /api/publishing/content/{contentId}/review`
- `POST /api/publishing/content/{contentId}/publish`
- `POST /api/publishing/content/{contentId}/appeals`
- `POST /api/publishing/appeals/{appealId}/decision`
- `POST /api/publishing/content/{contentId}/corrections?appealId=...`
- `GET /api/publishing/content/{contentId}/versions`
- `GET /api/publishing/content/{contentId}/diff?leftVersion=...&rightVersion=...`
- `POST /api/publishing/content/{contentId}/rollback?targetVersion=...`
- `GET /api/publishing/content/{contentId}/audit`
- `POST /api/security/accounts`
- `POST /api/security/login`
- `GET /api/security/menu`
- `POST /api/security/verification`
- `GET /api/security/verification/pending`
- `PATCH /api/security/verification/{verificationId}`
- `POST /api/payments/tenders`
- `POST /api/payments/callbacks`
- `POST /api/payments/settlements/import`
- `POST /api/payments/refunds`
- `GET /api/payments/reconciliation/report`
- `GET /api/payments/reconciliation/traces`
