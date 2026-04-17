package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationApiIntegrationTest extends ApiTestBase {

    private String seniorToken;
    private String seniorUsername;
    private String moderatorToken;
    private String moderatorUsername;

    @BeforeAll
    void setUpUsers() {
        String[] senior = registerAndLoginWithUsername("mod_senior", "SENIOR");
        seniorToken = senior[0];
        seniorUsername = senior[1];

        String[] moderator = registerAndLoginWithUsername("mod_orgadmin", "ORG_ADMIN");
        moderatorToken = moderator[0];
        moderatorUsername = moderator[1];
    }

    // -----------------------------------------------------------------------
    // POST /api/moderation/reports
    // -----------------------------------------------------------------------

    @Test
    void submitReport_success() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", "reported_demo_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Test moderation report");

        ResponseEntity<String> resp = postMultipart("/api/moderation/reports", parts, seniorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "reportId"), "Body should contain 'reportId' key");
    }

    @Test
    void submitReport_unauthorized() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", "reported_demo_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Test moderation report");

        ResponseEntity<String> resp = postMultipartNoAuth("/api/moderation/reports", parts);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // GET /api/moderation/reports
    // -----------------------------------------------------------------------

    @Test
    void listOpenReports_success() {
        ResponseEntity<String> resp = get("/api/moderation/reports", moderatorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void listOpenReports_unauthorized() {
        ResponseEntity<String> resp = get("/api/moderation/reports");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listOpenReports_forbidden_senior() {
        ResponseEntity<String> resp = get("/api/moderation/reports", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // POST /api/moderation/reports/{reportId}/decision
    // -----------------------------------------------------------------------

    @Test
    void decideReport_success() {
        // Submit a report first to obtain a real reportId
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", "reported_demo_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Test moderation report for decision");

        ResponseEntity<String> submitResp = postMultipart("/api/moderation/reports", parts, seniorToken);
        assertStatus(submitResp, HttpStatus.CREATED);
        assertNotNull(submitResp.getBody(), "Submit report body must not be null");

        long reportId = extractLong(submitResp.getBody(), "reportId");

        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Violation confirmed\"}";
        ResponseEntity<String> decisionResp = postJson(
                "/api/moderation/reports/" + reportId + "/decision",
                decisionJson,
                moderatorToken
        );

        assertStatus(decisionResp, HttpStatus.OK);
        assertNotNull(decisionResp.getBody(), "Decision response body must not be null");
        assertTrue(bodyContains(decisionResp.getBody(), "reportId"), "Body should contain 'reportId' key");
    }

    @Test
    void decideReport_unauthorized() {
        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Violation confirmed\"}";
        ResponseEntity<String> resp = postJson("/api/moderation/reports/99999/decision", decisionJson);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void decideReport_forbidden_senior() {
        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Violation confirmed\"}";
        ResponseEntity<String> resp = postJson(
                "/api/moderation/reports/99999/decision",
                decisionJson,
                seniorToken
        );

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void decideReport_invalidReportId() {
        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Violation confirmed\"}";
        ResponseEntity<String> resp = postJson(
                "/api/moderation/reports/99999/decision",
                decisionJson,
                moderatorToken
        );

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for decision on non-existent reportId=99999 but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // GET /api/moderation/users/{username}/penalties
    // -----------------------------------------------------------------------

    @Test
    void penalties_self() {
        ResponseEntity<String> resp = get("/api/moderation/users/" + seniorUsername + "/penalties", seniorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void penalties_otherUser_forbidden() {
        ResponseEntity<String> resp = get("/api/moderation/users/other_user/penalties", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void penalties_moderatorCanSeeAnyUser() {
        ResponseEntity<String> resp = get("/api/moderation/users/" + seniorUsername + "/penalties", moderatorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    // -----------------------------------------------------------------------
    // GET /api/moderation/users/{username}/notifications
    // -----------------------------------------------------------------------

    @Test
    void notifications_self() {
        ResponseEntity<String> resp = get("/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void notifications_otherUser_forbidden() {
        ResponseEntity<String> resp = get("/api/moderation/users/other_user/notifications", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // PATCH /api/moderation/notifications/{notificationId}/read
    // -----------------------------------------------------------------------

    @Test
    void markNotificationRead_unauthorized() {
        // Invalid token must be rejected — service requires a valid authenticated session
        ResponseEntity<String> resp = patchNoBody("/api/moderation/notifications/99999/read", "invalid-token");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void markNotificationRead_noToken_returns401() {
        // Omitting the X-Auth-Token header entirely must also yield 401
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/moderation/notifications/99999/read",
                HttpMethod.PATCH,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void markNotificationRead_success() {
        // Step 1: Submit a report to trigger a notification for the reported user
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", seniorUsername);
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Trigger notification for mark-read test");

        ResponseEntity<String> submitResp = postMultipart("/api/moderation/reports", parts, moderatorToken);
        assertStatus(submitResp, HttpStatus.CREATED);
        long reportId = extractLong(submitResp.getBody(), "reportId");

        // Step 2: Decide on the report to generate a notification for the senior
        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Mark-read test\"}";
        postJson("/api/moderation/reports/" + reportId + "/decision", decisionJson, moderatorToken);

        // Step 3: Load notifications for the senior user
        ResponseEntity<String> notifResp = get(
                "/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);
        assertStatus(notifResp, HttpStatus.OK);
        assertNotNull(notifResp.getBody(), "Notifications body must not be null");

        // Step 4: Extract a notification ID and mark it as read
        long notificationId = extractLong(notifResp.getBody(), "notificationId");
        ResponseEntity<String> markResp = patchNoBody(
                "/api/moderation/notifications/" + notificationId + "/read", seniorToken);
        assertStatus(markResp, HttpStatus.NO_CONTENT);
    }

    @Test
    void markNotificationRead_ownershipViolation_returns403() {
        // Create a notification for seniorUsername by triggering a report+decision
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", seniorUsername);
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Ownership violation test — generate notification for senior");

        ResponseEntity<String> submitResp = postMultipart("/api/moderation/reports", parts, moderatorToken);
        assertStatus(submitResp, HttpStatus.CREATED);
        long reportId = extractLong(submitResp.getBody(), "reportId");

        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Ownership test\"}";
        postJson("/api/moderation/reports/" + reportId + "/decision", decisionJson, moderatorToken);

        // Retrieve the notification ID belonging to the senior
        ResponseEntity<String> notifResp = get(
                "/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);
        assertStatus(notifResp, HttpStatus.OK);
        assertNotNull(notifResp.getBody(), "Notifications body must not be null");
        long notificationId = extractLong(notifResp.getBody(), "notificationId");

        // A different non-admin user (freshly registered senior) must NOT be allowed to mark it read
        String otherSeniorToken = registerAndLogin("mod_other_senior", "SENIOR");
        ResponseEntity<String> forbiddenResp = patchNoBody(
                "/api/moderation/notifications/" + notificationId + "/read", otherSeniorToken);
        assertStatus(forbiddenResp, HttpStatus.FORBIDDEN);
    }

    @Test
    void markNotificationRead_readStateVerifiedViaFollowUp() {
        // Step 1: Generate a notification for the senior via report+decision
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", seniorUsername);
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Read-state follow-up verification test");

        ResponseEntity<String> submitResp = postMultipart("/api/moderation/reports", parts, moderatorToken);
        assertStatus(submitResp, HttpStatus.CREATED);
        long reportId = extractLong(submitResp.getBody(), "reportId");

        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Read-state test\"}";
        postJson("/api/moderation/reports/" + reportId + "/decision", decisionJson, moderatorToken);

        // Step 2: Retrieve the notification ID
        ResponseEntity<String> notifBefore = get(
                "/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);
        assertStatus(notifBefore, HttpStatus.OK);
        assertNotNull(notifBefore.getBody(), "Notifications body must not be null");
        long notificationId = extractLong(notifBefore.getBody(), "notificationId");

        // Step 3: Mark the notification as read
        ResponseEntity<String> markResp = patchNoBody(
                "/api/moderation/notifications/" + notificationId + "/read", seniorToken);
        assertStatus(markResp, HttpStatus.NO_CONTENT);

        // Step 4: Fetch notifications again and verify the readFlag changed to "Y"
        ResponseEntity<String> notifAfter = get(
                "/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);
        assertStatus(notifAfter, HttpStatus.OK);
        assertNotNull(notifAfter.getBody(), "Post-read notifications body must not be null");
        assertTrue(notifAfter.getBody().contains("\"readFlag\":\"Y\""),
                "After marking as read, at least one notification should have readFlag='Y' but body was: "
                        + notifAfter.getBody());
    }

    @Test
    void markNotificationRead_nonexistentNotification() {
        // A valid authenticated user attempts to mark a notification that does not exist
        ResponseEntity<String> resp = patchNoBody(
                "/api/moderation/notifications/999999/read", seniorToken);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for nonexistent notificationId=999999 but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Report Spoofing — reporter identity enforcement
    // -----------------------------------------------------------------------

    @Test
    void submitReport_reporterSpoofing_nonPrivileged_forbidden() {
        // Authenticated senior tries to submit report as someone else
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reporterUser", "spoofed_reporter");
        parts.add("reportedUser", "some_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:1");
        parts.add("reason", "Spoofed reporter test");

        ResponseEntity<String> resp = postMultipart("/api/moderation/reports", parts, seniorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void submitReport_reporterSpoofing_privileged_allowed() {
        // Moderator (ORG_ADMIN) CAN submit on behalf of another reporter
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reporterUser", "delegated_reporter");
        parts.add("reportedUser", "some_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:1");
        parts.add("reason", "Delegated reporter test");

        ResponseEntity<String> resp = postMultipart("/api/moderation/reports", parts, moderatorToken);
        assertStatus(resp, HttpStatus.CREATED);
        assertTrue(bodyContains(resp.getBody(), "reportId"), "Should create report successfully");
    }

    // -----------------------------------------------------------------------
    // Full Workflow
    // -----------------------------------------------------------------------

    @Test
    void moderationWorkflow_fullCycle() {
        // Step 1: Submit a report
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("reportedUser", "reported_demo_user");
        parts.add("contentType", "ANNOUNCEMENT");
        parts.add("contentRef", "announcement:3");
        parts.add("reason", "Full cycle test report");

        ResponseEntity<String> submitResp = postMultipart("/api/moderation/reports", parts, seniorToken);
        assertStatus(submitResp, HttpStatus.CREATED);
        assertNotNull(submitResp.getBody(), "Submit response body must not be null");
        assertTrue(bodyContains(submitResp.getBody(), "reportId"), "Submit body should contain 'reportId'");
        long reportId = extractLong(submitResp.getBody(), "reportId");

        // Step 2: List open reports as moderator
        ResponseEntity<String> listResp = get("/api/moderation/reports", moderatorToken);
        assertStatus(listResp, HttpStatus.OK);
        assertNotNull(listResp.getBody(), "List reports body must not be null");

        // Step 3: Decide on the report
        String decisionJson = "{\"penaltyType\":\"MUTE_24H\",\"decisionNotes\":\"Full cycle violation confirmed\"}";
        ResponseEntity<String> decisionResp = postJson(
                "/api/moderation/reports/" + reportId + "/decision",
                decisionJson,
                moderatorToken
        );
        assertStatus(decisionResp, HttpStatus.OK);
        assertNotNull(decisionResp.getBody(), "Decision response body must not be null");
        assertTrue(bodyContains(decisionResp.getBody(), "reportId"), "Decision body should contain 'reportId'");

        // Step 4: Check penalties for the senior user (self-access)
        ResponseEntity<String> penaltiesResp = get("/api/moderation/users/" + seniorUsername + "/penalties", seniorToken);
        assertStatus(penaltiesResp, HttpStatus.OK);
        assertNotNull(penaltiesResp.getBody(), "Penalties response body must not be null");

        // Step 5: Check notifications for the senior user (self-access)
        ResponseEntity<String> notifResp = get("/api/moderation/users/" + seniorUsername + "/notifications", seniorToken);
        assertStatus(notifResp, HttpStatus.OK);
        assertNotNull(notifResp.getBody(), "Notifications response body must not be null");
    }
}
