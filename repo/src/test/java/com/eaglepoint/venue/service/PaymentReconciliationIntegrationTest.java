package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.PaymentTenderRequest;
import com.eaglepoint.venue.api.dto.RefundRequest;
import com.eaglepoint.venue.api.dto.ReconciliationReportResponse;
import com.eaglepoint.venue.domain.PaymentTransaction;
import com.eaglepoint.venue.domain.ReconciliationException;
import com.eaglepoint.venue.domain.RefundTransaction;
import com.eaglepoint.venue.domain.SettlementCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class PaymentReconciliationIntegrationTest {

    @Autowired
    private PaymentReconciliationService paymentReconciliationService;

    private String transactionRef = "TXN-TEST-001";
    private String batchRef = "BATCH-TEST-001";

    @BeforeEach
    void setUp() {
        // Clear any existing data (though @Transactional with @Rollback should handle this)
    }

    @Test
    void testPaymentCallbackIdempotencyAndReconciliationExceptions() {
        // Record a payment tender
        PaymentTenderRequest tenderRequest = new PaymentTenderRequest();
        tenderRequest.setTransactionRef(transactionRef);
        tenderRequest.setTenderType("TERMINAL_BATCH");
        tenderRequest.setAmount(new BigDecimal("100.00"));
        tenderRequest.setMerchantCode("MERCHANT001");

        PaymentTransaction tx = paymentReconciliationService.recordTender("system", tenderRequest);
        assertEquals("RECORDED", tx.getStatus());
        assertEquals(new BigDecimal("100.00"), tx.getGrossAmount());

        // Process first callback (should succeed)
        boolean firstResult = paymentReconciliationService.processCallback(
                "system", transactionRef, batchRef, new BigDecimal("100.00"), "SETTLED", "test_gateway");
        assertTrue(firstResult); // First callback should return true

        // Process duplicate callback with same data (should be ignored)
        boolean duplicateResult = paymentReconciliationService.processCallback(
                "system", transactionRef, batchRef, new BigDecimal("100.00"), "SETTLED", "test_gateway");
        assertFalse(duplicateResult); // Duplicate should return false

        // Process duplicate callback with different amount (should create exception)
        boolean conflictResult = paymentReconciliationService.processCallback(
                "system", transactionRef, batchRef, new BigDecimal("150.00"), "SETTLED", "test_gateway");
        assertFalse(conflictResult); // Conflicting callback should return false

        // Process duplicate callback with different status (should create exception)
        boolean statusConflictResult = paymentReconciliationService.processCallback(
                "system", transactionRef, batchRef, new BigDecimal("100.00"), "FAILED", "test_gateway");
        assertFalse(statusConflictResult); // Status conflict should return false

        // Test settlement file import
        String csvContent = "transactionRef,batchRef,amount,status,source\n" +
                "TXN-TEST-002,BATCH-TEST-002,50.00,SETTLED,test_gateway\n";
        MockMultipartFile file = new MockMultipartFile(
                "settlement.csv",
                "settlement.csv",
                "text/csv",
                csvContent.getBytes()
        );

        ReconciliationReportResponse report = paymentReconciliationService.importSettlementFile("system", file);
        assertEquals(1, report.getImportedRows());
        assertEquals(1, report.getProcessedRows());

        // Test refund functionality
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setTransactionRef(transactionRef);
        refundRequest.setAmount(new BigDecimal("25.00"));
        refundRequest.setReason("Customer requested refund");

        RefundTransaction refund = paymentReconciliationService.refund("system", refundRequest);
        assertNotNull(refund);
        assertEquals(new BigDecimal("25.00"), refund.getRefundAmount());
        assertEquals("PARTIAL", refund.getRefundType());

        // Test reconciliation report
        ReconciliationReportResponse reconciliationReport = paymentReconciliationService.reconciliationReport();
        assertEquals(2, reconciliationReport.getImportedRows()); // 2 transactions imported
        assertEquals(2, reconciliationReport.getProcessedRows()); // 2 transactions processed
        assertEquals(new BigDecimal("100.00"), reconciliationReport.getGrossRevenue());
        assertEquals(new BigDecimal("25.00"), reconciliationReport.getRefundedRevenue()); // 25 refunded
        assertEquals(new BigDecimal("75.00"), reconciliationReport.getNetRevenue());
    }
}
