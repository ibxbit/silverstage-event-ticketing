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
        String body = "{\"name\":\"General\",\"price\":25.00,\"quota\":100,\"channel\":\"ONLINE_PORTAL\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events/" + eventId + "/ticket-types", body,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 401);
    }

    private static long testCreateTicketTypeWithAdmin(long eventId) throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        String body = "{\"name\":\"General " + System.currentTimeMillis()
            + "\",\"price\":20.00,\"quota\":50,\"channel\":\"ONLINE_PORTAL\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events/" + eventId + "/ticket-types", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"id\"")) {
            throw new IllegalStateException("create ticket type response missing id: " + resp.body());
        }
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), "id");
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
