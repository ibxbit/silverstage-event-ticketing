import java.net.http.HttpResponse;
import java.util.Map;

public final class SecurityApiFunctionalTests {

    private SecurityApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        testRegisterSuccess();
        testLoginSuccess();
        testMenuReturnsMenus(token);
        testSubmitVerificationAuthenticated(token);
        testPendingVerificationsRequiresAdmin();
    }

    private static void testRegisterSuccess() throws Exception {
        String name = "reg_test_" + System.currentTimeMillis();
        String body = "{\"username\":\"" + name + "\",\"password\":\"Passw0rd!23\",\"role\":\"SENIOR\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/security/accounts", body,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        String respBody = resp.body();
        if (!respBody.contains("\"id\"")) {
            throw new IllegalStateException("register response missing id: " + respBody);
        }
        if (respBody.contains("passwordHash") || respBody.contains("password")) {
            throw new IllegalStateException("register response must not expose password/passwordHash: " + respBody);
        }
        if (!respBody.contains("\"username\"")) {
            throw new IllegalStateException("register response missing username: " + respBody);
        }
    }

    private static void testLoginSuccess() throws Exception {
        String name = "login_test_" + System.currentTimeMillis();
        String regBody = "{\"username\":\"" + name + "\",\"password\":\"Passw0rd!23\",\"role\":\"SENIOR\"}";
        ApiFunctionalTestHelper.request(
            "POST", "/api/security/accounts", regBody,
            Map.of("Content-Type", "application/json")
        );

        String loginBody = "{\"username\":\"" + name + "\",\"password\":\"Passw0rd!23\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/security/login", loginBody,
            Map.of("Content-Type", "application/json")
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String tok = ApiFunctionalTestHelper.extractString(resp.body(), "token");
        if (tok == null || tok.isBlank()) {
            throw new IllegalStateException("login response missing token: " + resp.body());
        }
    }

    private static void testMenuReturnsMenus(String token) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/security/menu", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"menus\"")) {
            throw new IllegalStateException("menu response missing menus key: " + resp.body());
        }
    }

    private static void testSubmitVerificationAuthenticated(String token) throws Exception {
        String verBody = "{\"fullName\":\"Test User\",\"idType\":\"PASSPORT\",\"idNumber\":\"VER-" + System.currentTimeMillis() + "\"}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/security/verification", verBody,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"id\"")) {
            throw new IllegalStateException("verification response missing id: " + resp.body());
        }
    }

    private static void testPendingVerificationsRequiresAdmin() throws Exception {
        // Without token → 401
        HttpResponse<String> noAuth = ApiFunctionalTestHelper.request(
            "GET", "/api/security/verification/pending", null, null
        );
        ApiFunctionalTestHelper.requireStatus(noAuth, 401);

        // Admin token → 200
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        HttpResponse<String> adminResp = ApiFunctionalTestHelper.request(
            "GET", "/api/security/verification/pending", null,
            Map.of("X-Auth-Token", adminToken)
        );
        ApiFunctionalTestHelper.requireStatus(adminResp, 200);
    }
}
