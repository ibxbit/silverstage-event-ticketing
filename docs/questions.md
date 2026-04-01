# questions.md — Business Logic Questions Log

## Project: SilverStage Event Ticketing & Governance System (TASK-47)

> This document records all questions raised during the process of understanding the Prompt, covering unclear business processes, business rules, data relationships, and boundary conditions.

---

## 1. Event Hierarchy & Data Model

### 1.1 Relationship Cardinality Between Event → Season → Session

**Question:** The prompt defines a hierarchy of `event → season → session → stand/zone → seat`, but does not specify the cardinality constraints. Can an event have multiple seasons? Can a session belong to multiple seasons? Are stand/zone/seat entities shared across sessions or unique per session?

**My Understanding:** An event can have multiple seasons (e.g., "Spring Concert Series" with 2025 and 2026 seasons). Each season contains multiple sessions (individual performances). Stand/zone definitions belong to the venue and are shared, but individual seat *availability* is tracked per session.

**Solution:** Modeled with a `venue` table owning `stand → zone → seat` structure (shared across events). `event` has many `seasons`, each season has many `sessions`. A `session_seat` join table tracks per-session seat availability and status (available, held, sold). This decouples venue layout from per-event inventory.

---

### 1.2 Seat vs. General Admission — Seat Map Applicability

**Question:** The prompt mentions both "interactive seat maps by stand/zone/seat" and "General Admission" ticket types. General Admission typically implies no assigned seat. How does the seat map interact with GA tickets? Do GA tickets consume zone-level capacity instead of specific seats?

**My Understanding:** General Admission tickets do not assign a specific seat but are scoped to a zone/stand with a maximum capacity. The seat map would show zone-level fill status for GA areas, whereas reserved-seating zones show individual seat availability.

**Solution:** Ticket types include a `seating_mode` enum (`ASSIGNED_SEAT`, `GENERAL_ADMISSION`). GA ticket purchases decrement a `zone_capacity_remaining` counter rather than locking a specific seat row. The interactive seat map renders GA zones with a capacity bar/indicator instead of individual seat icons.

---

### 1.3 Is the Venue Fixed or Multi-Venue?

**Question:** The prompt refers to "senior-focused community venues" (plural), but does not clarify whether the system manages a single venue or multiple venues. Can events occur at different venues?

**My Understanding:** The system supports multiple venues, since community organizations may host events across different locations (halls, theaters, outdoor stages).

**Solution:** Added a `venue` entity at the top of the hierarchy: `venue → stand/zone → seat`. Each `session` is linked to a specific `venue`, allowing events/seasons to span multiple locations.

---

## 2. Seat Reservation & Inventory Management

### 2.1 Seat Hold (15 min) vs. Unpaid Order Auto-Cancel (30 min) — Lifecycle Overlap

**Question:** The prompt specifies a **15-minute seat hold** with automatic release AND **30-minute auto-cancel for unpaid orders**. What happens between minutes 15 and 30? Does the seat release at 15 minutes while the order remains open for another 15 minutes? Can another user purchase the same seat in that window, creating a conflict?

**My Understanding:** The 15-minute hold applies to the *reservation/selection* phase before the user initiates payment. Once the user submits payment (or proceeds to checkout), the order is created and the seat hold converts to a firm reservation. The 30-minute window applies to created orders awaiting payment confirmation. If the hold expires before checkout, both the hold and any pending order are canceled.

**Solution:** Implemented a two-phase state machine: `HELD (15 min TTL) → ORDER_CREATED (30 min TTL) → PAID`. A scheduled job runs every minute to release expired holds and cancel expired unpaid orders, returning seats to the available pool. The transition from HELD to ORDER_CREATED resets the timer to 30 minutes.

---

### 2.2 Concurrent Oversell Prevention — Locking Strategy

**Question:** The prompt mandates "transactional inventory deductions and unique reservation constraints" to prevent overselling under concurrency, but does not specify the locking strategy. Should we use pessimistic locking (SELECT FOR UPDATE), optimistic locking (version columns), or database-level unique constraints?

