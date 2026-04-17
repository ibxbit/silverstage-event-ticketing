package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagementApiIntegrationTest extends ApiTestBase {

    private String staffToken;
    private String seniorToken;

    @BeforeAll
    void setUpUsers() {
        staffToken = registerAndLogin("fm_staff", "SERVICE_STAFF");
        seniorToken = registerAndLogin("fm_senior", "SENIOR");
    }

    // -----------------------------------------------------------------------
    // POST /api/files/upload
    // -----------------------------------------------------------------------

    @Test
    void uploadDocument_success() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "Test Document");
        parts.add("folderPath", "/test");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        parts.add("file", fileResource("test.txt", "test content".getBytes()));

        ResponseEntity<String> resp = postMultipart("/api/files/upload", parts, staffToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "documentId"), "Body should contain 'documentId' key");
    }

    @Test
    void uploadDocument_unauthorized() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "Test Document");
        parts.add("folderPath", "/test");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        parts.add("file", fileResource("test.txt", "test content".getBytes()));

        ResponseEntity<String> resp = postMultipartNoAuth("/api/files/upload", parts);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void uploadDocument_forbidden_senior() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "Test Document");
        parts.add("folderPath", "/test");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        parts.add("file", fileResource("test.txt", "test content".getBytes()));

        ResponseEntity<String> resp = postMultipart("/api/files/upload", parts, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // GET /api/files
    // -----------------------------------------------------------------------

    @Test
    void listDocuments_success() {
        ResponseEntity<String> resp = get("/api/files", staffToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void listDocuments_unauthorized() {
        ResponseEntity<String> resp = get("/api/files");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // POST /api/files/{documentId}/versions
    // -----------------------------------------------------------------------

    @Test
    void uploadVersion_success() {
        // Upload an initial document first
        MultiValueMap<String, Object> uploadParts = multipartMap();
        uploadParts.add("title", "Versioned Document");
        uploadParts.add("folderPath", "/test");
        uploadParts.add("accessLevel", "STAFF_AND_ADMIN");
        uploadParts.add("file", fileResource("original.txt", "original content".getBytes()));

        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", uploadParts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        assertNotNull(uploadResp.getBody(), "Upload response body must not be null");

        long documentId = extractLong(uploadResp.getBody(), "documentId");

        // Upload a new version
        MultiValueMap<String, Object> versionParts = multipartMap();
        versionParts.add("file", fileResource("test-v2.txt", "updated content".getBytes()));

        ResponseEntity<String> versionResp = postMultipart(
                "/api/files/" + documentId + "/versions",
                versionParts,
                staffToken
        );

        assertStatus(versionResp, HttpStatus.OK);
        assertNotNull(versionResp.getBody(), "Version response body must not be null");
        assertTrue(bodyContains(versionResp.getBody(), "documentId"), "Body should contain 'documentId' key");
    }

    @Test
    void uploadVersion_unauthorized() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("test-v2.txt", "updated content".getBytes()));

        ResponseEntity<String> resp = postMultipartNoAuth("/api/files/99999/versions", parts);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void uploadVersion_forbidden_senior() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("test-v2.txt", "updated content".getBytes()));

        ResponseEntity<String> resp = postMultipart("/api/files/99999/versions", parts, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // GET /api/files/{documentId}/history
    // -----------------------------------------------------------------------

    @Test
    void versionHistory_success() {
        // Upload a document to get a real documentId
        MultiValueMap<String, Object> uploadParts = multipartMap();
        uploadParts.add("title", "History Document");
        uploadParts.add("folderPath", "/test");
        uploadParts.add("accessLevel", "STAFF_AND_ADMIN");
        uploadParts.add("file", fileResource("history.txt", "history content".getBytes()));

        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", uploadParts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        assertNotNull(uploadResp.getBody(), "Upload response body must not be null");

        long documentId = extractLong(uploadResp.getBody(), "documentId");

        ResponseEntity<String> historyResp = get("/api/files/" + documentId + "/history", staffToken);

        assertStatus(historyResp, HttpStatus.OK);
        assertNotNull(historyResp.getBody(), "History response body must not be null");
        assertTrue(
                bodyContains(historyResp.getBody(), "versions") || bodyContains(historyResp.getBody(), "documentId"),
                "Body should contain 'versions' or 'documentId' key"
        );
    }

    @Test
    void versionHistory_unauthorized() {
        ResponseEntity<String> resp = get("/api/files/99999/history");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // POST /api/files/{documentId}/download-links
    // -----------------------------------------------------------------------

    @Test
    void generateDownloadLink_success() {
        // Upload a document to get a real documentId
        MultiValueMap<String, Object> uploadParts = multipartMap();
        uploadParts.add("title", "Download Link Document");
        uploadParts.add("folderPath", "/test");
        uploadParts.add("accessLevel", "STAFF_AND_ADMIN");
        uploadParts.add("file", fileResource("download.txt", "download content".getBytes()));

        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", uploadParts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        assertNotNull(uploadResp.getBody(), "Upload response body must not be null");

        long documentId = extractLong(uploadResp.getBody(), "documentId");

        ResponseEntity<String> linkResp = postNoBody("/api/files/" + documentId + "/download-links", staffToken);

        assertStatus(linkResp, HttpStatus.OK);
        assertNotNull(linkResp.getBody(), "Download link response body must not be null");
        assertTrue(
                bodyContains(linkResp.getBody(), "token") || bodyContains(linkResp.getBody(), "downloadUrl"),
                "Body should contain 'token' or 'downloadUrl' key"
        );
    }

    @Test
    void generateDownloadLink_unauthorized() {
        ResponseEntity<String> resp = postEmpty("/api/files/99999/download-links");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // GET /api/files/download/{token}
    // -----------------------------------------------------------------------

    @Test
    void downloadByToken_success() {
        // Upload a document
        MultiValueMap<String, Object> uploadParts = multipartMap();
        uploadParts.add("title", "Token Download Document");
        uploadParts.add("folderPath", "/test");
        uploadParts.add("accessLevel", "STAFF_AND_ADMIN");
        uploadParts.add("file", fileResource("token-download.txt", "token download content".getBytes()));

        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", uploadParts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        assertNotNull(uploadResp.getBody(), "Upload response body must not be null");

        long documentId = extractLong(uploadResp.getBody(), "documentId");

        // Generate a download link
        ResponseEntity<String> linkResp = postNoBody("/api/files/" + documentId + "/download-links", staffToken);
        assertStatus(linkResp, HttpStatus.OK);
        assertNotNull(linkResp.getBody(), "Download link response body must not be null");

        String downloadToken = extractString(linkResp.getBody(), "token");

        // Download using the token
        ResponseEntity<byte[]> downloadResp = getBytes("/api/files/download/" + downloadToken, staffToken);

        assertStatus(downloadResp, HttpStatus.OK);
    }

    @Test
    void downloadByToken_unauthorized() {
        ResponseEntity<String> resp = get("/api/files/download/some-token");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void uploadDocument_missingRequiredFields() {
        // POST multipart without title or folderPath — required fields missing
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("test.txt", "test content".getBytes()));

        ResponseEntity<String> resp = postMultipart("/api/files/upload", parts, staffToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void downloadByToken_invalidToken() {
        ResponseEntity<String> resp = get("/api/files/download/invalid-token-xyz", staffToken);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for invalid download token but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // List Documents — filter and pagination gaps
    // -----------------------------------------------------------------------

    @Test
    void listDocuments_filterByFolderPath() {
        // Upload a doc to /test-filter folder
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "Filtered Doc");
        parts.add("folderPath", "/test-filter");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        parts.add("file", fileResource("filtered.txt", "filter content".getBytes()));
        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", parts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);

        ResponseEntity<String> resp = get("/api/files?folderPath=/test-filter", staffToken);
        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("Filtered Doc"), "Filtered list should contain uploaded doc title");
    }

    @Test
    void listDocuments_pagination() {
        ResponseEntity<String> resp = get("/api/files?page=0&size=1", staffToken);
        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody());
        assertTrue(bodyContains(resp.getBody(), "totalItems") || bodyContains(resp.getBody(), "total"),
            "Paginated response should contain total count field");
    }

    // -----------------------------------------------------------------------
    // Download Links — gaps
    // -----------------------------------------------------------------------

    @Test
    void generateDownloadLink_invalidDocumentId() {
        ResponseEntity<String> resp = postNoBody("/api/files/999999/download-links", staffToken);
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Expected 4xx for non-existent documentId but got: " + resp.getStatusCode());
    }

    @Test
    void generateDownloadLink_assertsTokenAndExpiry() {
        // Upload first
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "Link Fields Doc");
        parts.add("folderPath", "/test");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        parts.add("file", fileResource("link-fields.txt", "content".getBytes()));
        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", parts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        long docId = extractLong(uploadResp.getBody(), "documentId");

        ResponseEntity<String> resp = postNoBody("/api/files/" + docId + "/download-links", staffToken);
        assertStatus(resp, HttpStatus.OK);
        assertTrue(bodyContains(resp.getBody(), "token"), "Response should contain download token");
        assertTrue(bodyContains(resp.getBody(), "expiresAt") || bodyContains(resp.getBody(), "expiry"),
            "Response should contain expiry field");
    }

    // -----------------------------------------------------------------------
    // Upload Validation — gaps
    // -----------------------------------------------------------------------

    @Test
    void uploadDocument_missingFilePart() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("title", "No File Doc");
        parts.add("folderPath", "/test");
        parts.add("accessLevel", "STAFF_AND_ADMIN");
        // No file part added
        ResponseEntity<String> resp = postMultipart("/api/files/upload", parts, staffToken);
        assertTrue(resp.getStatusCode().is4xxClientError() || resp.getStatusCode().is5xxServerError(),
            "Expected error for missing file part but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Full Workflow
    // -----------------------------------------------------------------------

    @Test
    void fileWorkflow_fullCycle() {
        // Step 1: Upload a document
        MultiValueMap<String, Object> uploadParts = multipartMap();
        uploadParts.add("title", "Full Cycle Document");
        uploadParts.add("folderPath", "/test");
        uploadParts.add("accessLevel", "STAFF_AND_ADMIN");
        uploadParts.add("file", fileResource("full-cycle.txt", "full cycle content".getBytes()));

        ResponseEntity<String> uploadResp = postMultipart("/api/files/upload", uploadParts, staffToken);
        assertStatus(uploadResp, HttpStatus.OK);
        assertNotNull(uploadResp.getBody(), "Upload response body must not be null");
        assertTrue(bodyContains(uploadResp.getBody(), "documentId"), "Upload body should contain 'documentId'");
        long documentId = extractLong(uploadResp.getBody(), "documentId");

        // Step 2: List documents
        ResponseEntity<String> listResp = get("/api/files", staffToken);
        assertStatus(listResp, HttpStatus.OK);
        assertNotNull(listResp.getBody(), "List response body must not be null");

        // Step 3: Upload a new version
        MultiValueMap<String, Object> versionParts = multipartMap();
        versionParts.add("file", fileResource("full-cycle-v2.txt", "updated full cycle content".getBytes()));

        ResponseEntity<String> versionResp = postMultipart(
                "/api/files/" + documentId + "/versions",
                versionParts,
                staffToken
        );
        assertStatus(versionResp, HttpStatus.OK);
        assertNotNull(versionResp.getBody(), "Version response body must not be null");
        assertTrue(bodyContains(versionResp.getBody(), "documentId"), "Version body should contain 'documentId'");

        // Step 4: View version history — verify it contains versionNumber and has at least 2 entries
        ResponseEntity<String> historyResp = get("/api/files/" + documentId + "/history", staffToken);
        assertStatus(historyResp, HttpStatus.OK);
        assertNotNull(historyResp.getBody(), "History response body must not be null");
        assertTrue(
                bodyContains(historyResp.getBody(), "versions") || bodyContains(historyResp.getBody(), "documentId"),
                "History body should contain 'versions' or 'documentId'"
        );
        assertTrue(historyResp.getBody().contains("\"versionNumber\""),
                "History body should contain 'versionNumber' key after uploading a new version");
        // After initial upload + one new version, versionNumber=2 should appear
        int versionNumberOccurrences = 0;
        int searchIdx = 0;
        String historyBody = historyResp.getBody();
        while ((searchIdx = historyBody.indexOf("\"versionNumber\"", searchIdx)) != -1) {
            versionNumberOccurrences++;
            searchIdx++;
        }
        assertTrue(versionNumberOccurrences >= 2,
                "History should contain at least 2 versionNumber entries after uploading a new version, found: "
                        + versionNumberOccurrences + " body=" + historyBody);

        // Step 5: Generate a download link
        ResponseEntity<String> linkResp = postNoBody("/api/files/" + documentId + "/download-links", staffToken);
        assertStatus(linkResp, HttpStatus.OK);
        assertNotNull(linkResp.getBody(), "Download link response body must not be null");
        assertTrue(
                bodyContains(linkResp.getBody(), "token") || bodyContains(linkResp.getBody(), "downloadUrl"),
                "Download link body should contain 'token' or 'downloadUrl'"
        );

        String downloadToken = extractString(linkResp.getBody(), "token");

        // Step 6: Download by token
        ResponseEntity<byte[]> downloadResp = getBytes("/api/files/download/" + downloadToken, staffToken);
        assertStatus(downloadResp, HttpStatus.OK);
    }
}
