package com.eaglepoint.venue.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentsApiIntegrationTest extends ApiTestBase {

    private String staffToken;
    private String adminToken;
    private String seniorToken;

    @BeforeAll
    void setUpUsers() {
        staffToken = registerAndLogin("pay_staff", "SERVICE_STAFF");
        adminToken = registerAndLogin("pay_admin", "ORG_ADMIN");
        seniorToken = registerAndLogin("pay_senior", "SENIOR");
    }

    // -----------------------------------------------------------------------
    // POST /api/payments/tenders
    // -----------------------------------------------------------------------

    @Test
    void recordTender_success() {
        String ref = unique("TXN");
        String body = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> resp = postJson("/api/payments/tenders", body, staffToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "transactionRef"), "Body should contain 'transactionRef' key");
    }

    @Test
    void recordTender_unauthorized() {
        String ref = unique("TXN");
        String body = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> resp = postJson("/api/payments/tenders", body);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void recordTender_forbidden_senior() {
        String ref = unique("TXN");
        String body = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> resp = postJson("/api/payments/tenders", body, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void recordTender_validationFailure() {
        String body = "{\"transactionRef\":\"\",\"tenderType\":\"\",\"amount\":null,\"merchantCode\":\"\"}";
        ResponseEntity<String> resp = postJson("/api/payments/tenders", body, staffToken);

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void recordTender_duplicateRef() {
        String ref = unique("TXN-DUP");
        String body = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";

        ResponseEntity<String> firstResp = postJson("/api/payments/tenders", body, staffToken);
        assertStatus(firstResp, HttpStatus.CREATED);

        ResponseEntity<String> secondResp = postJson("/api/payments/tenders", body, staffToken);
        int status = secondResp.getStatusCode().value();
        assertTrue(status == 400 || status == 409,
                "Second tender with duplicate transactionRef should return 400 or 409 but got: " + status);
    }

    // -----------------------------------------------------------------------
    // POST /api/payments/callbacks
    // -----------------------------------------------------------------------

    @Test
    void processCallback_success() {
        String ref = unique("TXN");
        String tenderBody = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> tenderResp = postJson("/api/payments/tenders", tenderBody, staffToken);
        assertStatus(tenderResp, HttpStatus.CREATED);

        String url = "/api/payments/callbacks?transactionRef=" + ref
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";
        ResponseEntity<String> resp = postNoBody(url, adminToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "processed"), "Body should contain 'processed' key");
    }

    @Test
    void processCallback_unauthorized() {
        String url = "/api/payments/callbacks?transactionRef=TXN-NOAUTH"
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";
        ResponseEntity<String> resp = postEmpty(url);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void processCallback_forbidden_senior() {
        String url = "/api/payments/callbacks?transactionRef=TXN-FORBIDDEN"
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";
        ResponseEntity<String> resp = postNoBody(url, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void processCallback_idempotency() {
        String ref = unique("TXN");
        String tenderBody = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> tenderResp = postJson("/api/payments/tenders", tenderBody, staffToken);
        assertStatus(tenderResp, HttpStatus.CREATED);

        String url = "/api/payments/callbacks?transactionRef=" + ref
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";

        // First call: should be processed (true)
        ResponseEntity<String> firstResp = postNoBody(url, adminToken);
        assertStatus(firstResp, HttpStatus.OK);
        assertNotNull(firstResp.getBody(), "First callback response body must not be null");
        assertTrue(firstResp.getBody().contains("true"), "First callback should return processed=true");

        // Second call: duplicate, should not be processed (false)
        ResponseEntity<String> secondResp = postNoBody(url, adminToken);
        assertStatus(secondResp, HttpStatus.OK);
        assertNotNull(secondResp.getBody(), "Second callback response body must not be null");
        assertTrue(secondResp.getBody().contains("false"), "Second callback should return processed=false for duplicate");
    }

    // -----------------------------------------------------------------------
    // POST /api/payments/settlements/import
    // -----------------------------------------------------------------------

    @Test
    void importSettlement_success() {
        String ref = unique("TXN");
        String csvContent = "transactionRef,batchRef,amount,status,source\n"
                + ref + ",BATCH-002,50.00,SETTLED,test_gateway\n";

        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("settlement.csv", csvContent.getBytes()));
        ResponseEntity<String> resp = postMultipart("/api/payments/settlements/import", parts, adminToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void importSettlement_unauthorized() {
        String csvContent = "transactionRef,batchRef,amount,status,source\nTXN-UNAUTH,BATCH-002,50.00,SETTLED,test_gateway\n";

        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("settlement.csv", csvContent.getBytes()));
        ResponseEntity<String> resp = postMultipartNoAuth("/api/payments/settlements/import", parts);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void importSettlement_forbidden_senior() {
        String csvContent = "transactionRef,batchRef,amount,status,source\nTXN-FORBIDDEN,BATCH-002,50.00,SETTLED,test_gateway\n";

        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("settlement.csv", csvContent.getBytes()));
        ResponseEntity<String> resp = postMultipart("/api/payments/settlements/import", parts, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void importSettlement_emptyFile() {
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("empty.csv", "".getBytes()));
        ResponseEntity<String> resp = postMultipart("/api/payments/settlements/import", parts, adminToken);
        // Empty file should either succeed with 0 rows or return 400
        assertStatus(resp, HttpStatus.OK);
        if (resp.getBody() != null) {
            assertTrue(resp.getBody().contains("0") || resp.getBody().contains("importedRows"),
                "Empty import should report 0 imported rows");
        }
    }

    @Test
    void importSettlement_malformedCsv() {
        String badCsv = "this is not valid csv content\nno headers at all\n";
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("bad.csv", badCsv.getBytes()));
        ResponseEntity<String> resp = postMultipart("/api/payments/settlements/import", parts, adminToken);
        // Should handle gracefully — either 400 or 200 with 0 processed
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
            "Malformed CSV should be handled gracefully, got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // POST /api/payments/refunds
    // -----------------------------------------------------------------------

    @Test
    void refund_success() {
        String ref = unique("TXN");
        String tenderBody = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> tenderResp = postJson("/api/payments/tenders", tenderBody, staffToken);
        assertStatus(tenderResp, HttpStatus.CREATED);

        String callbackUrl = "/api/payments/callbacks?transactionRef=" + ref
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";
        postNoBody(callbackUrl, adminToken);

        String refundBody = "{\"transactionRef\":\"" + ref + "\",\"amount\":25.00,\"reason\":\"Customer requested\"}";
        ResponseEntity<String> resp = postJson("/api/payments/refunds", refundBody, adminToken);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void refund_unauthorized() {
        String refundBody = "{\"transactionRef\":\"TXN-UNAUTH\",\"amount\":25.00,\"reason\":\"Customer requested\"}";
        ResponseEntity<String> resp = postJson("/api/payments/refunds", refundBody);

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refund_forbidden_senior() {
        String refundBody = "{\"transactionRef\":\"TXN-FORBIDDEN\",\"amount\":25.00,\"reason\":\"Customer requested\"}";
        ResponseEntity<String> resp = postJson("/api/payments/refunds", refundBody, seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void refund_invalidTransactionRef() {
        String refundBody = "{\"transactionRef\":\"TXN-NONEXISTENT-" + System.nanoTime() + "\",\"amount\":25.00,\"reason\":\"Invalid ref refund\"}";
        ResponseEntity<String> resp = postJson("/api/payments/refunds", refundBody, adminToken);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for refund of non-existent transactionRef but got: " + resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // GET /api/payments/reconciliation/report
    // -----------------------------------------------------------------------

    @Test
    void reconciliationReport_success() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/report", adminToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "grossRevenue"), "Report should contain grossRevenue");
        assertTrue(bodyContains(resp.getBody(), "netRevenue"), "Report should contain netRevenue");
        assertTrue(bodyContains(resp.getBody(), "importedRows"), "Report should contain importedRows");
    }

    @Test
    void reconciliationReport_unauthorized() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/report");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reconciliationReport_forbidden_senior() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/report", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // GET /api/payments/reconciliation/traces
    // -----------------------------------------------------------------------

    @Test
    void traces_success() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/traces", adminToken);

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void traces_unauthorized() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/traces");

        assertStatus(resp, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void traces_forbidden_senior() {
        ResponseEntity<String> resp = get("/api/payments/reconciliation/traces", seniorToken);

        assertStatus(resp, HttpStatus.FORBIDDEN);
    }

    @Test
    void traces_containExpectedFieldsAfterWorkflow() {
        // Create some payment data
        String ref = unique("TXN-TRACE");
        postJson("/api/payments/tenders",
            "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":10.00,\"merchantCode\":\"MC1\"}",
            staffToken);

        ResponseEntity<String> resp = get("/api/payments/reconciliation/traces", adminToken);
        assertStatus(resp, HttpStatus.OK);
        String body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.trim().startsWith("["), "Traces should be a JSON array");
        assertTrue(bodyContains(body, "action"), "Trace entries should contain action field");
        assertTrue(bodyContains(body, "actor"), "Trace entries should contain actor field");
        assertTrue(bodyContains(body, "entityType"), "Trace entries should contain entityType field");
    }

    // -----------------------------------------------------------------------
    // Full Workflow
    // -----------------------------------------------------------------------

    @Test
    void paymentWorkflow_fullCycle() {
        // Step 1: Record tender
        String ref = unique("TXN");
        String tenderBody = "{\"transactionRef\":\"" + ref + "\",\"tenderType\":\"TERMINAL_BATCH\",\"amount\":100.00,\"merchantCode\":\"MERCHANT001\"}";
        ResponseEntity<String> tenderResp = postJson("/api/payments/tenders", tenderBody, staffToken);
        assertStatus(tenderResp, HttpStatus.CREATED);
        assertNotNull(tenderResp.getBody(), "Tender response body must not be null");
        assertTrue(bodyContains(tenderResp.getBody(), "transactionRef"), "Tender body should contain 'transactionRef'");

        // Step 2: Process callback
        String callbackUrl = "/api/payments/callbacks?transactionRef=" + ref
                + "&gatewayBatchRef=BATCH-001&settledAmount=100.00&status=SETTLED&source=test_gateway";
        ResponseEntity<String> callbackResp = postNoBody(callbackUrl, adminToken);
        assertStatus(callbackResp, HttpStatus.OK);
        assertNotNull(callbackResp.getBody(), "Callback response body must not be null");
        assertTrue(bodyContains(callbackResp.getBody(), "processed"), "Callback body should contain 'processed'");

        // Step 3: Import settlement
        String importRef = unique("TXN");
        String csvContent = "transactionRef,batchRef,amount,status,source\n"
                + importRef + ",BATCH-002,50.00,SETTLED,test_gateway\n";
        MultiValueMap<String, Object> parts = multipartMap();
        parts.add("file", fileResource("settlement.csv", csvContent.getBytes()));
        ResponseEntity<String> importResp = postMultipart("/api/payments/settlements/import", parts, adminToken);
        assertStatus(importResp, HttpStatus.OK);
        assertNotNull(importResp.getBody(), "Import response body must not be null");

        // Step 4: Refund
        String refundBody = "{\"transactionRef\":\"" + ref + "\",\"amount\":25.00,\"reason\":\"Customer requested\"}";
        ResponseEntity<String> refundResp = postJson("/api/payments/refunds", refundBody, adminToken);
        assertStatus(refundResp, HttpStatus.CREATED);
        assertNotNull(refundResp.getBody(), "Refund response body must not be null");

        // Step 5: Check reconciliation report
        ResponseEntity<String> reportResp = get("/api/payments/reconciliation/report", adminToken);
        assertStatus(reportResp, HttpStatus.OK);
        assertNotNull(reportResp.getBody(), "Reconciliation report body must not be null");
        assertTrue(
                bodyContains(reportResp.getBody(), "grossRevenue") || bodyContains(reportResp.getBody(), "importedRows"),
                "Report body should contain 'grossRevenue' or 'importedRows' key"
        );

        // Step 6: Check traces
        ResponseEntity<String> tracesResp = get("/api/payments/reconciliation/traces", adminToken);
        assertStatus(tracesResp, HttpStatus.OK);
        assertNotNull(tracesResp.getBody(), "Traces response body must not be null");
    }
}