**My Understanding:** A combination approach: use a `UNIQUE` constraint on `(session_id, seat_id)` in the `reservation` table to make double-booking impossible at the DB level, plus `SELECT FOR UPDATE` on the seat row during the reservation transaction to serialize concurrent access to the same seat.

**Solution:** The `reservation` table has a unique index on `(session_id, seat_id, status)` where status is `ACTIVE`. The reservation service wraps seat selection in a transaction with `SELECT ... FOR UPDATE` on the target seat row, then inserts into `reservation`. If concurrency leads to a duplicate, the unique constraint throws an exception caught by the service, returning a "seat no longer available" response.

---

### 2.3 Multi-Seat Reservation Atomicity

**Question:** The prompt does not clarify whether users can reserve multiple seats in a single transaction (e.g., a family booking 3 seats together). If so, should the reservation succeed only if *all* seats are available, or allow partial fulfillment?

**My Understanding:** Users should be able to select multiple seats in one transaction (common for family bookings). The reservation must be atomic — all-or-nothing — to avoid splitting a family across distant seats.

**Solution:** The reservation endpoint accepts a list of `seat_ids`. The service acquires locks on all requested seats (ordered by seat ID to prevent deadlocks), validates all are available, then inserts reservations for all seats in a single transaction. If any seat is unavailable, the entire transaction rolls back and returns the specific seats that failed.

---

### 2.4 Channel Quota Enforcement and Cross-Channel Interplay

**Question:** The prompt specifies per-channel quotas (e.g., 60% online, 40% box office) but does not describe what happens when one channel's quota is exhausted while inventory remains in the other. Can unused box office quota overflow to the online channel?

**My Understanding:** Quotas are soft caps with optional overflow. Once the primary channel's quota is reached, the system should either block sales on that channel or allow an admin to release remaining inventory from the slower channel. Automatic overflow could undermine the purpose of quotas.

**Solution:** Each `ticket_type` per `session` has channel-level quota records (`channel_quota` table with `channel`, `total_qty`, `sold_qty`). When a channel's quota is full, sales on that channel are blocked. Organization admins can manually "rebalance" quota between channels via an admin API, transferring unsold inventory from one channel to another. An optional system setting allows automatic overflow N hours before the event.

---

## 3. Ticket Types & Pricing

### 3.1 "Tiered Pricing" Definition

**Question:** The prompt mentions "tiered pricing rules" but does not detail what the tiers are. Are tiers based on time (early-bird vs. regular vs. last-minute), quantity (group discounts), zone/section, or user role (senior discount)?

**My Understanding:** Tiers are primarily time-based and role-based. "Senior Discount" is role-based. Sale windows define time-based pricing tiers (e.g., early-bird pricing in the first week of sales, regular pricing after). Zone-based pricing is handled by different ticket types per zone.

**Solution:** The `pricing_rule` table links to `ticket_type` with fields: `tier_name`, `price`, `effective_start`, `effective_end`, `applicable_role` (nullable). The active price is resolved by matching the current timestamp and user role against applicable rules, with the most specific match taking priority.

---

### 3.2 Companion Pass — Eligibility and Linking

**Question:** The prompt lists "Companion Pass" as a ticket type but does not explain the eligibility rules. Must a Companion Pass be purchased alongside a Senior ticket? Can a companion attend independently? Is there a limit to companions per senior?

**My Understanding:** A Companion Pass requires a linked Senior ticket — it cannot be purchased independently. Each senior ticket allows at most one companion. The companion is seated adjacent to the senior if possible.

**Solution:** The `companion_pass` ticket type has a DB constraint via `linked_ticket_id` (FK to a senior ticket). The reservation flow validates that (a) the linked senior ticket exists and is active, (b) no other companion pass is already linked to it. Seat selection for companions prompts adjacent seat suggestions.

---

