import java.net.http.HttpResponse;
import java.util.Map;

public final class FilesApiFunctionalTests {

    private FilesApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        String staffToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        long documentId = testUploadDocument(staffToken);
        testListDocuments(staffToken);
        testUploadVersion(staffToken, documentId);
        testGetHistory(staffToken, documentId);
        String downloadToken = testGenerateDownloadLink(staffToken, documentId);
        testDownloadByToken(staffToken, downloadToken);
    }

    private static long testUploadDocument(String token) throws Exception {
        String boundary = "----Boundary" + System.currentTimeMillis();
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"title\"\r\n\r\nDoc " + System.currentTimeMillis() + "\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"folderPath\"\r\n\r\n/test\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"accessLevel\"\r\n\r\nSTAFF_AND_ADMIN\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\ntest content for API functional test\r\n"
            + "--" + boundary + "--\r\n";

        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/files/upload", body,
            Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "X-Auth-Token", token
            )
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"documentId\"")) {
            throw new IllegalStateException("upload document response missing documentId: " + resp.body());
        }
        return ApiFunctionalTestHelper.extractFirstLong(resp.body(), "documentId");
    }

    private static void testListDocuments(String token) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/files?page=0&size=10", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"documents\"") && !resp.body().contains("\"items\"")
            && !resp.body().trim().startsWith("[")) {
            throw new IllegalStateException("list documents response is unexpected: " + resp.body());
        }
    }

    private static void testUploadVersion(String token, long documentId) throws Exception {
        String boundary = "----VersionBoundary" + System.currentTimeMillis();
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"test-v2.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\nupdated content for API functional test v2\r\n"
            + "--" + boundary + "--\r\n";

        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/files/" + documentId + "/versions", body,
            Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "X-Auth-Token", token
            )
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"documentId\"")) {
            throw new IllegalStateException("upload version response missing documentId: " + resp.body());
        }
    }

    private static void testGetHistory(String token, long documentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/files/" + documentId + "/history", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"documentId\"") && !resp.body().contains("\"versions\"")) {
            throw new IllegalStateException("document history response is unexpected: " + resp.body());
        }
    }

    private static String testGenerateDownloadLink(String token, long documentId) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/files/" + documentId + "/download-links?validHours=24", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        String downloadToken = ApiFunctionalTestHelper.extractString(resp.body(), "token");
        if (downloadToken == null || downloadToken.isBlank()) {
            throw new IllegalStateException("download-links response missing token: " + resp.body());
        }
        return downloadToken;
    }

    private static void testDownloadByToken(String token, String downloadToken) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/files/download/" + downloadToken, null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
    }
}
