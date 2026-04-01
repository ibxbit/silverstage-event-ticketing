package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.ReconciliationReportResponse;
import com.eaglepoint.venue.api.dto.RefundRequest;
import com.eaglepoint.venue.domain.PaymentTransaction;
import com.eaglepoint.venue.domain.ReconciliationException;
import com.eaglepoint.venue.domain.RevenueShareRecord;
import com.eaglepoint.venue.domain.SettlementCallback;
import com.eaglepoint.venue.mapper.OperationTraceMapper;
import com.eaglepoint.venue.mapper.PaymentTransactionMapper;
import com.eaglepoint.venue.mapper.ReconciliationExceptionMapper;
import com.eaglepoint.venue.mapper.RefundTransactionMapper;
import com.eaglepoint.venue.mapper.RevenueShareRecordMapper;
import com.eaglepoint.venue.mapper.SettlementCallbackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private PaymentTransactionMapper paymentTransactionMapper;
    @Mock
    private SettlementCallbackMapper settlementCallbackMapper;
    @Mock
    private RefundTransactionMapper refundTransactionMapper;
    @Mock
    private RevenueShareRecordMapper revenueShareRecordMapper;
    @Mock
    private ReconciliationExceptionMapper reconciliationExceptionMapper;
    @Mock
    private OperationTraceMapper operationTraceMapper;

    private PaymentReconciliationService paymentReconciliationService;

    @BeforeEach
    void setUp() {
        paymentReconciliationService = new PaymentReconciliationService(
                paymentTransactionMapper,
                settlementCallbackMapper,
                refundTransactionMapper,
                revenueShareRecordMapper,
                reconciliationExceptionMapper,
                operationTraceMapper
        );
    }

    @Test
    void importSettlementFile_handlesInvalidRowsAndIdempotentCallbacks() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(10L);
        tx.setTransactionRef("TX100");
        tx.setGrossAmount(new BigDecimal("100.00"));
        tx.setMerchantCode("M01");

        when(paymentTransactionMapper.findByTransactionRef("TX100")).thenReturn(tx);
        when(paymentTransactionMapper.findAll()).thenReturn(java.util.Collections.singletonList(tx));
        when(refundTransactionMapper.findAll()).thenReturn(new ArrayList<>());

        final Set<String> seenRefs = new HashSet<String>();
        when(settlementCallbackMapper.findByTransactionRef(anyString())).thenAnswer(invocation -> {
            String ref = invocation.getArgument(0);
            if (seenRefs.contains(ref)) {
                SettlementCallback cb = new SettlementCallback();
                cb.setTransactionRef(ref);
                cb.setSettledAmount(new BigDecimal("100.00"));
                return cb;
            }
            return null;
        });
        doAnswer(invocation -> {
            SettlementCallback cb = invocation.getArgument(0);
            seenRefs.add(cb.getTransactionRef());
            return 1;
        }).when(settlementCallbackMapper).insert(any(SettlementCallback.class));

        final List<RevenueShareRecord> shares = new ArrayList<RevenueShareRecord>();
        doAnswer(invocation -> {
            shares.add(invocation.getArgument(0));
            return 1;
        }).when(revenueShareRecordMapper).insert(any(RevenueShareRecord.class));
        when(revenueShareRecordMapper.findAll()).thenAnswer(invocation -> new ArrayList<RevenueShareRecord>(shares));

        final List<ReconciliationException> exceptions = new ArrayList<ReconciliationException>();
        doAnswer(invocation -> {
            exceptions.add(invocation.getArgument(0));
            return 1;
        }).when(reconciliationExceptionMapper).insert(any(ReconciliationException.class));
        when(reconciliationExceptionMapper.findAll()).thenAnswer(invocation -> new ArrayList<ReconciliationException>(exceptions));

        String csv = "transactionRef,batchRef,amount,status,source\n"
                + "TX100,B1,100.00,OK,GATEWAY\n"
                + "BROKEN-ROW\n"
                + "TX100,B2,100.00,OK,GATEWAY\n";
        MockMultipartFile file = new MockMultipartFile("file", "daily.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ReconciliationReportResponse report = paymentReconciliationService.importSettlementFile("ops_admin", file);

        assertEquals(Integer.valueOf(3), report.getImportedRows());
        assertEquals(Integer.valueOf(1), report.getProcessedRows());
        assertEquals(new BigDecimal("100.00"), report.getGrossRevenue());
        assertTrue(report.getExceptions().stream().anyMatch(row -> row.contains("INVALID_ROW")));
    }

    @Test
    void processCallback_isIdempotentByTransactionReference() {
        SettlementCallback existing = new SettlementCallback();
        existing.setTransactionRef("TX-EXISTING");
        existing.setSettledAmount(new BigDecimal("20.00"));
        existing.setCallbackStatus("OK");
        when(settlementCallbackMapper.findByTransactionRef("TX-EXISTING")).thenReturn(existing);

        boolean processed = paymentReconciliationService.processCallback(
                "ops_admin",
                "TX-EXISTING",
                "BATCH-1",
                new BigDecimal("20.00"),
                "OK",
                "gateway"
        );

        assertFalse(processed);
        verify(settlementCallbackMapper, never()).insert(any(SettlementCallback.class));
    }

    @Test
    void processCallback_duplicateConflictRecordsReconciliationExceptions() {
        SettlementCallback existing = new SettlementCallback();
        existing.setTransactionRef("TX-CONFLICT");
        existing.setSettledAmount(new BigDecimal("20.00"));
        existing.setCallbackStatus("OK");
        when(settlementCallbackMapper.findByTransactionRef("TX-CONFLICT")).thenReturn(existing);

        final List<ReconciliationException> exceptions = new ArrayList<ReconciliationException>();
        doAnswer(invocation -> {
            exceptions.add(invocation.getArgument(0));
            return 1;
        }).when(reconciliationExceptionMapper).insert(any(ReconciliationException.class));

        final List<com.eaglepoint.venue.domain.OperationTrace> traces = new ArrayList<com.eaglepoint.venue.domain.OperationTrace>();
        doAnswer(invocation -> {
            traces.add(invocation.getArgument(0));
            return 1;
        }).when(operationTraceMapper).insert(any(com.eaglepoint.venue.domain.OperationTrace.class));

        boolean processed = paymentReconciliationService.processCallback(
                "ops_admin",
                "TX-CONFLICT",
                "BATCH-2",
                new BigDecimal("22.00"),
                "FAILED",
                "gateway"
        );

        assertFalse(processed);
        assertTrue(exceptions.stream().anyMatch(row -> "AMOUNT_MISMATCH".equals(row.getExceptionType())));
        assertTrue(exceptions.stream().anyMatch(row -> "STATUS_MISMATCH".equals(row.getExceptionType())));
        assertTrue(traces.stream().anyMatch(row -> "CALLBACK_DUPLICATE_CONFLICT".equals(row.getAction())));

        verify(reconciliationExceptionMapper, times(2)).insert(any(ReconciliationException.class));
        verify(settlementCallbackMapper, never()).insert(any(SettlementCallback.class));
    }

    @Test
    void refund_rejectsAmountGreaterThanAvailable() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(3L);
        tx.setTransactionRef("TX-REF");
        tx.setGrossAmount(new BigDecimal("50.00"));
        tx.setRefundedAmount(new BigDecimal("10.00"));
        when(paymentTransactionMapper.findByTransactionRef("TX-REF")).thenReturn(tx);

        RefundRequest request = new RefundRequest();
        request.setTransactionRef("TX-REF");
        request.setAmount(new BigDecimal("45.00"));
        request.setReason("over-refund attempt");

        assertThrows(ResponseStatusException.class, () -> paymentReconciliationService.refund("ops_admin", request));
    }

    @Test
    void reconciliationReport_flagsAmountMismatchException() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(66L);
        tx.setTransactionRef("TX-MISMATCH");
        tx.setGrossAmount(new BigDecimal("100.00"));

        SettlementCallback callback = new SettlementCallback();
        callback.setTransactionRef("TX-MISMATCH");
        callback.setSettledAmount(new BigDecimal("95.00"));

        final List<ReconciliationException> exceptions = new ArrayList<ReconciliationException>();
        doAnswer(invocation -> {
            exceptions.add(invocation.getArgument(0));
            return 1;
        }).when(reconciliationExceptionMapper).insert(any(ReconciliationException.class));
        when(reconciliationExceptionMapper.findAll()).thenAnswer(invocation -> new ArrayList<ReconciliationException>(exceptions));

        when(paymentTransactionMapper.findAll()).thenReturn(java.util.Collections.singletonList(tx));
        when(settlementCallbackMapper.findByTransactionRef("TX-MISMATCH")).thenReturn(callback);
        when(refundTransactionMapper.findAll()).thenReturn(new ArrayList<>());
        when(revenueShareRecordMapper.findAll()).thenReturn(new ArrayList<>());

        ReconciliationReportResponse report = paymentReconciliationService.reconciliationReport();

        assertEquals(new BigDecimal("100.00"), report.getGrossRevenue());
        verify(reconciliationExceptionMapper, atLeastOnce()).insert(any(ReconciliationException.class));
    }
}