### 3.3 Sale Window Timezone Handling

**Question:** Sale windows are specified with specific timestamps (e.g., "03/25/2026 9:00 AM–04/10/2026 5:00 PM"), but the prompt does not state which timezone applies. Is it the venue's local timezone, the user's timezone, or a system-wide timezone like UTC?

**My Understanding:** Sale windows should be defined and displayed in the **venue's local timezone**, since these are on-prem, community venues where all users are local. Internally, timestamps are stored in UTC and converted for display.

**Solution:** The `venue` table includes a `timezone` column (e.g., `America/New_York`). Sale window start/end are stored as UTC in the database. The API returns timezone-aware ISO-8601 timestamps, and the jQuery frontend converts them to the venue's local timezone for display.

---

## 4. Publishing Workflow & Version Control

### 4.1 What Content Follows the Publishing Workflow?

**Question:** The prompt describes a publishing workflow (draft → submission → review → publish) but does not specify *which entities* go through this workflow. Is it only events? Or do announcements, file attachments, and user-generated content also follow this workflow?

**My Understanding:** The publishing workflow applies to **events** and **announcements/community posts**. File uploads (waivers, flyers) are direct uploads by authorized staff and do not require editorial review. Moderation reports follow a separate workflow.

**Solution:** Added a polymorphic `publishable_content` table with `content_type` (EVENT, ANNOUNCEMENT) and `content_id` linking to the specific entity. The publishing state machine (DRAFT → SUBMITTED → IN_REVIEW → PUBLISHED) is managed on this table, allowing unified workflow logic.

---

### 4.2 Appeal Process for Post-Publish Corrections

**Question:** The prompt says "post-publish corrections require an appeal record" but does not define who initiates the appeal, who approves it, or what the appeal lifecycle looks like. Can the original author submit an appeal, or must it be an admin?

**My Understanding:** The original author (or service staff) submits an appeal with a justification/reason. Organization admins or platform admins review and approve/reject the appeal. If approved, the content reverts to a draft state where the correction is made, then goes through the publish workflow again.

**Solution:** Created an `appeal` table with fields: `content_id`, `requester_id`, `reason`, `status` (PENDING, APPROVED, REJECTED), `reviewer_id`, `reviewed_at`. On approval, the system creates a new version of the content in DRAFT status linked to the appeal. The correction goes through the standard draft → publish pipeline.

---

### 4.3 Rollback Scope and Side Effects

**Question:** The prompt allows "rollback to any prior version within 30 days." If an event is rolled back to a prior version, what happens to tickets already sold under the current version? For example, if pricing, session times, or seat maps changed, does a rollback affect existing reservations?

**My Understanding:** Rollback applies to *content/metadata* (descriptions, flyers, announcement text) and NOT to transactional data (sold tickets, reservations, pricing already applied). Rolling back an event's description does not undo ticket sales. If critical details like session times change, the system should flag affected reservations for manual review.

**Solution:** The version-control system stores snapshots of editable content fields only. Rollback replaces current content fields with the historical snapshot. A `rollback_impact_check` service scans for downstream impacts (e.g., changed session times vs. existing reservations) and generates a warning report for the admin to review before confirming the rollback.

---

### 4.4 Side-by-Side Diff — Granularity

**Question:** The prompt requires "side-by-side diff comparison" but does not specify the granularity. Is it field-level diffs (e.g., showing that `title` changed from A to B), full-text diffs (like a code diff), or structured object diffs?

**My Understanding:** For structured entities (events, sessions), field-level diffs are most useful (table showing field name, old value, new value). For free-text content (announcements, descriptions), a text-level diff with inline highlighting (similar to GitHub diffs) is appropriate.

**Solution:** Version snapshots store a JSON representation of the entity at that point. The diff engine compares JSON keys for structured fields (producing field-level change sets) and applies a text-diff algorithm (e.g., Myers diff) for long-text fields, rendering both in a side-by-side HTML view.

---

