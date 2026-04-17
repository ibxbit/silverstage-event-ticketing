package com.eaglepoint.venue.api;

import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApiTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());
    protected static final String DEFAULT_PASSWORD = "Passw0rd!23";

    protected String unique(String prefix) {
        return prefix + "_" + COUNTER.incrementAndGet();
    }

    // ---------- Registration & Login ----------

    protected ResponseEntity<String> registerUser(String username, String password, String role) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
        return restTemplate.exchange("/api/security/accounts", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    protected String registerAndLogin(String usernamePrefix, String role) {
        String username = unique(usernamePrefix);
        ResponseEntity<String> regResp = registerUser(username, DEFAULT_PASSWORD, role);
        assertTrue(regResp.getStatusCode().is2xxSuccessful(),
                "Registration failed for " + username + ": " + regResp.getBody());
        return loginUser(username, DEFAULT_PASSWORD);
    }

    protected String[] registerAndLoginWithUsername(String usernamePrefix, String role) {
        String username = unique(usernamePrefix);
        ResponseEntity<String> regResp = registerUser(username, DEFAULT_PASSWORD, role);
        assertTrue(regResp.getStatusCode().is2xxSuccessful(),
                "Registration failed for " + username + ": " + regResp.getBody());
        String token = loginUser(username, DEFAULT_PASSWORD);
        return new String[]{token, username};
    }

    protected String loginUser(String username, String password) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<String> resp = restTemplate.exchange("/api/security/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "Login failed for " + username + ": " + resp.getBody());
        return extractString(resp.getBody(), "token");
    }

    // ---------- HTTP Headers ----------

    protected HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Auth-Token", token);
        return h;
    }

    protected HttpHeaders jsonAuthHeaders(String token) {
        HttpHeaders h = jsonHeaders();
        h.set("X-Auth-Token", token);
        return h;
    }

    // ---------- HTTP Methods ----------

    protected ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(path, String.class);
    }

    protected ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    protected ResponseEntity<String> postJson(String path, String jsonBody) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(jsonBody, jsonHeaders()), String.class);
    }

    protected ResponseEntity<String> postJson(String path, String jsonBody, String token) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(jsonBody, jsonAuthHeaders(token)), String.class);
    }

    protected ResponseEntity<String> postNoBody(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    protected ResponseEntity<String> postEmpty(String path) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(jsonHeaders()), String.class);
    }

    protected ResponseEntity<String> patchJson(String path, String jsonBody, String token) {
        return restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(jsonBody, jsonAuthHeaders(token)), String.class);
    }

    protected ResponseEntity<String> patchNoBody(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    protected ResponseEntity<String> postMultipart(String path, MultiValueMap<String, Object> parts, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.set("X-Auth-Token", token);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(parts, h), String.class);
    }

    protected ResponseEntity<String> postMultipartNoAuth(String path, MultiValueMap<String, Object> parts) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(parts, h), String.class);
    }

    protected ResponseEntity<byte[]> getBytes(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
    }

    // ---------- Multipart Helpers ----------

    protected ByteArrayResource fileResource(final String filename, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    protected MultiValueMap<String, Object> multipartMap() {
        return new LinkedMultiValueMap<>();
    }

    // ---------- JSON Extraction ----------

    protected String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        fail("String key '" + key + "' not found in: " + truncate(json));
        return null;
    }

    protected long extractLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Long.parseLong(m.group(1));
        fail("Numeric key '" + key + "' not found in: " + truncate(json));
        return -1;
    }

    protected boolean bodyContains(String body, String key) {
        return body != null && body.contains("\"" + key + "\"");
    }

    protected void assertStatus(ResponseEntity<?> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(),
                "Expected " + expected + " but got " + response.getStatusCode()
                        + " body=" + truncate(String.valueOf(response.getBody())));
    }

    private String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
