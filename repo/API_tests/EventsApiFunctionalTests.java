import java.net.http.HttpResponse;
import java.util.Map;

public final class EventsApiFunctionalTests {

    private EventsApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        testListEvents();
        testCreateEventRequiresAdmin();
        testCreateEventWithAdminToken();
        testGetEventHierarchy();
    }

    private static void testListEvents() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/events", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        // Must be an array
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("events list response should be an array: " + body);
        }
    }

    private static void testCreateEventRequiresAdmin() throws Exception {
        long ts = System.currentTimeMillis();
        String body = "{\"code\":\"EVT-" + ts + "\",\"name\":\"Test Event\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events", body,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 401);
    }

    private static void testCreateEventWithAdminToken() throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        long ts = System.currentTimeMillis();
        String body = "{\"code\":\"EVT-" + ts + "\",\"name\":\"FuncTest Event " + ts + "\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-08-31\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/events", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"id\"")) {
            throw new IllegalStateException("create event response missing id: " + resp.body());
        }
    }

    private static void testGetEventHierarchy() throws Exception {
        HttpResponse<String> events = ApiFunctionalTestHelper.request(
            "GET", "/api/events", null, null
        );
        ApiFunctionalTestHelper.requireStatus(events, 200);

        // Only test hierarchy if at least one event exists
        String eventsBody = events.body().trim();
        if (eventsBody.equals("[]")) {
            return;
        }

        long eventId = ApiFunctionalTestHelper.extractFirstLong(eventsBody, "id");
        HttpResponse<String> hierarchy = ApiFunctionalTestHelper.request(
            "GET", "/api/events/" + eventId + "/hierarchy", null, null
        );
        ApiFunctionalTestHelper.requireStatus(hierarchy, 200);
        if (!hierarchy.body().contains("\"seasons\"")) {
            throw new IllegalStateException("hierarchy response missing seasons: " + hierarchy.body());
        }
    }
}
