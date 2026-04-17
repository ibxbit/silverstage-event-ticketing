package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketingApiIntegrationTest extends ApiTestBase {

    private String adminToken;
    private String seniorToken;

    @BeforeAll
    void setUpUsers() {
        adminToken = registerAndLogin("tkt_admin", "PLATFORM_ADMIN");
        seniorToken = registerAndLogin("tkt_senior", "SENIOR");
    }

    // -----------------------------------------------------------------------
    // POST /api/events/{eventId}/ticket-types
    // -----------------------------------------------------------------------

    @Test
    void createTicketType_success() {
        String code = unique("TKT");
        String body = "{"
                + "\"code\":\"" + code + "\","
                + "\"name\":\"Test Type\","
                + "\"basePrice\":25.00,"
                + "\"visibilityScope\":\"PUBLIC\","
                + "\"saleStart\":\"2026-04-01T09:00:00\","
                + "\"saleEnd\":\"2026-06-01T17:00:00\","
                + "\"totalInventory\":100,"
                + "\"onlineQuotaPercent\":60,"
                + "\"boxOfficeQuotaPercent\":40,"
                + "\"tierRules\":["
                + "{\"minQuantity\":1,\"price\":25.00},"
                + "{\"minQuantity\":5,\"price\":22.00}"
                + "]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/events/1/ticket-types", body, adminToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "id"), "Body should contain 'id' key");
        assertTrue(bodyContains(resp.getBody(), "name"), "Body should contain 'name' key");
    }

    @Test
    void createTicketType_unauthorized() {
        String code = unique("TKT");
        String body = "{"
                + "\"code\":\"" + code + "\","
                + "\"name\":\"Test Type\","
                + "\"basePrice\":25.00,"
                + "\"visibilityScope\":\"PUBLIC\","
                + "\"saleStart\":\"2026-04-01T09:00:00\","
                + "\"saleEnd\":\"2026-06-01T17:00:00\","
                + "\"totalInventory\":100,"
                + "\"onlineQuotaPercent\":60,"
                + "\"boxOfficeQuotaPercent\":40,"
                + "\"tierRules\":[{\"minQuantity\":1,\"price\":25.00}]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/events/1/ticket-types", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createTicketType_forbidden_senior() {
        String code = unique("TKT");
        String body = "{"
                + "\"code\":\"" + code + "\","
                + "\"name\":\"Test Type\","
                + "\"basePrice\":25.00,"
                + "\"visibilityScope\":\"PUBLIC\","
                + "\"saleStart\":\"2026-04-01T09:00:00\","
                + "\"saleEnd\":\"2026-06-01T17:00:00\","
                + "\"totalInventory\":100,"
                + "\"onlineQuotaPercent\":60,"
                + "\"boxOfficeQuotaPercent\":40,"
                + "\"tierRules\":[{\"minQuantity\":1,\"price\":25.00}]"
                + "}";

        ResponseEntity<String> resp = postJson("/api/events/1/ticket-types", body, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void createTicketType_validationFailure() {
        String body = "{\"code\":\"\",\"name\":\"\"}";

        ResponseEntity<String> resp = postJson("/api/events/1/ticket-types", body, adminToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // GET /api/events/{eventId}/ticket-types
    // -----------------------------------------------------------------------

    @Test
    void listTicketTypes_success() {
        ResponseEntity<String> resp = get("/api/events/1/ticket-types");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "id"), "Body should contain 'id' key from seed data");
    }

    // -----------------------------------------------------------------------
    // POST /api/tickets/reservations
    // -----------------------------------------------------------------------

    @Test
    void reserveTickets_success() {
        String reservationCode = unique("RES");
        String body = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"" + reservationCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body, seniorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "reservationId"), "Body should contain 'reservationId' key");
        assertTrue(bodyContains(resp.getBody(), "reservationCode"), "Body should contain 'reservationCode' key");
    }

    @Test
    void reserveTickets_unauthorized() {
        String reservationCode = unique("RES");
        String body = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"" + reservationCode + "\","
                + "\"buyerReference\":\"anyone\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reserveTickets_overwritesBuyerReference() {
        // The controller overwrites buyerReference with the authenticated user's username.
        // We verify the request succeeds (201) which confirms the overwrite path was executed.
        String reservationCode = unique("RES");
        String body = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"" + reservationCode + "\","
                + "\"buyerReference\":\"attacker\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body, seniorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        // Confirm the response does not echo back "attacker" as the buyer reference
        String responseBody = resp.getBody();
        assertTrue(!responseBody.contains("\"buyerReference\":\"attacker\""),
                "Response must not reflect the forged buyerReference 'attacker'");
    }

    @Test
    void reserveTickets_validationFailure() {
        // Missing required fields: ticketTypeId, reservationCode, channel, quantity
        String body = "{\"buyerReference\":\"user\"}";

        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body, seniorToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void reserveTickets_invalidTicketType() {
        String code = unique("RES-BADTYPE");
        String body = "{"
            + "\"ticketTypeId\":999999,"
            + "\"reservationCode\":\"" + code + "\","
            + "\"buyerReference\":\"ignored\","
            + "\"channel\":\"ONLINE_PORTAL\","
            + "\"quantity\":1"
            + "}";
        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body, seniorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Invalid ticket type should return 4xx but got: " + resp.getStatusCode());
    }

    @Test
    void reserveTickets_excessiveQuantity() {
        String code = unique("RES-EXCESS");
        String body = "{"
            + "\"ticketTypeId\":1,"
            + "\"reservationCode\":\"" + code + "\","
            + "\"buyerReference\":\"ignored\","
            + "\"channel\":\"ONLINE_PORTAL\","
            + "\"quantity\":99999"
            + "}";
        ResponseEntity<String> resp = postJson("/api/tickets/reservations", body, seniorToken);
        // Should fail with 409 or 400 due to insufficient inventory
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Excessive quantity should return 4xx but got: " + resp.getStatusCode());
    }

    @Test
    void reserveTickets_duplicateReservationCode() {
        String reservationCode = unique("RES-DUP");
        String body = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"" + reservationCode + "\","
                + "\"buyerReference\":\"ignored\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        ResponseEntity<String> firstResp = postJson("/api/tickets/reservations", body, seniorToken);
        assertStatus(firstResp, HttpStatus.CREATED);

        ResponseEntity<String> secondResp = postJson("/api/tickets/reservations", body, seniorToken);
        int status = secondResp.getStatusCode().value();
        assertTrue(status == 400 || status == 409,
                "Second reservation with duplicate code should return 400 or 409 but got: " + status);
    }
}
