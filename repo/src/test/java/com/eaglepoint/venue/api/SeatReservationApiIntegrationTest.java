package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatReservationApiIntegrationTest extends ApiTestBase {

    private String adminToken;
    private String seniorToken;
    private String moderatorToken;

    @BeforeAll
    void setUpUsers() {
        adminToken = registerAndLogin("seat_admin", "PLATFORM_ADMIN");
        seniorToken = registerAndLogin("seat_senior", "SENIOR");
        moderatorToken = registerAndLogin("seat_mod", "MODERATOR");
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{sessionId}/seat-map
    // -----------------------------------------------------------------------

    @Test
    void getSeatMap_success() {
        ResponseEntity<String> resp = get("/api/sessions/1/seat-map?ticketTypeId=1&channel=ONLINE_PORTAL");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        String body = resp.getBody();
        assertTrue(
                body.contains("\"zones\"") || body.contains("\"seats\"") || body.contains("\"remainingQuota\""),
                "Body should contain 'zones', 'seats', or 'remainingQuota' but was: " + body
        );
    }

    // -----------------------------------------------------------------------
    // POST /api/seat-orders
    // -----------------------------------------------------------------------

    @Test
    void createSeatOrder_success() {
        String orderCode = unique("SO");
        String body = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[5]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/seat-orders", body, seniorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "orderId"), "Body should contain 'orderId' key");
    }

    @Test
    void createSeatOrder_unauthorized() {
        String orderCode = unique("SO");
        String body = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"anyone\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[4]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/seat-orders", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createSeatOrder_forbidden_moderator() {
        // MODERATOR is not in the allowed roles for createSeatOrder
        String orderCode = unique("SO");
        String body = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"mod\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[4]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/seat-orders", body, moderatorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void createSeatOrder_overwritesBuyerReference() {
        // The controller overwrites buyerReference with the authenticated user's username.
        // A 201 response confirms the overwrite path ran successfully.
        String orderCode = unique("SO");
        String body = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[6]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/seat-orders", body, seniorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        // The response must not echo back the forged buyer reference
        assertTrue(!resp.getBody().contains("\"buyerReference\":\"forged\""),
                "Response must not reflect the forged buyerReference 'forged'");
    }

    // -----------------------------------------------------------------------
    // POST /api/seat-orders/{orderId}/pay
    // -----------------------------------------------------------------------

    @Test
    void markOrderPaid_success() {
        // Seat 15 (C02) belongs to zone 3 which is in session 2 — use correct sessionId
        String orderCode = unique("SO");
        String createBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":2,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[15]"
                + "}";

        ResponseEntity<String> createResp = postJson("/api/seat-orders", createBody, seniorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        assertNotNull(createResp.getBody(), "Create response body must not be null");
        long orderId = extractLong(createResp.getBody(), "orderId");

        // Now mark the order as paid
        ResponseEntity<String> payResp = postNoBody("/api/seat-orders/" + orderId + "/pay", seniorToken);

        assertStatus(payResp, HttpStatus.OK);
        assertNotNull(payResp.getBody(), "Pay response body must not be null");
        assertTrue(payResp.getBody().contains("\"status\":\"PAID\""),
                "Pay response should contain status PAID but was: " + payResp.getBody());
        assertTrue(bodyContains(payResp.getBody(), "orderId"),
                "Pay response should contain 'orderId' key");
        assertTrue(payResp.getBody().contains("\"orderId\":" + orderId),
                "Pay response orderId should match the created order id=" + orderId);
        assertTrue(bodyContains(payResp.getBody(), "orderCode"),
                "Pay response should contain 'orderCode' key");
        assertTrue(bodyContains(payResp.getBody(), "quantity"),
                "Pay response should contain 'quantity' key");
    }

    @Test
    void markOrderPaid_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/seat-orders/99999/pay");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void markOrderPaid_forbidden_moderator() {
        ResponseEntity<String> resp = postNoBody("/api/seat-orders/99999/pay", moderatorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void markOrderPaid_nonexistentOrder() {
        // Authenticated user tries to pay an order that does not exist in the DB
        ResponseEntity<String> resp = postNoBody("/api/seat-orders/999999/pay", seniorToken);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for nonexistent order but got: " + resp.getStatusCode());
    }

    @Test
    void markOrderPaid_nonOwnerNonAdmin_forbidden() {
        // Register a second senior who does NOT own the order
        String secondSeniorToken = registerAndLogin("seat_senior2", "SENIOR");

        // First senior creates the order (session 1 seat)
        String orderCode = unique("SO");
        String createBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[7]"
                + "}";
        ResponseEntity<String> createResp = postJson("/api/seat-orders", createBody, seniorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long orderId = extractLong(createResp.getBody(), "orderId");

        // Second senior (different user) tries to pay — service checks buyer.equalsIgnoreCase(actor)
        ResponseEntity<String> payResp = postNoBody("/api/seat-orders/" + orderId + "/pay", secondSeniorToken);
        assertStatus(payResp, HttpStatus.FORBIDDEN);
    }

    @Test
    void markOrderPaid_alreadyPaid_conflict() {
        // Create a seat order and pay it once successfully, then attempt a second payment
        String orderCode = unique("SO");
        String createBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[8]"
                + "}";
        ResponseEntity<String> createResp = postJson("/api/seat-orders", createBody, seniorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long orderId = extractLong(createResp.getBody(), "orderId");

        // First payment must succeed
        ResponseEntity<String> firstPay = postNoBody("/api/seat-orders/" + orderId + "/pay", seniorToken);
        assertStatus(firstPay, HttpStatus.OK);

        // Second payment attempt on the same already-PAID order must return 409 CONFLICT
        ResponseEntity<String> secondPay = postNoBody("/api/seat-orders/" + orderId + "/pay", seniorToken);
        assertStatus(secondPay, HttpStatus.CONFLICT);
    }

    @Test
    void markOrderPaid_adminCanPayOthersOrder() {
        // Senior creates an order; admin (PLATFORM_ADMIN) pays it — admin override path
        String orderCode = unique("SO");
        String createBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":2,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[16]"
                + "}";
        ResponseEntity<String> createResp = postJson("/api/seat-orders", createBody, seniorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long orderId = extractLong(createResp.getBody(), "orderId");

        // Admin pays the senior's order — admin roles bypass ownership check
        ResponseEntity<String> payResp = postNoBody("/api/seat-orders/" + orderId + "/pay", adminToken);
        assertStatus(payResp, HttpStatus.OK);
        assertTrue(payResp.getBody().contains("\"status\":\"PAID\""),
                "Admin-paid order should have PAID status but was: " + payResp.getBody());
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{sessionId}/seat-map — additional coverage
    // -----------------------------------------------------------------------

    @Test
    void getSeatMap_withoutParams() {
        // Optional ticketTypeId and channel params are omitted; service returns map without quota data
        ResponseEntity<String> resp = get("/api/sessions/1/seat-map");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        String body = resp.getBody();
        assertTrue(
                body.contains("\"zones\"") || body.contains("\"seats\"") || body.contains("\"sessionId\""),
                "Body should contain zone or session data but was: " + body
        );
    }

    // -----------------------------------------------------------------------
    // POST /api/seat-orders — duplicate orderCode
    // -----------------------------------------------------------------------

    @Test
    void getSeatMap_nonexistentSession() {
        ResponseEntity<String> resp = get("/api/sessions/999999/seat-map?ticketTypeId=1&channel=ONLINE_PORTAL");
        assertTrue(resp.getStatusCode().is4xxClientError() || resp.getStatusCode().is5xxServerError(),
            "Nonexistent session should return error but got: " + resp.getStatusCode());
    }

    @Test
    void getSeatMap_invalidTicketTypeId() {
        ResponseEntity<String> resp = get("/api/sessions/1/seat-map?ticketTypeId=999999&channel=ONLINE_PORTAL");
        // May return 200 with empty zones or 4xx — both acceptable
        assertNotNull(resp.getBody(), "Response should have a body");
    }

    @Test
    void getSeatMap_quotaFieldsPresent() {
        ResponseEntity<String> resp = get("/api/sessions/1/seat-map?ticketTypeId=1&channel=ONLINE_PORTAL");
        assertStatus(resp, HttpStatus.OK);
        String body = resp.getBody();
        assertNotNull(body);
        assertTrue(bodyContains(body, "remainingQuota") || bodyContains(body, "quotaReached"),
            "Seat map should contain quota fields");
        assertTrue(bodyContains(body, "zones"), "Seat map should contain zones");
    }

    @Test
    void createSeatOrder_emptySeatIds_rejected() {
        String orderCode = unique("SO-EMPTY");
        String body = "{"
            + "\"eventId\":1,"
            + "\"sessionId\":1,"
            + "\"ticketTypeId\":1,"
            + "\"orderCode\":\"" + orderCode + "\","
            + "\"buyerReference\":\"ignored\","
            + "\"channel\":\"ONLINE_PORTAL\","
            + "\"seatIds\":[]"
            + "}";
        ResponseEntity<String> resp = postJson("/api/seat-orders", body, seniorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Empty seatIds should be rejected but got: " + resp.getStatusCode());
    }

    @Test
    void createSeatOrder_seatAlreadyReserved_conflict() {
        // Seat 2 is RESERVED in seed data (status='RESERVED')
        String orderCode = unique("SO-RESV");
        String body = "{"
            + "\"eventId\":1,"
            + "\"sessionId\":1,"
            + "\"ticketTypeId\":1,"
            + "\"orderCode\":\"" + orderCode + "\","
            + "\"buyerReference\":\"ignored\","
            + "\"channel\":\"ONLINE_PORTAL\","
            + "\"seatIds\":[2]"
            + "}";
        ResponseEntity<String> resp = postJson("/api/seat-orders", body, seniorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Already-reserved seat should return 4xx but got: " + resp.getStatusCode());
    }

    @Test
    void createSeatOrder_duplicateOrderCode() {
        // Use a fixed orderCode shared by both requests; use different seats so the
        // second request reaches the duplicate-code guard in the service layer.
        String orderCode = unique("SO-DUP");

        String firstBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[9]"
                + "}";

        ResponseEntity<String> firstResp = postJson("/api/seat-orders", firstBody, seniorToken);
        assertStatus(firstResp, HttpStatus.CREATED);

        // Seat 10 is AVAILABLE; same orderCode triggers DataIntegrityViolationException -> 409
        String secondBody = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":1,"
                + "\"ticketTypeId\":1,"
                + "\"orderCode\":\"" + orderCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[10]"
                + "}";

        ResponseEntity<String> secondResp = postJson("/api/seat-orders", secondBody, seniorToken);
        assertStatus(secondResp, HttpStatus.CONFLICT);
    }
}
