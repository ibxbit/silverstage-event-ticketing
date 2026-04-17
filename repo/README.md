# fullstack

# Senior Venue Platform

Offline-first senior community venue ticketing platform built with Spring MVC REST APIs, MyBatis, MySQL, and a jQuery web portal served by the same application.

## Implemented Scope

- Spring MVC REST endpoints for events and full hierarchy retrieval.
- Structured ticket type setup with tiered pricing, visibility scopes, and labeled sale windows.
- Per-channel inventory quotas (`ONLINE_PORTAL`, `BOX_OFFICE`) with quota-safe transactional deductions.
- Unique reservation code enforcement to prevent duplicate bookings under concurrent requests.
- Interactive session/zone/seat selection map with quota feedback for seniors and family booking.
- 15-minute seat hold release and 30-minute unpaid auto-cancel with inventory return via local scheduler.
- Discovery APIs with type-ahead suggestions, result highlighting, deduplication, and multi-filter search.
- Paginated browsing endpoints for seasons, sessions, and community announcements.
- Secure file management with folder organization, tags, and full document version history.
- Expiring download links (default 72 hours) and role-based file access for `SERVICE_STAFF` and `ORG_ADMIN`.
- Content reporting with optional evidence attachments for any user.
- Moderator review flow with penalties: `MUTE_24H`, `POST_RESTRICT_7D`, `PERMANENT_BAN`.
- Publishing workflow states: `DRAFT -> SUBMISSION -> REVIEW -> PUBLISH`.
- Post-publish corrections gated by appeal approval, version diff, rollback within 30 days, and audit trail.
- RBAC-enabled account security with local login, lockout policy, and role-based menu/API authorization.
- Offline real-name verification with AES-encrypted ID storage and masked display.
- Payment tender recording, settlement import/callback idempotency, refunds, reconciliation exceptions, and operation traces.

## Startup Instructions

The application must be started with Docker Compose.

1. Create `.env` from the provided template.
2. Start the full stack:

```bash
docker-compose up --build
```

3. Wait for the app container to expose port `8080` and the MySQL container to become healthy.

## Configuration

All runtime configuration is containerized through Docker Compose and environment variables.

Required values are defined in `repo/.env.example`:

```env
MYSQL_ROOT_PASSWORD=
MYSQL_USER=
MYSQL_PASSWORD=
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/senior_venue_platform
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
APP_SECURITY_AES_KEY=YOUR_32_CHAR_KEY_HERE
```

The application fails fast if required secrets are missing or weak.

## Access Method

- Web portal: `http://localhost:8080/`
- API base URL: `http://localhost:8080`
- MySQL: `localhost:3306`

## Verification Method

Use the running Dockerized stack and verify both API and UI behavior.

1. Confirm the API is reachable:

```bash
curl http://localhost:8080/api/events
```

2. Open the web portal at `http://localhost:8080/`.

3. Use the portal login/registration panel to create an account and confirm:
- the login banner reports success
- the event list loads
- auth-gated sections appear only for allowed roles

4. Verify core API auth behavior with curl:

```bash
curl -X POST http://localhost:8080/api/seat-orders -H "Content-Type: application/json" -d '{"eventId":1,"sessionId":1,"ticketTypeId":1,"orderCode":"SO-DEMO","buyerReference":"forged","channel":"ONLINE_PORTAL","seatIds":[1]}'
curl -X PATCH http://localhost:8080/api/moderation/notifications/1/read
```

Expected result:
- both requests return `401 Unauthorized` without `X-Auth-Token`
- requests with a valid token but insufficient role or ownership return `403 Forbidden`

5. Verify an authenticated flow:

```bash
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"senior_demo","password":"Passw0rd!23","role":"SENIOR"}'
curl -X POST http://localhost:8080/api/security/login -H "Content-Type: application/json" -d '{"username":"senior_demo","password":"Passw0rd!23"}'
```

Use the returned token to call another protected endpoint, for example:

```bash
curl http://localhost:8080/api/security/menu -H "X-Auth-Token: <TOKEN>"
```

## Authentication And Demo Credentials

Authentication is required for protected workflows.

There are no pre-seeded demo credentials documented for this repository.
Use the self-registration endpoint or portal registration UI to create test accounts for each role.

Role creation examples:

```bash
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"senior_demo","password":"Passw0rd!23","role":"SENIOR"}'
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"family_demo","password":"Passw0rd!23","role":"FAMILY_MEMBER"}'
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"staff_demo","password":"Passw0rd!23","role":"SERVICE_STAFF"}'
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"org_admin_demo","password":"Passw0rd!23","role":"ORG_ADMIN"}'
curl -X POST http://localhost:8080/api/security/accounts -H "Content-Type: application/json" -d '{"username":"platform_admin_demo","password":"Passw0rd!23","role":"PLATFORM_ADMIN"}'
```

Credential matrix for verification:

| Role | Username | Password |
|---|---|---|
| `SENIOR` | `senior_demo` | `Passw0rd!23` |
| `FAMILY_MEMBER` | `family_demo` | `Passw0rd!23` |
| `SERVICE_STAFF` | `staff_demo` | `Passw0rd!23` |
| `ORG_ADMIN` | `org_admin_demo` | `Passw0rd!23` |
| `PLATFORM_ADMIN` | `platform_admin_demo` | `Passw0rd!23` |

## Testing Notes

- Backend HTTP coverage is implemented through Spring integration tests and external API functional tests.
- Frontend unit coverage is implemented with Jest against the browser modules in `src/main/resources/static/js/`.
- Browser E2E coverage is implemented with Playwright in `repo/e2e-tests/`.
- This README does not require local package-manager or runtime installation steps to start the application.

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

## API Contract Updates

- `POST /api/security/accounts` now returns a safe registration payload (`id`, `username`, `role`, `active`) and never exposes `passwordHash`, failed attempts, or lockout internals.
- Moderation, publishing, and file-management privileged endpoints now require `X-Auth-Token`.
- `POST /api/moderation/reports` requires `X-Auth-Token`; `reporterUser` may be omitted and defaults to the authenticated username.
- `POST /api/seat-orders` and `POST /api/seat-orders/{orderId}/pay` require `X-Auth-Token`; buyer identity is server-derived from token user.
- `POST /api/tickets/reservations` requires `X-Auth-Token`; `buyerReference` is overwritten server-side from authenticated token identity.
- `PATCH /api/moderation/notifications/{notificationId}/read` now performs object-level ownership checks for non-privileged roles.
- `POST /api/publishing/content/{contentId}/corrections` requires content owner or privileged role.
- `POST /api/publishing/content/{contentId}/rollback` requires privileged role.
- `GET /api/publishing/content`, `GET /api/publishing/content/{contentId}/versions`, `GET /api/publishing/content/{contentId}/diff`, and `GET /api/publishing/content/{contentId}/audit` require `X-Auth-Token` and enforce owner/privileged-role authorization.

## File Upload Guardrails

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