## 5. Accounts, RBAC & Security

### 5.1 Role Hierarchy and Permission Inheritance

**Question:** The prompt lists five roles — seniors, family members, service staff, organization admins, platform admins — but does not define whether roles are hierarchical (i.e., does a platform admin inherit all permissions of an organization admin?), or if each role has an independent permission set.

**My Understanding:** Roles form a loose hierarchy: `Platform Admin > Organization Admin > Service Staff > Family Member / Senior`. Higher roles inherit the permissions of lower roles. Seniors and Family Members are at the same level but may have slightly different permissions (e.g., Family Members can purchase Companion Passes).

**Solution:** Implemented RBAC with a `role` table, a `permission` table, and a `role_permission` join table. Role hierarchy is implemented by assigning cumulative permissions to each role (platform admin has all permissions from all lower roles). A `role_hierarchy` mapping defines inheritance for maintainability.

---

### 5.2 Multi-Role Assignment

**Question:** Can a single user have multiple roles? For example, can a senior also be service staff at the venue?

**My Understanding:** Yes, a single user can be assigned multiple roles. The effective permission set is the union of all assigned role permissions.

**Solution:** The `user_role` table supports many-to-many relationships between users and roles. The authorization middleware aggregates permissions across all roles assigned to the authenticated user.

---

### 5.3 Password Complexity Rules — Specifics

**Question:** The prompt requires "at least 10 characters, complexity rules" but does not define the specific complexity rules. Must passwords include uppercase, lowercase, digits, special characters? Are there rules against common passwords or sequential characters?

**My Understanding:** Standard complexity rules: at least one uppercase letter, one lowercase letter, one digit, and one special character (e.g., `!@#$%^&*`). No more than 3 consecutive identical characters. Password must not contain the username.

**Solution:** A `PasswordValidator` service enforces: min 10 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char, no 3+ consecutive identical chars, and no username substring. Validation messages are returned per-rule so the frontend can display specific guidance.

---

### 5.4 Account Lockout — Unlock Mechanism

**Question:** The prompt specifies "15-minute lockout after 5 failed attempts" but does not describe how accounts are unlocked. Is it automatic after 15 minutes? Can an admin manually unlock? Does the failed-attempt counter reset after a successful login?

**My Understanding:** Lockout is automatic and time-based: after 15 minutes, the account is automatically unlocked. An admin can also manually unlock an account before the timer expires. The failed-attempt counter resets to zero after a successful login.

**Solution:** The `user` table includes `failed_login_attempts` (int), `locked_until` (datetime). On each failed attempt, the counter increments. At 5 failures, `locked_until` is set to `now + 15 min`. Login checks `locked_until` before authenticating. On successful login, both fields reset. Admin API provides `POST /api/admin/users/{id}/unlock`.

---

### 5.5 Real-Name Verification — Status Transitions and Impact

**Question:** The prompt mentions "offline and manual" real-name verification where "staff review ID information and mark status." What are the possible verification statuses? What can unverified users do vs. verified users? Is verification required for ticket purchases?

**My Understanding:** Verification statuses: `UNVERIFIED`, `PENDING_REVIEW`, `VERIFIED`, `REJECTED`. Unverified users can browse events but cannot purchase tickets. Verification is required for the first ticket purchase. Rejected users can resubmit with corrected information.

**Solution:** The `user_verification` table stores: `user_id`, `id_type`, `id_number_encrypted`, `id_name`, `status`, `reviewer_id`, `reviewed_at`, `rejection_reason`. The purchase flow checks verification status and redirects unverified users to the verification submission page. The display API masks `id_number` to show only the last 4 characters.

---

## 6. Payment & Reconciliation

### 6.1 "On-Prem Terminal Batches" — Format and Integration

**Question:** The prompt mentions "on-prem terminal batches" as a tender type and "importing daily settlement files from major gateways/acquirers," but does not specify the file format, import mechanism, or how terminal-based payments are linked to orders in the system.

