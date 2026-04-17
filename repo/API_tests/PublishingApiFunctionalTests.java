import java.net.http.HttpResponse;
import java.util.Map;

public final class PublishingApiFunctionalTests {

    private PublishingApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        long contentId = testCreateDraft(token);
        testListContent(token);
        testUpdateDraft(token, contentId);
        testSubmitContent(token, contentId);

        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        testReviewContent(adminToken, contentId);
        testPublishContent(adminToken, contentId);

        long appealId = testRequestAppeal(token, contentId);
        testDecideAppeal(adminToken, appealId);
        testApplyCorrection(token, contentId, appealId);
        testGetVersions(token, contentId);
        testGetDiff(token, contentId);
        testGetAudit(token, contentId);
        testRollback(adminToken, contentId);
    }

    private static long testCreateDraft(String token) throws Exception {
        String body = "{\"title\":\"Publishing Test " + System.currentTimeMillis()
            + "\",\"body\":\"Test body for API functional test.\",\"summary\":\"Summary\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"contentId\"")) {
            throw new IllegalStateException("create draft response missing contentId: " + resp.body());
        }
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), "contentId");
    }

    private static void testListContent(String token) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/publishing/content", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("publishing list should be an array: " + body);
        }
    }

    private static void testUpdateDraft(String token, long contentId) throws Exception {
        String body = "{\"title\":\"Updated Title " + System.currentTimeMillis()
            + "\",\"body\":\"Updated body.\",\"summary\":\"Updated summary\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/update", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"contentId\"")) {
            throw new IllegalStateException("update draft response missing contentId: " + resp.body());
        }
    }

    private static void testSubmitContent(String token, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/submit", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String state = ApiFunctionalTestHelper.extractString(resp.body(), "state");
        if (!"SUBMISSION".equalsIgnoreCase(state)) {
            throw new IllegalStateException(
                "expected state SUBMISSION after submit, got: " + state
            );
        }
    }

    private static void testReviewContent(String adminToken, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/review", null,
            Map.of("X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
    }

    private static void testPublishContent(String adminToken, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/publish", null,
            Map.of("X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String state = ApiFunctionalTestHelper.extractString(resp.body(), "state");
        if (!"PUBLISH".equalsIgnoreCase(state)) {
            throw new IllegalStateException("expected state PUBLISH after publish, got: " + state);
        }
    }

    private static long testRequestAppeal(String token, long contentId) throws Exception {
        String body = "{\"reason\":\"Appeal reason for API functional test\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/appeals", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"appealId\"")) {
            throw new IllegalStateException("appeal response missing appealId: " + resp.body());
        }
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), "appealId");
    }

    private static void testDecideAppeal(String adminToken, long appealId) throws Exception {
        String body = "{\"decision\":\"APPROVED\",\"note\":\"Approved by API functional test\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/appeals/" + appealId + "/decision", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
    }

    private static void testApplyCorrection(String token, long contentId, long appealId) throws Exception {
        String body = "{\"title\":\"Corrected Title\",\"body\":\"Corrected body.\",\"summary\":\"Corrected summary\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/corrections?appealId=" + appealId, body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
    }

    private static void testGetVersions(String token, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/publishing/content/" + contentId + "/versions", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("versions should be an array: " + body);
        }
    }

    private static void testGetDiff(String token, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/publishing/content/" + contentId + "/diff?leftVersion=1&rightVersion=2", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"contentId\"") && !resp.body().contains("\"leftVersion\"")) {
            throw new IllegalStateException("diff response missing expected fields: " + resp.body());
        }
    }

    private static void testGetAudit(String token, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/publishing/content/" + contentId + "/audit", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("audit should be an array: " + body);
        }
    }

    private static void testRollback(String adminToken, long contentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/publishing/content/" + contentId + "/rollback?targetVersion=1", null,
            Map.of("X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"contentId\"")) {
            throw new IllegalStateException("rollback response missing contentId: " + resp.body());
        }
    }
}
