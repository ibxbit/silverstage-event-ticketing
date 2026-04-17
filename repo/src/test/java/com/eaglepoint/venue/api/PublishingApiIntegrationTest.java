package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishingApiIntegrationTest extends ApiTestBase {

    private String authorToken;
    private String authorUsername;
    private String moderatorToken;
    private String otherSeniorToken;

    @BeforeAll
    void setUpUsers() {
        String[] author = registerAndLoginWithUsername("pub_author", "SENIOR");
        authorToken = author[0];
        authorUsername = author[1];

        String[] moderator = registerAndLoginWithUsername("pub_moderator", "ORG_ADMIN");
        moderatorToken = moderator[0];

        String[] otherSenior = registerAndLoginWithUsername("pub_other_senior", "SENIOR");
        otherSeniorToken = otherSenior[0];
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content
    // -----------------------------------------------------------------------

    @Test
    void createDraft_success() {
        String body = "{\"title\":\"Test Draft\",\"body\":\"Test body content\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content", body, authorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "contentId"), "Body should contain 'contentId' key");
        assertTrue(bodyContains(resp.getBody(), "state"), "Body should contain 'state' key");
    }

    @Test
    void createDraft_unauthorized() {
        String body = "{\"title\":\"Test Draft\",\"body\":\"Test body content\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createDraft_validationFailure() {
        String body = "{\"title\":\"\",\"body\":\"\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content", body, authorToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // GET /api/publishing/content
    // -----------------------------------------------------------------------

    @Test
    void listContent_ownerSeesOwn() {
        // Create a draft first so there is something to list
        String createBody = "{\"title\":\"Listable Draft\",\"body\":\"Listable body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);

        ResponseEntity<String> resp = get("/api/publishing/content", authorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "contentId"), "Body should contain 'contentId' key");
    }

    @Test
    void listContent_adminSeesAll() {
        ResponseEntity<String> resp = get("/api/publishing/content", moderatorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void listContent_unauthorized() {
        ResponseEntity<String> resp = get("/api/publishing/content");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/update
    // -----------------------------------------------------------------------

    @Test
    void updateDraft_success() {
        String createBody = "{\"title\":\"Draft To Update\",\"body\":\"Original body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        String updateBody = "{\"title\":\"Updated\",\"body\":\"Updated body\",\"summary\":\"Update summary\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content/" + contentId + "/update", updateBody, authorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");

        // Verify version count increased after update
        ResponseEntity<String> versionsResp = get("/api/publishing/content/" + contentId + "/versions", authorToken);
        assertStatus(versionsResp, HttpStatus.OK);
        assertNotNull(versionsResp.getBody(), "Versions response body must not be null after update");
        String versionsBody = versionsResp.getBody().trim();
        assertTrue(versionsBody.startsWith("["), "Versions response should be a JSON array");
        assertTrue(versionsBody.contains("\"versionNumber\""),
                "Versions array should contain versionNumber entries after update: " + versionsBody);
        // After create + update there should be at least 2 version entries
        int versionCount = 0;
        int idx = 0;
        while ((idx = versionsBody.indexOf("\"versionNumber\"", idx)) != -1) {
            versionCount++;
            idx++;
        }
        assertTrue(versionCount >= 2,
                "Expected at least 2 version entries after create+update but found " + versionCount + ": " + versionsBody);
    }

    @Test
    void updateDraft_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Draft For Forbidden Update\",\"body\":\"Body content\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        String updateBody = "{\"title\":\"Hijacked Update\",\"body\":\"Hijacked body\",\"summary\":\"Hijacked summary\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content/" + contentId + "/update", updateBody, otherSeniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/submit
    // -----------------------------------------------------------------------

    @Test
    void submit_success() {
        String createBody = "{\"title\":\"Draft To Submit\",\"body\":\"Submittable body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "state"), "Body should contain 'state' key");
        assertTrue(resp.getBody().contains("SUBMISSION"), "State should contain SUBMISSION");
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/review
    // -----------------------------------------------------------------------

    @Test
    void review_success() {
        String createBody = "{\"title\":\"Draft For Review\",\"body\":\"Review body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void review_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/publishing/content/99999/review");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void review_forbidden_senior() {
        String createBody = "{\"title\":\"Draft Review Forbidden\",\"body\":\"Body text\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/review", authorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/publish
    // -----------------------------------------------------------------------

    @Test
    void publish_success() {
        String createBody = "{\"title\":\"Draft To Publish\",\"body\":\"Publishable body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(resp.getBody().contains("PUBLISH"), "State should be PUBLISH after publishing");
    }

    @Test
    void publish_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/publishing/content/99999/publish");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/appeals
    // -----------------------------------------------------------------------

    @Test
    void appeal_success() {
        String createBody = "{\"title\":\"Draft For Appeal\",\"body\":\"Appeal body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content/" + contentId + "/appeals", appealBody, authorToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "id"), "Appeal response should contain id");
        assertTrue(bodyContains(resp.getBody(), "status"), "Appeal response should contain status");
        assertTrue(resp.getBody().contains("PENDING"), "Appeal status should be PENDING");
    }

    @Test
    void appeal_unauthorized() {
        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/content/99999/appeals", appealBody);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/appeals/{appealId}/decision
    // -----------------------------------------------------------------------

    @Test
    void appealDecision_success() {
        String createBody = "{\"title\":\"Draft For Appeal Decision\",\"body\":\"Appeal decision body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> appealResp = postJson("/api/publishing/content/" + contentId + "/appeals", appealBody, authorToken);
        assertStatus(appealResp, HttpStatus.CREATED);
        long appealId = extractLong(appealResp.getBody(), "id");

        String decisionBody = "{\"status\":\"APPROVED\",\"reviewNotes\":\"Correction allowed\"}";
        ResponseEntity<String> decisionResp = postJson("/api/publishing/appeals/" + appealId + "/decision", decisionBody, moderatorToken);

        assertStatus(decisionResp, HttpStatus.OK);
        assertNotNull(decisionResp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(decisionResp.getBody(), "status"), "Response should contain status");
        assertTrue(decisionResp.getBody().contains("APPROVED"), "Appeal should be APPROVED");
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/corrections
    // -----------------------------------------------------------------------

    @Test
    void correction_ownerAllowed() {
        String createBody = "{\"title\":\"Draft For Correction\",\"body\":\"Correction body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> appealResp = postJson("/api/publishing/content/" + contentId + "/appeals", appealBody, authorToken);
        assertStatus(appealResp, HttpStatus.CREATED);
        long appealId = extractLong(appealResp.getBody(), "id");

        String decisionBody = "{\"status\":\"APPROVED\",\"reviewNotes\":\"Correction allowed\"}";
        postJson("/api/publishing/appeals/" + appealId + "/decision", decisionBody, moderatorToken);

        String correctionBody = "{\"title\":\"Corrected\",\"body\":\"Corrected body\",\"summary\":\"Post-publish fix\"}";
        ResponseEntity<String> resp = postJson(
                "/api/publishing/content/" + contentId + "/corrections?appealId=" + appealId,
                correctionBody,
                authorToken
        );

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");

        // Verify audit trail recorded the correction
        ResponseEntity<String> auditResp = get("/api/publishing/content/" + contentId + "/audit", authorToken);
        assertStatus(auditResp, HttpStatus.OK);
        assertNotNull(auditResp.getBody(), "Audit response body must not be null after correction");
        String auditBody = auditResp.getBody().trim();
        assertTrue(auditBody.startsWith("["), "Audit response should be a JSON array");
        assertTrue(auditBody.length() > 2, "Audit array should be non-empty after correction");
    }

    @Test
    void correction_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Draft For Forbidden Correction\",\"body\":\"Forbidden correction body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> appealResp = postJson("/api/publishing/content/" + contentId + "/appeals", appealBody, authorToken);
        assertStatus(appealResp, HttpStatus.CREATED);
        long appealId = extractLong(appealResp.getBody(), "id");

        String decisionBody = "{\"status\":\"APPROVED\",\"reviewNotes\":\"Correction allowed\"}";
        postJson("/api/publishing/appeals/" + appealId + "/decision", decisionBody, moderatorToken);

        String correctionBody = "{\"title\":\"Hijacked Correction\",\"body\":\"Hijacked body\",\"summary\":\"Unauthorized fix\"}";
        ResponseEntity<String> resp = postJson(
                "/api/publishing/content/" + contentId + "/corrections?appealId=" + appealId,
                correctionBody,
                otherSeniorToken
        );

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // GET /api/publishing/content/{contentId}/versions
    // -----------------------------------------------------------------------

    @Test
    void versions_success() {
        String createBody = "{\"title\":\"Draft For Versions\",\"body\":\"Versions body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = get("/api/publishing/content/" + contentId + "/versions", authorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void versions_unauthorized() {
        ResponseEntity<String> resp = get("/api/publishing/content/99999/versions");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void versions_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Draft For Versions Forbidden\",\"body\":\"Versions forbidden body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = get("/api/publishing/content/" + contentId + "/versions", otherSeniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // GET /api/publishing/content/{contentId}/diff
    // -----------------------------------------------------------------------

    @Test
    void diff_success() {
        String createBody = "{\"title\":\"Draft For Diff\",\"body\":\"Diff original body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        String updateBody = "{\"title\":\"Diff Updated\",\"body\":\"Diff updated body\",\"summary\":\"Diff summary\"}";
        postJson("/api/publishing/content/" + contentId + "/update", updateBody, authorToken);

        ResponseEntity<String> resp = get(
                "/api/publishing/content/" + contentId + "/diff?leftVersion=1&rightVersion=2",
                authorToken
        );

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "leftVersion"), "Diff should contain leftVersion field");
        assertTrue(bodyContains(resp.getBody(), "rightVersion"), "Diff should contain rightVersion field");
        assertTrue(bodyContains(resp.getBody(), "leftLines"), "Diff should contain leftLines field");
        assertTrue(bodyContains(resp.getBody(), "rightLines"), "Diff should contain rightLines field");
    }

    // -----------------------------------------------------------------------
    // GET /api/publishing/content/{contentId}/audit
    // -----------------------------------------------------------------------

    @Test
    void audit_success() {
        String createBody = "{\"title\":\"Draft For Audit\",\"body\":\"Audit body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = get("/api/publishing/content/" + contentId + "/audit", authorToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(resp.getBody().trim().startsWith("["), "Audit should be a JSON array");
    }

    // -----------------------------------------------------------------------
    // POST /api/publishing/content/{contentId}/rollback
    // -----------------------------------------------------------------------

    @Test
    void rollback_success() {
        String createBody = "{\"title\":\"Draft For Rollback\",\"body\":\"Rollback original body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        String updateBody = "{\"title\":\"Rollback Updated\",\"body\":\"Rollback updated body\",\"summary\":\"Rollback summary\"}";
        postJson("/api/publishing/content/" + contentId + "/update", updateBody, authorToken);

        ResponseEntity<String> resp = postNoBody(
                "/api/publishing/content/" + contentId + "/rollback?targetVersion=1",
                moderatorToken
        );

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void rollback_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/publishing/content/99999/rollback?targetVersion=1");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rollback_forbidden_senior() {
        String createBody = "{\"title\":\"Draft Rollback Forbidden\",\"body\":\"Rollback forbidden body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        String updateBody = "{\"title\":\"Rollback Updated Forbidden\",\"body\":\"Updated body\",\"summary\":\"Summary\"}";
        postJson("/api/publishing/content/" + contentId + "/update", updateBody, authorToken);

        ResponseEntity<String> resp = postNoBody(
                "/api/publishing/content/" + contentId + "/rollback?targetVersion=1",
                authorToken
        );

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Negative-path tests
    // -----------------------------------------------------------------------

    @Test
    void rollback_invalidVersion() {
        String createBody = "{\"title\":\"Rollback Invalid Version\",\"body\":\"Body content\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = postNoBody(
                "/api/publishing/content/" + contentId + "/rollback?targetVersion=99999",
                moderatorToken
        );

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for rollback to non-existent version but got: " + resp.getStatusCode());
    }

    @Test
    void diff_invalidVersions() {
        String createBody = "{\"title\":\"Diff Invalid Versions\",\"body\":\"Body content\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        ResponseEntity<String> resp = get(
                "/api/publishing/content/" + contentId + "/diff?leftVersion=999&rightVersion=1000",
                authorToken
        );

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for diff of non-existent versions but got: " + resp.getStatusCode());
    }

    @Test
    void updateDraft_afterPublish_fails() {
        // Create, submit, review, publish, then try to update — should be rejected
        String createBody = "{\"title\":\"Draft That Gets Published\",\"body\":\"Body content\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        String updateBody = "{\"title\":\"Illegal Update After Publish\",\"body\":\"Updated body\",\"summary\":\"Should fail\"}";
        ResponseEntity<String> updateResp = postJson(
                "/api/publishing/content/" + contentId + "/update", updateBody, authorToken);

        assertTrue(updateResp.getStatusCode().is4xxClientError(),
                "Expected 4xx when updating already-published content but got: " + updateResp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Diff endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void diff_unauthorized() {
        ResponseEntity<String> resp = get("/api/publishing/content/99999/diff?leftVersion=1&rightVersion=2");
        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void diff_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Diff Forbidden\",\"body\":\"Body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");
        ResponseEntity<String> resp = get("/api/publishing/content/" + contentId + "/diff?leftVersion=1&rightVersion=2", otherSeniorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Audit endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void audit_unauthorized() {
        ResponseEntity<String> resp = get("/api/publishing/content/99999/audit");
        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void audit_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Audit Forbidden\",\"body\":\"Body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");
        ResponseEntity<String> resp = get("/api/publishing/content/" + contentId + "/audit", otherSeniorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void audit_containsExpectedActionSequence() {
        // Create, update, submit — then check audit has matching actions
        String createBody = "{\"title\":\"Audit Actions\",\"body\":\"Body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        long contentId = extractLong(createResp.getBody(), "contentId");

        postJson("/api/publishing/content/" + contentId + "/update",
            "{\"title\":\"Updated\",\"body\":\"Updated body\",\"summary\":\"Update\"}", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);

        ResponseEntity<String> auditResp = get("/api/publishing/content/" + contentId + "/audit", authorToken);
        assertStatus(auditResp, HttpStatus.OK);
        String body = auditResp.getBody();
        assertTrue(body.contains("CREATE") || body.contains("create"), "Audit should contain CREATE action");
        assertTrue(body.contains("UPDATE") || body.contains("update"), "Audit should contain UPDATE action");
        assertTrue(body.contains("SUBMIT") || body.contains("submit"), "Audit should contain SUBMIT action");
    }

    // -----------------------------------------------------------------------
    // Appeal decision endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void appealDecision_unauthorized() {
        String body = "{\"status\":\"APPROVED\",\"reviewNotes\":\"test\"}";
        ResponseEntity<String> resp = postJson("/api/publishing/appeals/99999/decision", body);
        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void appealDecision_forbidden_senior() {
        // Create full lifecycle to get a real appeal
        String createBody = "{\"title\":\"Appeal Decision Forbidden\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");
        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);
        ResponseEntity<String> appealResp = postJson("/api/publishing/content/" + contentId + "/appeals",
            "{\"justification\":\"test\"}", authorToken);
        assertStatus(appealResp, HttpStatus.CREATED);
        long appealId = extractLong(appealResp.getBody(), "id");

        // Senior (non-privileged) tries to decide
        ResponseEntity<String> resp = postJson("/api/publishing/appeals/" + appealId + "/decision",
            "{\"status\":\"APPROVED\",\"reviewNotes\":\"unauthorized\"}", otherSeniorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void appealDecision_invalidAppealId() {
        ResponseEntity<String> resp = postJson("/api/publishing/appeals/999999/decision",
            "{\"status\":\"APPROVED\",\"reviewNotes\":\"test\"}", moderatorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Expected 4xx for invalid appealId but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Submit endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void submit_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/publishing/content/99999/submit");
        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void submit_nonOwner_forbidden() {
        String createBody = "{\"title\":\"Submit Forbidden\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");
        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/submit", otherSeniorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void submit_afterPublish_invalidTransition() {
        String createBody = "{\"title\":\"Submit After Publish\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");
        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Expected 4xx for submit after publish but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Publish endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void publish_forbidden_senior() {
        String createBody = "{\"title\":\"Publish Forbidden\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");
        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);

        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/publish", authorToken);
        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void publish_beforeReview_invalidTransition() {
        String createBody = "{\"title\":\"Publish Before Review\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");
        postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);

        // Skip review, try to publish directly
        ResponseEntity<String> resp = postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Expected 4xx for publish before review but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Appeal endpoint gap tests
    // -----------------------------------------------------------------------

    @Test
    void appeal_beforePublish_invalidState() {
        String createBody = "{\"title\":\"Appeal Before Publish\",\"body\":\"Body\"}";
        ResponseEntity<String> cr = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(cr, HttpStatus.CREATED);
        long contentId = extractLong(cr.getBody(), "contentId");

        ResponseEntity<String> resp = postJson("/api/publishing/content/" + contentId + "/appeals",
            "{\"justification\":\"premature appeal\"}", authorToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Expected 4xx for appeal on non-published content but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Full Workflow
    // -----------------------------------------------------------------------

    @Test
    void publishingWorkflow_fullCycle() {
        // Step 1: Create draft
        String createBody = "{\"title\":\"Full Cycle Draft\",\"body\":\"Full cycle original body\"}";
        ResponseEntity<String> createResp = postJson("/api/publishing/content", createBody, authorToken);
        assertStatus(createResp, HttpStatus.CREATED);
        assertNotNull(createResp.getBody(), "Create response body must not be null");
        assertTrue(bodyContains(createResp.getBody(), "contentId"), "Create body should contain 'contentId'");
        long contentId = extractLong(createResp.getBody(), "contentId");

        // Step 2: Update draft
        String updateBody = "{\"title\":\"Full Cycle Updated\",\"body\":\"Full cycle updated body\",\"summary\":\"Full cycle update summary\"}";
        ResponseEntity<String> updateResp = postJson("/api/publishing/content/" + contentId + "/update", updateBody, authorToken);
        assertStatus(updateResp, HttpStatus.OK);
        assertNotNull(updateResp.getBody(), "Update response body must not be null");

        // Step 3: Submit draft
        ResponseEntity<String> submitResp = postNoBody("/api/publishing/content/" + contentId + "/submit", authorToken);
        assertStatus(submitResp, HttpStatus.OK);
        assertNotNull(submitResp.getBody(), "Submit response body must not be null");
        assertTrue(submitResp.getBody().contains("SUBMISSION"), "Submit state should contain SUBMISSION");

        // Step 4: Review
        ResponseEntity<String> reviewResp = postNoBody("/api/publishing/content/" + contentId + "/review", moderatorToken);
        assertStatus(reviewResp, HttpStatus.OK);
        assertNotNull(reviewResp.getBody(), "Review response body must not be null");

        // Step 5: Publish
        ResponseEntity<String> publishResp = postNoBody("/api/publishing/content/" + contentId + "/publish", moderatorToken);
        assertStatus(publishResp, HttpStatus.OK);
        assertNotNull(publishResp.getBody(), "Publish response body must not be null");

        // Step 6: Request appeal
        String appealBody = "{\"justification\":\"Need correction\"}";
        ResponseEntity<String> appealResp = postJson("/api/publishing/content/" + contentId + "/appeals", appealBody, authorToken);
        assertStatus(appealResp, HttpStatus.CREATED);
        assertNotNull(appealResp.getBody(), "Appeal response body must not be null");
        long appealId = extractLong(appealResp.getBody(), "id");

        // Step 7: Approve appeal
        String decisionBody = "{\"status\":\"APPROVED\",\"reviewNotes\":\"Correction allowed\"}";
        ResponseEntity<String> decisionResp = postJson("/api/publishing/appeals/" + appealId + "/decision", decisionBody, moderatorToken);
        assertStatus(decisionResp, HttpStatus.OK);
        assertNotNull(decisionResp.getBody(), "Appeal decision response body must not be null");

        // Step 8: Apply correction
        String correctionBody = "{\"title\":\"Full Cycle Corrected\",\"body\":\"Full cycle corrected body\",\"summary\":\"Post-publish fix\"}";
        ResponseEntity<String> correctionResp = postJson(
                "/api/publishing/content/" + contentId + "/corrections?appealId=" + appealId,
                correctionBody,
                authorToken
        );
        assertStatus(correctionResp, HttpStatus.OK);
        assertNotNull(correctionResp.getBody(), "Correction response body must not be null");

        // Step 9: Check versions — must be a non-empty array with multiple entries after create + update + correct
        ResponseEntity<String> versionsResp = get("/api/publishing/content/" + contentId + "/versions", authorToken);
        assertStatus(versionsResp, HttpStatus.OK);
        assertNotNull(versionsResp.getBody(), "Versions response body must not be null");
        String versionsBody = versionsResp.getBody().trim();
        assertTrue(versionsBody.startsWith("["), "Versions response should be a JSON array");
        assertTrue(versionsBody.length() > 2, "Versions array should be non-empty");
        int versionCount = 0;
        int idx = 0;
        while ((idx = versionsBody.indexOf("\"versionNumber\"", idx)) != -1) {
            versionCount++;
            idx++;
        }
        assertTrue(versionCount > 1,
                "Versions array should contain more than one entry after create+update+correction, got occurrences=" + versionCount + " body=" + versionsBody);

        // Step 10: Diff versions 1 and 2
        ResponseEntity<String> diffResp = get(
                "/api/publishing/content/" + contentId + "/diff?leftVersion=1&rightVersion=2",
                authorToken
        );
        assertStatus(diffResp, HttpStatus.OK);
        assertNotNull(diffResp.getBody(), "Diff response body must not be null");

        // Step 11: Audit trail — must be a non-empty array
        ResponseEntity<String> auditResp = get("/api/publishing/content/" + contentId + "/audit", authorToken);
        assertStatus(auditResp, HttpStatus.OK);
        assertNotNull(auditResp.getBody(), "Audit response body must not be null");
        String auditBody = auditResp.getBody().trim();
        assertTrue(auditBody.startsWith("["), "Audit response should be a JSON array");
        assertTrue(auditBody.length() > 2, "Audit array should be non-empty after the full workflow");

        // Step 12: Rollback to version 1
        ResponseEntity<String> rollbackResp = postNoBody(
                "/api/publishing/content/" + contentId + "/rollback?targetVersion=1",
                moderatorToken
        );
        assertStatus(rollbackResp, HttpStatus.OK);
        assertNotNull(rollbackResp.getBody(), "Rollback response body must not be null");
    }
}
