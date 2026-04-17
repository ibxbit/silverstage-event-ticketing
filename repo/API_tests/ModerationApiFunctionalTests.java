import java.net.http.HttpResponse;
import java.util.Map;

public final class ModerationApiFunctionalTests {

    private ModerationApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        long reportId = testSubmitReport(token);
        testGetReportsRequiresAdmin();
        testGetReportsWithAdmin();
        testDecideReport(reportId);
        testGetPenalties(token, username);
        testGetNotifications(token, username);
    }

    private static long testSubmitReport(String token) throws Exception {
        String boundary = "----SilverStageBoundary" + System.currentTimeMillis();
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"reportedUser\"\r\n\r\nreported_demo_user\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"contentType\"\r\n\r\nANNOUNCEMENT\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"contentRef\"\r\n\r\nannouncement:3\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"reason\"\r\n\r\nAPI functional test report\r\n"
            + "--" + boundary + "--\r\n";

        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/moderation/reports", body,
            Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "X-Auth-Token", token
            )
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"reportId\"")) {
            throw new IllegalStateException("submit report response missing reportId: " + resp.body());
        }
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), "reportId");
    }

    private static void testGetReportsRequiresAdmin() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/moderation/reports", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 401, 403);
    }

    private static void testGetReportsWithAdmin() throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/moderation/reports", null,
            Map.of("X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("reports list should be an array: " + body);
        }
    }

    private static void testDecideReport(long reportId) throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        String body = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"API functional test decision\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/moderation/reports/" + reportId + "/decision", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"reportId\"")) {
            throw new IllegalStateException("decision response missing reportId: " + resp.body());
        }
    }

    private static void testGetPenalties(String token, String username) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/moderation/users/" + username + "/penalties", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("penalties list should be an array: " + body);
        }
    }

    private static void testGetNotifications(String token, String username) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/moderation/users/" + username + "/notifications", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("notifications list should be an array: " + body);
        }
    }
}
