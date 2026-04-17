import java.net.http.HttpResponse;
import java.util.Map;

public final class PaymentsApiFunctionalTests {

    private PaymentsApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        String adminToken = ApiFunctionalTestHelper.registerAndLogin("ORG_ADMIN");
        String transactionRef = testRecordTender(adminToken);
        testCallbackProcessing(adminToken, transactionRef);
        testCallbackIdempotency(adminToken, transactionRef);
        testSettlementImport(adminToken);
        testRefund(adminToken, transactionRef);
        testReconciliationReport(adminToken);
        testTraces(adminToken);
    }

    private static String testRecordTender(String token) throws Exception {
        String ref = "TXN-" + System.currentTimeMillis();
        String body = "{"
            + "\"transactionRef\":\"" + ref + "\","
            + "\"tenderType\":\"TERMINAL_BATCH\","
            + "\"amount\":50.00,"
            + "\"merchantCode\":\"MERCHANT001\""
            + "}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/payments/tenders", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200, 201);
        if (!resp.body().contains("\"transactionRef\"")) {
            throw new IllegalStateException("record tender response missing transactionRef: " + resp.body());
        }
        return ref;
    }

    private static void testCallbackProcessing(String token, String transactionRef) throws Exception {
        String path = "/api/payments/callbacks?transactionRef=" + transactionRef
            + "&gatewayBatchRef=BATCH-001&settledAmount=50.00&status=SETTLED&source=gateway";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", path, null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"processed\"")) {
            throw new IllegalStateException("callback response missing processed flag: " + resp.body());
        }
    }

    private static void testCallbackIdempotency(String token, String transactionRef) throws Exception {
        // Submit the same callback a second time — should still return 200 (idempotent)
        String path = "/api/payments/callbacks?transactionRef=" + transactionRef
            + "&gatewayBatchRef=BATCH-001&settledAmount=50.00&status=SETTLED&source=gateway";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", path, null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"processed\"")) {
            throw new IllegalStateException("idempotent callback response missing processed flag: " + resp.body());
        }
    }

    private static void testSettlementImport(String token) throws Exception {
        String boundary = "----SettlementBoundary" + System.currentTimeMillis();
        String csvContent = "transactionRef,batchRef,amount,status,source\nTXN-SAMPLE-" + System.currentTimeMillis() + ",BATCH-IMP-001,25.00,SETTLED,test_gateway\n";
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"settlement.csv\"\r\n"
            + "Content-Type: text/csv\r\n\r\n"
            + csvContent + "\r\n"
            + "--" + boundary + "--\r\n";

        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/payments/settlements/import", body,
            Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "X-Auth-Token", token
            )
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"importedRows\"") && !resp.body().contains("\"processedRows\"")) {
            throw new IllegalStateException(
                "settlement import response missing row counts: " + resp.body()
            );
        }
    }

    private static void testRefund(String token, String transactionRef) throws Exception {
        String body = "{"
            + "\"transactionRef\":\"" + transactionRef + "\","
            + "\"amount\":10.00,"
            + "\"reason\":\"API functional test refund\""
            + "}";
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "POST", "/api/payments/refunds", body,
            Map.of("Content-Type", "application/json", "X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 201);
        if (!resp.body().contains("\"refundAmount\"") && !resp.body().contains("\"transactionRef\"")) {
            throw new IllegalStateException(
                "refund response missing expected fields: " + resp.body()
            );
        }
    }

    private static void testReconciliationReport(String token) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/payments/reconciliation/report", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"grossRevenue\"") && !resp.body().contains("\"importedRows\"")) {
            throw new IllegalStateException("reconciliation report missing key fields: " + resp.body());
        }
    }

    private static void testTraces(String token) throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/payments/reconciliation/traces", null,
            Map.of("X-Auth-Token", token)
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        String body = resp.body().trim();
        if (!body.startsWith("[")) {
            throw new IllegalStateException("traces should be an array: " + body);
        }
    }
}
