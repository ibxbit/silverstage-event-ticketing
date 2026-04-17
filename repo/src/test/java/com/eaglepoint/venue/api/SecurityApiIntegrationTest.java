package com.eaglepoint.venue.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityApiIntegrationTest extends ApiTestBase {

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    @Test
    void registerAccount_success() {
        String username = unique("reg_ok");
        ResponseEntity<String> resp = registerUser(username, DEFAULT_PASSWORD, "SENIOR");

        assertStatus(resp, HttpStatus.CREATED);
        String body = resp.getBody();
        assertNotNull(body, "Response body must not be null");
        assertTrue(bodyContains(body, "username"), "Body should contain 'username' key");
        assertTrue(bodyContains(body, "role"), "Body should contain 'role' key");
        assertFalse(body.contains("passwordHash"), "Body must not expose passwordHash");
        assertFalse(body.contains("failedAttempts"), "Body must not expose failedAttempts");
        assertFalse(body.contains("lockoutUntil"), "Body must not expose lockoutUntil");
    }

    @Test
    void registerAccount_validationFailure_blankUsername() {
        ResponseEntity<String> resp = registerUser("", DEFAULT_PASSWORD, "SENIOR");

        assertTrue(resp.getStatusCode().value() == 400,
                "Expected 400 for blank username but got: " + resp.getStatusCode());
    }

    @Test
    void registerAccount_duplicateUsername() {
        String username = unique("dup_user");
        ResponseEntity<String> first = registerUser(username, DEFAULT_PASSWORD, "SENIOR");
        assertTrue(first.getStatusCode().is2xxSuccessful(), "First registration should succeed");

        ResponseEntity<String> second = registerUser(username, DEFAULT_PASSWORD, "SENIOR");
        int status = second.getStatusCode().value();
        assertTrue(status == 409 || status == 400,
                "Duplicate registration should return 409 or 400 but got: " + status);
    }

    // -----------------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------------

    @Test
    void login_success() {
        String username = unique("login_ok");
        registerUser(username, DEFAULT_PASSWORD, "SENIOR");

        String body = "{\"username\":\"" + username + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}";
        ResponseEntity<String> resp = postJson("/api/security/login", body);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "token"), "Body should contain 'token' key");
    }

    @Test
    void login_badPassword() {
        String username = unique("login_bad");
        registerUser(username, DEFAULT_PASSWORD, "SENIOR");

        String body = "{\"username\":\"" + username + "\",\"password\":\"WrongP@ss99\"}";
        ResponseEntity<String> resp = postJson("/api/security/login", body);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for bad password but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Menu
    // -----------------------------------------------------------------------

    @Test
    void menu_withToken() {
        String token = registerAndLogin("menu_auth", "SENIOR");

        ResponseEntity<String> resp = get("/api/security/menu", token);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "menus"), "Body should contain 'menus' key");
    }

    @Test
    void menu_withoutToken() {
        ResponseEntity<String> resp = get("/api/security/menu");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "menus"), "Body should contain 'menus' key");
    }

    @Test
    void menu_seniorRole_containsExpectedMenus() {
        String token = registerAndLogin("menu_senior_role", "SENIOR");
        ResponseEntity<String> resp = get("/api/security/menu", token);
        assertStatus(resp, HttpStatus.OK);
        String body = resp.getBody();
        assertTrue(bodyContains(body, "menus"), "Body should contain menus key");
        // Senior should see at least Discovery
        assertTrue(body.contains("Discovery") || body.contains("discovery"),
            "Senior menus should include Discovery");
    }

    @Test
    void menu_adminRole_containsAdminMenus() {
        String token = registerAndLogin("menu_admin_role", "ORG_ADMIN");
        ResponseEntity<String> resp = get("/api/security/menu", token);
        assertStatus(resp, HttpStatus.OK);
        String body = resp.getBody();
        assertTrue(bodyContains(body, "menus"), "Body should contain menus key");
        // Admin should see Moderation
        assertTrue(body.contains("Moderation") || body.contains("moderation"),
            "Admin menus should include Moderation");
    }

    // -----------------------------------------------------------------------
    // Login Lockout
    // -----------------------------------------------------------------------

    @Test
    void login_repeatedFailures_locksAccount() {
        String username = unique("lockout_user");
        registerUser(username, DEFAULT_PASSWORD, "SENIOR");

        // Attempt login with wrong password multiple times
        for (int i = 0; i < 5; i++) {
            postJson("/api/security/login", "{\"username\":\"" + username + "\",\"password\":\"WrongP@ss" + i + "!\"}");
        }

        // Now even correct password should fail (account locked)
        ResponseEntity<String> resp = postJson("/api/security/login",
            "{\"username\":\"" + username + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}");
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Locked account should reject login, got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Identity Verification — Submit
    // -----------------------------------------------------------------------

    @Test
    void submitVerification_success() {
        String token = registerAndLogin("verif_ok", "SENIOR");

        String body = "{\"fullName\":\"Test User\",\"idType\":\"PASSPORT\",\"idNumber\":\"AB123456\"}";
        ResponseEntity<String> resp = postJson("/api/security/verification", body, token);

        assertStatus(resp, HttpStatus.OK);
    }

    @Test
    void submitVerification_unauthorized() {
        String body = "{\"fullName\":\"Test User\",\"idType\":\"PASSPORT\",\"idNumber\":\"AB123456\"}";
        ResponseEntity<String> resp = postJson("/api/security/verification", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void submitVerification_validationFailure() {
        String token = registerAndLogin("verif_valid_fail", "SENIOR");

        String body = "{\"fullName\":\"\",\"idType\":\"\",\"idNumber\":\"\"}";
        ResponseEntity<String> resp = postJson("/api/security/verification", body, token);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitVerification_responseMasksIdNumber() {
        String token = registerAndLogin("verif_mask", "SENIOR");
        String body = "{\"fullName\":\"Sensitive User\",\"idType\":\"PASSPORT\",\"idNumber\":\"AB123456789\"}";
        ResponseEntity<String> resp = postJson("/api/security/verification", body, token);
        assertStatus(resp, HttpStatus.OK);
        String respBody = resp.getBody();
        // The full ID number should not appear in the response (should be masked or encrypted)
        assertTrue(!respBody.contains("AB123456789"),
            "Raw ID number should not appear in response — should be masked or encrypted");
        assertTrue(bodyContains(respBody, "idNumberMasked") || bodyContains(respBody, "masked"),
            "Response should contain masked ID number field");
    }

    // -----------------------------------------------------------------------
    // Identity Verification — Pending (admin-only)
    // -----------------------------------------------------------------------

    @Test
    void pendingVerifications_adminSuccess() {
        String adminToken = registerAndLogin("verif_admin", "PLATFORM_ADMIN");

        ResponseEntity<String> resp = get("/api/security/verification/pending", adminToken);

        assertStatus(resp, HttpStatus.OK);
    }

    @Test
    void pendingVerifications_unauthorized() {
        ResponseEntity<String> resp = get("/api/security/verification/pending");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pendingVerifications_forbidden_senior() {
        String seniorToken = registerAndLogin("verif_senior", "SENIOR");

        ResponseEntity<String> resp = get("/api/security/verification/pending", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Identity Verification — Review (admin-only)
    // -----------------------------------------------------------------------

    @Test
    void reviewVerification_unauthorized() {
        String body = "{\"status\":\"APPROVED\",\"notes\":\"ok\"}";
        ResponseEntity<String> resp = patchJson("/api/security/verification/99999", body, "invalid-token");

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for missing/invalid token but got: " + resp.getStatusCode());
    }

    @Test
    void reviewVerification_forbidden_senior() {
        String seniorToken = registerAndLogin("review_senior", "SENIOR");

        String body = "{\"status\":\"APPROVED\",\"notes\":\"ok\"}";
        ResponseEntity<String> resp = patchJson("/api/security/verification/99999", body, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Identity Verification — Full Success Path
    // -----------------------------------------------------------------------

    @Test
    void verificationWorkflow_submitThenApprove() {
        // Step 1: Register and login a senior user who will submit verification
        String seniorToken = registerAndLogin("verif_flow_senior", "SENIOR");

        // Step 2: Submit identity verification
        String submitBody = "{\"fullName\":\"Jane Doe\",\"idType\":\"DRIVERS_LICENSE\",\"idNumber\":\"DL-987654\"}";
        ResponseEntity<String> submitResp = postJson("/api/security/verification", submitBody, seniorToken);
        assertStatus(submitResp, HttpStatus.OK);
        assertNotNull(submitResp.getBody(), "Submit verification body must not be null");
        long verificationId = extractLong(submitResp.getBody(), "id");

        // Step 3: Register and login an admin to review
        String adminToken = registerAndLogin("verif_flow_admin", "PLATFORM_ADMIN");

        // Step 4: List pending verifications — should include the one just submitted
        ResponseEntity<String> pendingResp = get("/api/security/verification/pending", adminToken);
        assertStatus(pendingResp, HttpStatus.OK);
        assertNotNull(pendingResp.getBody(), "Pending verifications body must not be null");
        assertTrue(pendingResp.getBody().contains(String.valueOf(verificationId)),
                "Pending list should include verification " + verificationId);

        // Step 5: Approve the verification
        String reviewBody = "{\"status\":\"APPROVED\",\"notes\":\"Identity confirmed\"}";
        ResponseEntity<String> reviewResp = patchJson(
                "/api/security/verification/" + verificationId, reviewBody, adminToken);
        assertStatus(reviewResp, HttpStatus.OK);
        assertNotNull(reviewResp.getBody(), "Review verification body must not be null");
        assertTrue(reviewResp.getBody().contains("APPROVED"),
                "Reviewed verification should have APPROVED status");

        // Step 6: After approval, the pending list should no longer contain this verification
        ResponseEntity<String> pendingAfterApproval = get("/api/security/verification/pending", adminToken);
        assertStatus(pendingAfterApproval, HttpStatus.OK);
        assertNotNull(pendingAfterApproval.getBody(), "Pending verifications body must not be null after approval");
        assertTrue(!pendingAfterApproval.getBody().contains("\"id\":" + verificationId)
                        || pendingAfterApproval.getBody().contains("APPROVED"),
                "After approval, the verification should not appear as pending or should show APPROVED status; body="
                        + pendingAfterApproval.getBody());
    }
}
