# API Specification - SilverStage Event Ticketing & Governance

This document outlines the RESTful API endpoints for the SilverStage system.

**Base URL**: `http://localhost:8080/api`

---

## 1. Event Management & Hierarchy
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/events` | List all available events. | Public |
| `POST` | `/events` | Create a new event draft. | Org Admin+ |
| `GET` | `/events/{id}/hierarchy` | Deep-fetch Event -> Season -> Session -> Stand -> Zone -> Seat tree. | Public |

---

## 2. Ticketing & Reservations
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/events/{id}/ticket-types` | Create new pricing tiers for an event. | Org Admin+ |
| `GET` | `/events/{id}/ticket-types` | List pricing tiers (General, Senior, etc.). | Public |
| `POST` | `/tickets/reservations` | Legacy/Internal ticket reservation. | User+ |
| `GET` | `/sessions/{id}/seat-map` | View interactive seat map with inventory status. | Public |
| `POST` | `/seat-orders` | Create a new order for reserved seats (15m hold). | User+ |
| `POST` | `/seat-orders/{id}/pay` | Record payment and finalize seats. | User+ |

---

## 3. Discovery & Search
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/discovery/suggestions` | Type-ahead query suggestions. | Public |
| `GET` | `/discovery/search` | Search events and announcements with filters. | Public |
| `GET` | `/discovery/browse/seasons` | Paginated browse for seasons. | Public |
| `GET` | `/discovery/browse/sessions` | Paginated browse for sessions. | Public |
| `GET` | `/discovery/browse/announcements` | Paginated browse for news/announcements. | Public |

---

## 4. File Management
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/files/upload` | Upload documents with tags and access control. | Staff+ |
| `POST` | `/files/{id}/versions` | Upload a new version of a document. | Staff+ |
| `GET` | `/files` | List documents with metadata filters. | User+ |
| `GET` | `/files/{id}/history` | View document version history. | User+ |
| `POST` | `/files/{id}/download-links` | Generate a 72-hour expiring download link. | User+ |
| `GET` | `/files/download/{token}` | Download file via temporary token. | User+ |

---

## 5. Publishing Workflow
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/publishing/content` | Create a new content draft. | User+ |
| `GET` | `/publishing/content` | List all content items. | Public |
| `POST` | `/publishing/content/{id}/update` | Update an existing draft. | Owner / Mod+ |
| `POST` | `/publishing/content/{id}/submit` | Submit draft for review. | Owner / Mod+ |
| `POST` | `/publishing/content/{id}/review` | Mark content as under review. | Mod+ |
| `POST` | `/publishing/content/{id}/publish` | Final approval and public release. | Mod+ |
| `POST` | `/publishing/content/{id}/appeals` | Request an appeal for rejected content. | Owner |
| `POST` | `/appeals/{id}/decision` | Resolve an appeal (Approve/Reject). | Mod+ |
| `POST` | `/publishing/content/{id}/corrections` | Apply corrections to published content. | Owner / Mod+ |
| `GET` | `/publishing/content/{id}/versions` | List all versions of a content piece. | Public |
| `GET` | `/publishing/content/{id}/diff` | Side-by-side version comparison. | Public |
| `POST` | `/publishing/content/{id}/rollback` | Rollback to a specific version. | Mod+ |
| `GET` | `/publishing/content/{id}/audit` | View full audit trail for content. | Public |

---

## 6. Moderation & Reporting
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/moderation/reports` | Report a user or content item. | User+ |
| `GET` | `/moderation/reports` | List open reports for review. | Mod+ |
| `POST` | `/moderation/reports/{id}/decision` | Resolve report and apply penalties. | Mod+ |
| `GET` | `/moderation/users/{u}/penalties` | View penalties applied to a user. | User / Mod+ |
| `GET` | `/moderation/users/{u}/notifications` | View system notifications. | User / Mod+ |
| `PATCH` | `/moderation/notifications/{id}/read` | Mark a notification as read. | User+ |

---

## 7. Security & Authentication
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/security/accounts` | Register a new account. | Public |
| `POST` | `/security/login` | Authenticate and receive a token. | Public |
| `GET` | `/security/menu` | Get visible navigation menus for current user. | Public |
| `POST` | `/security/verification` | Submit identity verification request. | User+ |
| `GET` | `/security/verification/pending` | List pending identity verifications. | Mod+ |
| `PATCH` | `/security/verification/{id}` | Approve or reject identity verification. | Mod+ |

---

## 8. Payments & Reconciliation
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/payments/tenders` | Record offline tenders (Cash/Check). | Staff+ |
| `POST` | `/payments/callbacks` | Process gateway settlement callbacks. | Admin+ |
| `POST` | `/payments/settlements/import` | Batch import settlement files (CSV). | Admin+ |
| `POST` | `/payments/refunds` | Process a refund request. | Admin+ |
| `GET` | `/payments/reconciliation/report` | Generate reconciliation summary. | Admin+ |
| `GET` | `/payments/reconciliation/traces` | View detailed operation audit traces. | Admin+ |

---

## Headers
- **`X-Auth-Token`**: Required for all protected routes.
