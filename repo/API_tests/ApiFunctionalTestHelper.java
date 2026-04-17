import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiFunctionalTestHelper {
    static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    static final String BASE_URL =
        System.getenv().getOrDefault("SILVERSTAGE_BASE_URL", "http://localhost:8080");

    private ApiFunctionalTestHelper() {}

    static HttpResponse<String> request(
        String method,
        String path,
        String body,
        Map<String, String> headers
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(Duration.ofSeconds(15));

        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static void requireStatus(HttpResponse<String> response, int... expected) {
        int actual = response.statusCode();
        for (int v : expected) {
            if (actual == v) return;
        }
        throw new IllegalStateException("unexpected status " + actual + " body=" + response.body());
    }

    static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static long extractFirstLong(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)").matcher(json);
        if (m.find()) return Long.parseLong(m.group(1));
        throw new IllegalStateException("missing numeric " + key + " in: " + json);
    }

    static String registerAndLogin(String role) throws Exception {
        String username = "api_" + role.toLowerCase() + "_" + System.currentTimeMillis();
        String password = "Passw0rd!23";

        String regBody = "{\"username\":\"" + username + "\",\"password\":\"" + password
            + "\",\"role\":\"" + role + "\"}";
        HttpResponse<String> reg = request(
            "POST", "/api/security/accounts", regBody,
            Map.of("Content-Type", "application/json")
        );
        requireStatus(reg, 200, 201);

        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> login = request(
            "POST", "/api/security/login", loginBody,
            Map.of("Content-Type", "application/json")
        );
        requireStatus(login, 200);
        String token = extractString(login.body(), "token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("no token in login response: " + login.body());
        }
        return token;
    }

    static String[] registerAndLoginWithUsername(String role) throws Exception {
        String username = "api_" + role.toLowerCase() + "_" + System.currentTimeMillis();
        String password = "Passw0rd!23";

        String regBody = "{\"username\":\"" + username + "\",\"password\":\"" + password
            + "\",\"role\":\"" + role + "\"}";
        request("POST", "/api/security/accounts", regBody,
            Map.of("Content-Type", "application/json"));

        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> login = request(
            "POST", "/api/security/login", loginBody,
            Map.of("Content-Type", "application/json")
        );
        String token = extractString(login.body(), "token");
        return new String[]{token, username};
    }
}
