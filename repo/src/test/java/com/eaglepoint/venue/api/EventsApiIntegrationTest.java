package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsApiIntegrationTest extends ApiTestBase {

    private String adminToken;
    private String seniorToken;

    @BeforeAll
    void setUp() {
        adminToken = registerAndLogin("evt_admin", "PLATFORM_ADMIN");
        seniorToken = registerAndLogin("evt_senior", "SENIOR");
    }

    // -----------------------------------------------------------------------
    // List Events
    // -----------------------------------------------------------------------

    @Test
    void listEvents_success() {
        ResponseEntity<String> resp = get("/api/events");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        // seed data has event id=1
        assertTrue(bodyContains(resp.getBody(), "id"), "Body should contain 'id' key");
    }

    // -----------------------------------------------------------------------
    // Create Event
    // -----------------------------------------------------------------------

    @Test
    void createEvent_success() {
        String code = "EVT-TEST-" + System.nanoTime();
        String body = "{\"code\":\"" + code + "\",\"name\":\"Test Event\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> resp = postJson("/api/events", body, adminToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "id"), "Body should contain 'id' key");
        assertTrue(bodyContains(resp.getBody(), "name"), "Body should contain 'name' key");
    }

    @Test
    void createEvent_unauthorized() {
        String code = "EVT-UNAUTH-" + System.nanoTime();
        String body = "{\"code\":\"" + code + "\",\"name\":\"Unauthorized Event\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> resp = postJson("/api/events", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createEvent_forbidden_senior() {
        String code = "EVT-FORBID-" + System.nanoTime();
        String body = "{\"code\":\"" + code + "\",\"name\":\"Forbidden Event\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> resp = postJson("/api/events", body, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void createEvent_validationFailure() {
        String body = "{\"code\":\"\",\"name\":\"\",\"startDate\":null,\"endDate\":null}";

        ResponseEntity<String> resp = postJson("/api/events", body, adminToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // Get Hierarchy
    // -----------------------------------------------------------------------

    @Test
    void getHierarchy_success() {
        // seed data has event id=1 with seasons
        ResponseEntity<String> resp = get("/api/events/1/hierarchy");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "seasons"), "Body should contain 'seasons' key");
    }

    @Test
    void createEvent_duplicateCode() {
        String code = "EVT-DUP-" + System.nanoTime();
        String body = "{\"code\":\"" + code + "\",\"name\":\"Duplicate Code Event\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> firstResp = postJson("/api/events", body, adminToken);
        assertStatus(firstResp, HttpStatus.CREATED);

        ResponseEntity<String> secondResp = postJson("/api/events", body, adminToken);
        int status = secondResp.getStatusCode().value();
        assertTrue(status == 400 || status == 409,
                "Second event with duplicate code should return 400 or 409 but got: " + status);
    }

    // -----------------------------------------------------------------------
    // Get Hierarchy — additional coverage
    // -----------------------------------------------------------------------

    @Test
    void getHierarchy_nonExistentEvent() {
        ResponseEntity<String> resp = get("/api/events/99999/hierarchy");

        int status = resp.getStatusCode().value();
        assertTrue(status >= 400 && status < 600,
                "Non-existent event hierarchy should return 4xx/5xx but got: " + status);
    }

    @Test
    void createEvent_withAdminToken_thenGetHierarchy() {
        String code = "EVT-HIER-" + System.nanoTime();
        String createBody = "{\"code\":\"" + code + "\",\"name\":\"Hierarchy Test Event\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> createResp = postJson("/api/events", createBody, adminToken);
        assertStatus(createResp, HttpStatus.CREATED);
        assertNotNull(createResp.getBody(), "Create response body must not be null");
        long eventId = extractLong(createResp.getBody(), "id");

        ResponseEntity<String> hierResp = get("/api/events/" + eventId + "/hierarchy");

        assertStatus(hierResp, HttpStatus.OK);
        assertNotNull(hierResp.getBody(), "Hierarchy response body must not be null");
        assertTrue(bodyContains(hierResp.getBody(), "seasons"),
                "Hierarchy body should contain 'seasons' key");
    }

    // -----------------------------------------------------------------------
    // Create then List
    // -----------------------------------------------------------------------

    @Test
    void createEvent_thenList() {
        String code = "EVT-LIST-" + System.nanoTime();
        String eventName = "List Verification Event";
        String createBody = "{\"code\":\"" + code + "\",\"name\":\"" + eventName + "\","
                + "\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";

        ResponseEntity<String> createResp = postJson("/api/events", createBody, adminToken);
        assertStatus(createResp, HttpStatus.CREATED);

        ResponseEntity<String> listResp = get("/api/events");
        assertStatus(listResp, HttpStatus.OK);
        assertNotNull(listResp.getBody(), "List response body must not be null");
        assertTrue(listResp.getBody().contains(code),
                "List response should contain the newly created event code: " + code);
    }
}
