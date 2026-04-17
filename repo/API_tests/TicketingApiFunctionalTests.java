import java.net.http.HttpResponse;
import java.util.Map;

public final class TicketingApiFunctionalTests {

    private TicketingApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        long eventId = resolveOrCreateEvent();
        testCreateTicketTypeRequiresAdmin(eventId);
        long ticketTypeId = testCreateTicketTypeWithAdmin(eventId);
        testListTicketTypes(eventId);
        testReserveTicketsRequiresAuth(eventId, ticketTypeId);
        testReserveTicketsWithAuth(token, username, eventId, ticketTypeId);
    }

    private static long resolveOrCreateEvent() throws Exception {
        HttpResponse<String> events = ApiFunctionalTestHelper.request(
            "GET", "/api/events", null, null
        );
        ApiFunctionalTestHelper.requireStatus(events, 200);
        String body = events.body().trim();
        if (!body.equals("[]")) {
            return ApiFunctionalTestHelper.extractFirstLong(body, "id");
        }
        // Create one
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        String createBody = "{\"name\":\"Ticketing Test Event " + System.currentTimeMillis()
            + "\",\"description\":\"Ticketing functional test\"}";
        HttpResponse<String> created = ApiFunctionalTestHelper.request(
            "POST", "/api/events", createBody,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(created, 200, 201);
        return ApiFunctionalTestHelper.extractFirstLong(created.body(), "id");
    }

    private static void testCreateTicketTypeRequiresAdmin(long eventId) throws Exception {
        String body = ticketTypePayload("GEN-NOAUTH");
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events/" + eventId + "/ticket-types", body,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 401);
    }

    private static long testCreateTicketTypeWithAdmin(long eventId) throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        String body = ticketTypePayload("GEN-" + System.currentTimeMillis());
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events/" + eventId + "/ticket-types", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"id\"") && !resp.body().contains("\"ticketTypeId\"")) {
            throw new IllegalStateException("create ticket type response missing id: " + resp.body());
        }
        String idKey = resp.body().contains("\"ticketTypeId\"") ? "ticketTypeId" : "id";
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), idKey);
    }

    private static String ticketTypePayload(String code) {
        return "{\"code\":\"" + code + "\","
            + "\"name\":\"General Admission\","
            + "\"basePrice\":25.00,"
            + "\"visibilityScope\":\"PUBLIC\","
            + "\"saleStart\":\"2026-01-01T00:00:00\","
            + "\"saleEnd\":\"2026-12-31T23:59:59\","
            + "\"totalInventory\":100,"
            + "\"onlineQuotaPercent\":60,"
            + "\"boxOfficeQuotaPercent\":40,"
            + "\"tierRules\":[{\"minQuantity\":1,\"price\":25.00}]"
            + "}";
    }

    private static void testListTicketTypes(long eventId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/events/" + eventId + "/ticket-types", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("ticket types list should be an array: " + body);
        }
    }

    private static void testReserveTicketsRequiresAuth(long eventId, long ticketTypeId) throws Exception {
        String body = "{\"ticketTypeId\":" + ticketTypeId
            + ",\"reservationCode\":\"RES-NOAUTH-" + System.currentTimeMillis() + "\""
            + ",\"quantity\":1,\"channel\":\"ONLINE_PORTAL\",\"buyerReference\":\"anon\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/tickets/reservations", body,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 401);
    }

    private static void testReserveTicketsWithAuth(
        String token, String username, long eventId, long ticketTypeId
    ) throws Exception {
        String resCode = "RES-" + System.currentTimeMillis();
        String body = "{\"ticketTypeId\":" + ticketTypeId
            + ",\"reservationCode\":\"" + resCode + "\""
            + ",\"quantity\":1,\"channel\":\"ONLINE_PORTAL\",\"buyerReference\":\"" + username + "\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/tickets/reservations", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 201);
        if (!resp.body().contains("\"reservationId\"")) {
            throw new IllegalStateException(
                "reservation response missing reservationId: " + resp.body()
            );
        }
    }
}