**My Understanding:** On-prem terminals (card readers) process payments offline and produce batch files (likely CSV or fixed-width format). These are imported daily into the system via a file upload or scheduled directory scan. Each record in the batch file contains a transaction reference that maps to an order in the system.

**Solution:** Created a `settlement_import` module that accepts CSV/Excel file uploads. Each row must contain a `transaction_reference` matching the `order.external_ref` field. An `ImportService` parses the file, matches records to orders, and updates payment status. Unmatched records are flagged in the exception report. The import is idempotent — re-importing the same file (identified by hash) is a no-op.

---

### 6.2 Idempotent Callback Processing — Deduplication Key

**Question:** The prompt requires imported callbacks to be "processed idempotently by transaction reference." What constitutes the transaction reference? Is it a single field or a composite key? How should the system handle callbacks with the same reference but different amounts?

**My Understanding:** The transaction reference is a unique identifier provided by the payment terminal/gateway (e.g., a batch reference number + sequence number). If a callback with the same reference arrives with a different amount, it should be flagged as an anomaly in the exception report rather than silently ignored or overwritten.

**Solution:** The `payment_callback` table has a unique index on `transaction_reference`. Processing first checks for existence: if found with matching amount, it's a duplicate (skip with 200 OK). If found with a *different* amount, it's logged as an `AMOUNT_MISMATCH` exception. If not found, it's processed normally. All outcomes are logged in the operation audit trail.

---

### 6.3 Revenue Sharing — Calculation Model

**Question:** The prompt states "revenue sharing between platform and merchants" but does not define the sharing model. Is it a fixed percentage split, tiered by volume, or configurable per event/organization?

**My Understanding:** Revenue sharing is configurable per organization/merchant, with a default platform commission rate (e.g., 10%). Individual events may override the default rate if negotiated. The split is calculated during reconciliation, not at payment time.

**Solution:** A `revenue_sharing_config` table stores `organization_id`, `default_rate`, and optional `event_id` override rate. During reconciliation, the settled amount is split: `platform_amount = settled * rate`, `merchant_amount = settled * (1 - rate)`. The reconciliation report itemizes the split per event and per organization.

---

### 6.4 Full vs. Partial Refund Rules

**Question:** The prompt supports "full/partial refunds" but does not define the business rules: Is there a refund deadline? Can attendees get refunds after the event occurs? Who can initiate refunds (user self-service, staff only, admin only)? Are there refund fees?

**My Understanding:** Refunds before the event are allowed (full refund up to 48 hours before, partial after that). Post-event refunds are exceptional and require admin approval. Service staff can initiate refunds; seniors cannot self-serve refunds (since payments are offline). No refund processing fee is deducted.

**Solution:** A `refund_policy` table defines per-event rules: `full_refund_deadline` (hours before event), `partial_refund_deadline`, `partial_refund_percentage`, `post_event_allowed` (boolean). The refund endpoint validates against these rules. Post-event refunds require `platform_admin` or `organization_admin` role. All refunds are logged with reason, initiator, and approval chain.

---

### 6.5 Cash and Check Payment Tracking

**Question:** The prompt lists "cash" and "check" as tender types. How are cash payments verified and reconciled if there is no digital trail from a gateway? Who records cash payments, and how are discrepancies handled?

**My Understanding:** Cash and check payments are recorded manually by service staff / box-office operators at the point of sale. The system records the tender type and amount; reconciliation for cash is based on the daily cash register report submitted by staff.

**Solution:** The POS (box office) interface includes a "Record Payment" form with tender type (cash/check/terminal), amount, and operator ID. For cash, the system generates a daily `cash_reconciliation_report` comparing recorded cash sales to the physical count submitted by the operator. Discrepancies are flagged as exceptions requiring manager review.

---

## 7. File Management & Secure Attachments

### 7.1 File Size and Type Restrictions

**Question:** The prompt allows uploads of "waivers, accessibility notes, event flyers" but does not specify file size limits or allowed file types. Are there restrictions to prevent abuse (e.g., uploading very large videos)?

