import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiFunctionalTests {
  private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  private static final String BASE_URL =
      System.getenv().getOrDefault("SILVERSTAGE_BASE_URL", "http://localhost:8080");

  private static int total;
  private static int passed;
  private static int failed;

  private static String token;
  private static String username;

  private ApiFunctionalTests() {}

  public static void main(String[] args) throws Exception {
    waitForApiReady();

    run("registration_authentication_flow", ApiFunctionalTests::registrationAuthenticationFlow);
    run("event_search_flow", ApiFunctionalTests::eventSearchFlow);
    run("seat_reservation_flow", ApiFunctionalTests::seatReservationFlow);
    run("moderation_reporting_flow", ApiFunctionalTests::moderationReportingFlow);

    writeSummary();
    if (failed > 0) {
      System.exit(1);
    }
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

  private static void waitForApiReady() throws Exception {
    Instant deadline = Instant.now().plusSeconds(90);
    while (Instant.now().isBefore(deadline)) {
      HttpResponse<String> response = request("GET", "/api/events", null, null);
      if (response.statusCode() == 200) {
        return;
      }
      Thread.sleep(1500);
    }
    throw new IllegalStateException("API did not become ready at " + BASE_URL);
  }

  private static void registrationAuthenticationFlow() throws Exception {
    username = "api_user_" + System.currentTimeMillis();
    String password = "Passw0rd!23";

    String registrationBody = "{"
        + "\"username\":\"" + username + "\"," 
        + "\"password\":\"" + password + "\"," 
        + "\"role\":\"SENIOR\""
        + "}";
    HttpResponse<String> registerResponse = request(
        "POST",
        "/api/security/accounts",
        registrationBody,
        Map.of("Content-Type", "application/json")
    );
    requireStatus(registerResponse, 200, 201);

    String loginBody = "{"
        + "\"username\":\"" + username + "\"," 
        + "\"password\":\"" + password + "\""
        + "}";
    HttpResponse<String> loginResponse = request(
        "POST",
        "/api/security/login",
        loginBody,
        Map.of("Content-Type", "application/json")
    );
    requireStatus(loginResponse, 200);
    token = extractString(loginResponse.body(), "token");
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("missing token in login response: " + loginResponse.body());
    }
  }

  private static void eventSearchFlow() throws Exception {
    HttpResponse<String> events = request("GET", "/api/events", null, null);
    requireStatus(events, 200);
    if (!events.body().contains("\"id\"")) {
      throw new IllegalStateException("events response did not include ids: " + events.body());
    }

    HttpResponse<String> search = request(
        "GET",
        "/api/discovery/search?q=choir&type=ALL&author=&category=&sort=relevance&page=0&size=5",
        null,
        null
    );
    requireStatus(search, 200);
    if (!search.body().contains("\"items\"")) {
      throw new IllegalStateException("search response missing items: " + search.body());
    }
  }

  private static void seatReservationFlow() throws Exception {
    HttpResponse<String> events = request("GET", "/api/events", null, null);
    requireStatus(events, 200);
    long eventId = extractFirstLong(events.body(), "id");

    HttpResponse<String> hierarchy = request("GET", "/api/events/" + eventId + "/hierarchy", null, null);
    requireStatus(hierarchy, 200);
    long sessionId = extractSessionId(hierarchy.body());

    HttpResponse<String> ticketTypes = request("GET", "/api/events/" + eventId + "/ticket-types", null, null);
    requireStatus(ticketTypes, 200);
    long ticketTypeId = extractFirstLong(ticketTypes.body(), "id");

    HttpResponse<String> orderResponse = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      HttpResponse<String> seatMap = request(
          "GET",
          "/api/sessions/" + sessionId + "/seat-map?ticketTypeId=" + ticketTypeId + "&channel=ONLINE_PORTAL",
          null,
          null
      );
      requireStatus(seatMap, 200);
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
      orderResponse = request(
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
      requireStatus(orderResponse, 200, 201);
      break;
    }

    if (orderResponse == null) {
      throw new IllegalStateException("seat order did not execute");
    }
    if (!orderResponse.body().contains("\"orderId\"")) {
      throw new IllegalStateException("seat order missing orderId: " + orderResponse.body());
    }
  }

  private static void moderationReportingFlow() throws Exception {
    String boundary = "----SilverStageBoundary" + System.currentTimeMillis();
    String multipartBody = "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"reportedUser\"\r\n\r\n"
        + "reported_demo_user\r\n"
        + "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"contentType\"\r\n\r\n"
        + "ANNOUNCEMENT\r\n"
        + "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"contentRef\"\r\n\r\n"
        + "announcement:3\r\n"
        + "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"reason\"\r\n\r\n"
        + "API functional test report\r\n"
        + "--" + boundary + "--\r\n";

    HttpResponse<String> reportResponse = request(
        "POST",
        "/api/moderation/reports",
        multipartBody,
        Map.of(
            "Content-Type", "multipart/form-data; boundary=" + boundary,
            "X-Auth-Token", token
        )
    );
    requireStatus(reportResponse, 200, 201);
    if (!reportResponse.body().contains("\"reportId\"")) {
      throw new IllegalStateException("moderation response missing reportId: " + reportResponse.body());
    }
  }

  private static HttpResponse<String> request(
      String method,
      String path,
      String body,
      Map<String, String> headers
  ) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + path))
        .timeout(Duration.ofSeconds(15));

    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }
    }

    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }

    return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static void requireStatus(HttpResponse<String> response, int... expected) {
    int actual = response.statusCode();
    for (int value : expected) {
      if (actual == value) {
        return;
      }
    }
    throw new IllegalStateException("unexpected status " + actual + " body=" + response.body());
  }

  private static String extractString(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private static long extractFirstLong(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException("missing numeric field " + key + " in: " + json);
  }

  private static long extractSessionId(String json) {
    Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*([0-9]+)\\s*,\\s*\"title\"");
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException("no session id found in hierarchy: " + json);
  }

  private static long extractAvailableSeatId(String json) {
    Pattern pattern = Pattern.compile(
        "\"seatId\"\\s*:\\s*([0-9]+).*?\"status\"\\s*:\\s*\"AVAILABLE\"",
        Pattern.DOTALL
    );
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException("no available seat found in seat map: " + json);
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

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
