import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiFunctionalTests {
    private static int total;
    private static int passed;
    private static int failed;

    private ApiFunctionalTests() {}

    public static void main(String[] args) throws Exception {
        waitForApiReady();

        // Shared auth context: register a SENIOR user used by most domain tests
        String[] seniorAuth = ApiFunctionalTestHelper.registerAndLoginWithUsername("SENIOR");
        final String userToken = seniorAuth[0];
        final String userName = seniorAuth[1];

        run("security_flow", () -> SecurityApiFunctionalTests.run(userToken, userName));
        run("discovery_flow", () -> DiscoveryApiFunctionalTests.run(userToken, userName));
        run("events_flow", () -> EventsApiFunctionalTests.run(userToken, userName));
        run("ticketing_flow", () -> TicketingApiFunctionalTests.run(userToken, userName));
        run("seat_reservation_flow", () -> seatReservationFlow(userToken, userName));
        run("moderation_flow", () -> ModerationApiFunctionalTests.run(userToken, userName));
        run("publishing_flow", () -> PublishingApiFunctionalTests.run(userToken, userName));
        run("files_flow", () -> FilesApiFunctionalTests.run(userToken, userName));
        run("payments_flow", () -> PaymentsApiFunctionalTests.run(userToken, userName));

        writeSummary();
        if (failed > 0) {
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // Seat reservation flow (kept inline — uses multiple helpers directly)
    // -------------------------------------------------------------------------

    private static void seatReservationFlow(String token, String username) throws Exception {
        HttpResponse<String> events = ApiFunctionalTestHelper.request("GET", "/api/events", null, null);
        ApiFunctionalTestHelper.requireStatus(events, 200);
        long eventId = ApiFunctionalTestHelper.extractFirstLong(events.body(), "id");

        HttpResponse<String> hierarchy = ApiFunctionalTestHelper.request(
            "GET", "/api/events/" + eventId + "/hierarchy", null, null
        );
        ApiFunctionalTestHelper.requireStatus(hierarchy, 200);
        long sessionId = extractSessionId(hierarchy.body());

        HttpResponse<String> ticketTypes = ApiFunctionalTestHelper.request(
            "GET", "/api/events/" + eventId + "/ticket-types", null, null
        );
        ApiFunctionalTestHelper.requireStatus(ticketTypes, 200);
        long ticketTypeId = ApiFunctionalTestHelper.extractFirstLong(ticketTypes.body(), "id");

        HttpResponse<String> orderResponse = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpResponse<String> seatMap = ApiFunctionalTestHelper.request(
                "GET",
                "/api/sessions/" + sessionId + "/seat-map?ticketTypeId=" + ticketTypeId + "&channel=ONLINE_PORTAL",
                null,
                null
            );
            ApiFunctionalTestHelper.requireStatus(seatMap, 200);
            long seatId = extractAvailableSeatId(seatMap.body());

            String orderBody = "{"
                + "\"eventId\":" + eventId + ","
                + "\"sessionId\":" + sessionId + ","
                + "\"ticketTypeId\":" + ticketTypeId + ","
                + "\"orderCode\":\"API-" + System.currentTimeMillis() + "-" + attempt + "\","
                + "\"buyerReference\":\"" + username + "\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[" + seatId + "]"
                + "}";
            orderResponse = ApiFunctionalTestHelper.request(
                "POST",
                "/api/seat-orders",
                orderBody,
                Map.of(
                    "Content-Type", "application/json",
                    "X-Auth-Token", token
                )
            );

            if (orderResponse.statusCode() == 409 && attempt < 3) {
                Thread.sleep(200);
                continue;
            }
            ApiFunctionalTestHelper.requireStatus(orderResponse, 200, 201);
            break;
        }

        if (orderResponse == null) {
            throw new IllegalStateException("seat order did not execute");
        }
        if (!orderResponse.body().contains("\"orderId\"")) {
            throw new IllegalStateException("seat order missing orderId: " + orderResponse.body());
        }
    }

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    private static void waitForApiReady() throws Exception {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> r = ApiFunctionalTestHelper.request("GET", "/api/events", null, null);
            if (r.statusCode() == 200) {
                return;
            }
            Thread.sleep(1500);
        }
        throw new IllegalStateException("API did not become ready at " + ApiFunctionalTestHelper.BASE_URL);
    }

    private static void run(String name, ThrowingRunnable test) {
        total += 1;
        try {
            test.run();
            passed += 1;
            System.out.println("[PASS] " + name);
        } catch (Exception ex) {
            failed += 1;
            System.out.println("[FAIL] " + name + ": " + ex.getMessage());
        }
    }

    private static void writeSummary() throws IOException {
        Path targetDir = Path.of("target");
        Files.createDirectories(targetDir);
        Path summary = targetDir.resolve("api-test-summary.properties");

        Map<String, String> values = new LinkedHashMap<>();
        values.put("suite", "api_functional");
        values.put("total", String.valueOf(total));
        values.put("passed", String.valueOf(passed));
        values.put("failed", String.valueOf(failed));

        try (OutputStream output = Files.newOutputStream(summary)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String line = entry.getKey() + "=" + entry.getValue() + "\n";
                output.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }

        System.out.println("api_functional total=" + total + " passed=" + passed + " failed=" + failed);
    }

    private static long extractSessionId(String json) {
        java.util.regex.Pattern pattern =
            java.util.regex.Pattern.compile("\"id\"\\s*:\\s*([0-9]+)\\s*,\\s*\"title\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new IllegalStateException("no session id found in hierarchy: " + json);
    }

    private static long extractAvailableSeatId(String json) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"seatId\"\\s*:\\s*([0-9]+).*?\"status\"\\s*:\\s*\"AVAILABLE\"",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new IllegalStateException("no available seat found in seat map: " + json);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