**My Understanding:** A reasonable default: max file size of 20 MB per upload, allowed types include PDF, DOCX, PNG, JPG, GIF, and TXT. Video files are not supported in this context (community venue documents only).

**Solution:** The upload endpoint validates `Content-Type` against an allowlist and rejects files exceeding the configurable `max_file_size` (default 20 MB). The configuration is stored in `system_settings` and can be adjusted by platform admins.

---

### 7.2 Expiring Download Links — Scope and Revocation

**Question:** The prompt states download links expire after 72 hours by default. Can the link creator customize the expiry duration? Can links be revoked before expiry? Who can generate these links — only the file owner, or any role with file access?

**My Understanding:** The 72-hour default can be customized (e.g., 24 hours for sensitive waivers). Links can be revoked by the creator or an admin. Any user with read access to the file can generate a download link.

**Solution:** The `download_link` table stores `file_id`, `created_by`, `expires_at`, `revoked` (boolean), `token` (UUID). The link generation API accepts an optional `expires_in_hours` parameter (default 72, max 168). A revocation endpoint sets `revoked = true`. The download controller checks both expiry and revocation status before serving the file.

---

### 7.3 Version History — Storage Strategy

**Question:** The prompt requires "version history" for uploaded files. Does this mean every upload overwrites the previous version (storing the old version), or are versions explicitly created? Is there a limit on the number of versions retained?

**My Understanding:** Each re-upload of the same logical file creates a new version, preserving the old file. Versions are retained indefinitely (or until the file is deleted), consistent with the 30-day rollback policy for other content.

**Solution:** The `file` table stores the current version metadata. A `file_version` table stores all historical versions with `version_number`, `file_path`, `uploaded_by`, `uploaded_at`, `size_bytes`, `checksum`. Downloading without specifying a version returns the latest. A `GET /api/files/{id}/versions` endpoint lists all versions.

---

## 8. Content Moderation

### 8.1 Moderation Penalty Escalation

**Question:** The prompt lists penalties: "24-hour mutes, 7-day posting restrictions, or account bans," but does not specify whether penalties escalate for repeat offenders. Is the penalty at the moderator's discretion, or is there an automatic escalation policy?

**My Understanding:** Penalties are at the moderator's discretion for the first occurrence. An automatic escalation policy tracks offense history: 1st offense → warning or 24-hour mute, 2nd → 7-day restriction, 3rd → permanent ban. Moderators can override the suggestion.

**Solution:** A `moderation_history` table tracks all penalties per user. The moderation console shows offense count and suggests the next escalation level. The moderator can accept the suggestion or choose a different penalty with a justification note. The system enforces the applied penalty automatically.

---

### 8.2 Mute vs. Posting Restriction — Scope

**Question:** The prompt distinguishes "24-hour mutes" from "7-day posting restrictions." What is the functional difference? Does a mute prevent *all* activity, or only posting? Can a muted user still browse and purchase tickets?

**My Understanding:** A **mute** prevents the user from posting content (comments, reviews, reports) but allows browsing and ticket purchases. A **posting restriction** has the same effect but for a longer duration. An **account ban** prevents all activity including login.

**Solution:** Penalties are stored with a `penalty_type` enum: `MUTE` (no posting, can browse/buy), `POSTING_RESTRICTION` (same as mute, longer duration), `BAN` (no login). Middleware checks the user's active penalties: `MUTE`/`POSTING_RESTRICTION` blocks write endpoints for content; `BAN` blocks authentication entirely.

---

### 8.3 Evidence Attachments for Reports — Privacy

**Question:** Users can submit "evidence attachments" when reporting content. Who can view these attachments? Are they visible to the reported user? What happens to evidence after the case is resolved?

**My Understanding:** Evidence attachments are visible only to moderators and admins during the review process. The reported user does NOT see the evidence (to protect the reporter's identity). After case closure, evidence is retained for 90 days for audit purposes, then automatically purged.

**Solution:** Evidence files are stored in a restricted `moderation_evidence` folder with access limited to `MODERATOR` and `PLATFORM_ADMIN` roles. The moderation case detail API includes evidence only for these roles. A cleanup job deletes evidence files 90 days after case closure.

---

## 9. Search & Discovery

### 9.1 "Word Count" Filter — Purpose and Scope

**Question:** The prompt lists "word count" as a search filter, which is unusual for event listings. What is the intended use case? Does it apply to announcement/post body length, event descriptions, or something else?

**My Understanding:** The "word count" filter applies to **announcements and community posts**, allowing users to filter for short updates vs. long articles. It does not apply to events themselves.

**Solution:** Announcements/posts store a pre-computed `word_count` field (calculated on insert/update). The search API accepts `min_word_count` and `max_word_count` parameters that filter on this field. These filter parameters are hidden/disabled when searching for events (only shown for announcements).

---

### 9.2 Search Relevance Scoring

**Question:** The prompt mentions sorting by "relevance" but does not define how relevance is calculated. Is it based on text match quality (TF-IDF), recency, popularity, or a weighted combination?

**My Understanding:** Relevance scoring is a weighted combination of text match quality (primary factor, via MySQL full-text search scoring), recency (recent content gets a boost), and popularity (view count or ticket sales).

**Solution:** MySQL `MATCH ... AGAINST ... IN BOOLEAN MODE` provides the base relevance score. A weighted formula calculates the final score: `final_score = (text_score * 0.6) + (recency_score * 0.25) + (popularity_score * 0.15)`. The API sorts by this computed score when `sort=relevance` is specified.

---

### 9.3 Deduplication Logic

**Question:** The prompt requires "deduped results" in search. What constitutes a duplicate? Is it the same entity appearing in multiple categories, or duplicated content across different entities?

**My Understanding:** Deduplication refers to the same entity appearing multiple times in results when it matches across different searchable fields (e.g., title AND description). The result set should contain each entity at most once, with the highest-scoring match.

**Solution:** The search query returns distinct entity IDs by grouping results on `(entity_type, entity_id)` and taking the maximum relevance score. This is implemented via `GROUP BY` in the SQL query with `MAX(score)` as the sort value.

---

## 10. Audit Logging & Traceability

### 10.1 Operation Log Granularity

**Question:** The prompt requires "end-to-end operation logs" and "every change must be logged with who/when/what." What is the granularity of logging? Should every API call be logged, or only business-critical state changes? How long are logs retained?

**My Understanding:** Business-critical operations (CRUD on entities, status transitions, payments, refunds, login events) are logged as structured audit entries. API-level access logs (all requests) are recorded separately at the infrastructure level. Audit logs are retained indefinitely; access logs are retained for 1 year.

**Solution:** An `audit_log` table records: `action` (CREATE, UPDATE, DELETE, STATUS_CHANGE, LOGIN, etc.), `entity_type`, `entity_id`, `user_id`, `timestamp`, `old_value` (JSON), `new_value` (JSON), `ip_address`. A Spring AOP aspect intercepts annotated service methods and auto-populates log entries. Retention is configurable in system settings, defaulting to indefinite for audit logs.

---

### 10.2 Sensitive Data in Logs

**Question:** Audit logs capture "who/when/what" for every change including sensitive fields (encrypted ID numbers, payment details). Should sensitive field values be logged in plaintext, masked, or excluded from the audit trail?

**My Understanding:** Sensitive fields should be **masked** in audit logs — the log records that the field changed but shows the value as `****1234` (last 4 chars). Full values are never stored in plaintext in the audit log.

**Solution:** The audit logger applies a `@SensitiveField` annotation to entity fields containing PII or payment data. When serializing `old_value`/`new_value` for the audit log, annotated fields are automatically masked using a `SensitiveDataMasker` utility that preserves only the last 4 characters.

---
