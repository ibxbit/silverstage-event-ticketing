package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.PaymentTenderRequest;
import com.eaglepoint.venue.api.dto.ReconciliationReportResponse;
import com.eaglepoint.venue.api.dto.RefundRequest;
import com.eaglepoint.venue.domain.OperationTrace;
import com.eaglepoint.venue.domain.PaymentTransaction;
import com.eaglepoint.venue.domain.ReconciliationException;
import com.eaglepoint.venue.domain.RefundTransaction;
import com.eaglepoint.venue.domain.RevenueShareRecord;
import com.eaglepoint.venue.domain.SettlementCallback;
import com.eaglepoint.venue.mapper.OperationTraceMapper;
import com.eaglepoint.venue.mapper.PaymentTransactionMapper;
import com.eaglepoint.venue.mapper.ReconciliationExceptionMapper;
import com.eaglepoint.venue.mapper.RefundTransactionMapper;
import com.eaglepoint.venue.mapper.RevenueShareRecordMapper;
import com.eaglepoint.venue.mapper.SettlementCallbackMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentReconciliationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentReconciliationService.class);
    
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final SettlementCallbackMapper settlementCallbackMapper;
    private final RefundTransactionMapper refundTransactionMapper;
    private final RevenueShareRecordMapper revenueShareRecordMapper;
    private final ReconciliationExceptionMapper reconciliationExceptionMapper;
    private final OperationTraceMapper operationTraceMapper;

    public PaymentReconciliationService(
            PaymentTransactionMapper paymentTransactionMapper,
            SettlementCallbackMapper settlementCallbackMapper,
            RefundTransactionMapper refundTransactionMapper,
            RevenueShareRecordMapper revenueShareRecordMapper,
            ReconciliationExceptionMapper reconciliationExceptionMapper,
            OperationTraceMapper operationTraceMapper
    ) {
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.settlementCallbackMapper = settlementCallbackMapper;
        this.refundTransactionMapper = refundTransactionMapper;
        this.revenueShareRecordMapper = revenueShareRecordMapper;
        this.reconciliationExceptionMapper = reconciliationExceptionMapper;
        this.operationTraceMapper = operationTraceMapper;
    }

    @Transactional
    public PaymentTransaction recordTender(String actor, PaymentTenderRequest request) {
        logger.info("Recording payment tender for transactionRef: {} by user: {}", 
                request.getTransactionRef(), actor);
        String tenderType = clean(request.getTenderType()).toUpperCase();
        if (!"CASH".equals(tenderType) && !"CHECK".equals(tenderType) && !"TERMINAL_BATCH".equals(tenderType)) {
            logger.warn("Invalid tenderType provided: {} for transactionRef: {} by user: {}", 
                    request.getTenderType(), request.getTransactionRef(), actor);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenderType must be CASH, CHECK, or TERMINAL_BATCH");
        }
        if (paymentTransactionMapper.findByTransactionRef(clean(request.getTransactionRef())) != null) {
            logger.warn("Duplicate transactionRef attempted: {} by user: {}", 
                    request.getTransactionRef(), actor);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "transactionRef already exists");
        }

        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionRef(clean(request.getTransactionRef()));
        tx.setTenderType(tenderType);
        tx.setGrossAmount(request.getAmount());
        tx.setRefundedAmount(BigDecimal.ZERO);
        tx.setMerchantCode(clean(request.getMerchantCode()));
        tx.setStatus("RECORDED");
        paymentTransactionMapper.insert(tx);
        trace(actor, "TENDER_RECORDED", "payment_transaction", tx.getTransactionRef(), "amount=" + tx.getGrossAmount());
        logger.info("Payment tender recorded successfully for transactionRef: {} by user: {}", 
                tx.getTransactionRef(), actor);
        return paymentTransactionMapper.findByTransactionRef(tx.getTransactionRef());
    }

    @Transactional
    public boolean processCallback(String actor, String transactionRef, String batchRef, BigDecimal settledAmount, String callbackStatus, String source) {
        logger.info("Processing settlement callback for transactionRef: {} by user: {}", transactionRef, actor);
        String ref = clean(transactionRef);
        SettlementCallback existing = settlementCallbackMapper.findByTransactionRef(ref);
        if (existing != null) {
            logger.info("Duplicate callback detected for transactionRef: {} by user: {}", transactionRef, actor);
            String incomingStatus = clean(callbackStatus).toUpperCase();
            boolean amountMismatch = existing.getSettledAmount() != null && settledAmount != null
                    && existing.getSettledAmount().compareTo(settledAmount) != 0;
            boolean statusMismatch = !clean(existing.getCallbackStatus()).equalsIgnoreCase(incomingStatus);

            if (amountMismatch) {
                logger.warn("Amount mismatch detected for transactionRef: {} - existing: {}, incoming: {}", 
                        transactionRef, existing.getSettledAmount(), settledAmount);
                exception(ref, "AMOUNT_MISMATCH", "duplicate callback amount mismatch; existing=" + existing.getSettledAmount() + ",incoming=" + settledAmount);
            }
            if (statusMismatch) {
                logger.warn("Status mismatch detected for transactionRef: {} - existing: {}, incoming: {}", 
                        transactionRef, existing.getCallbackStatus(), incomingStatus);
                exception(ref, "STATUS_MISMATCH", "duplicate callback status mismatch; existing=" + existing.getCallbackStatus() + ",incoming=" + incomingStatus);
            }

            String action = (amountMismatch || statusMismatch) ? "CALLBACK_DUPLICATE_CONFLICT" : "CALLBACK_DUPLICATE_IGNORED";
            String payload = (amountMismatch || statusMismatch) ? "duplicate callback conflict detected" : "idempotent skip";
            trace(actor, action, "settlement_callback", ref, payload);
            logger.info("Duplicate callback processed with action: {} for transactionRef: {}", action, transactionRef);
            return false;
        }

        logger.info("Processing new settlement callback for transactionRef: {} by user: {}", transactionRef, actor);
        SettlementCallback cb = new SettlementCallback();
        cb.setTransactionRef(ref);
        cb.setGatewayBatchRef(clean(batchRef));
        cb.setSettledAmount(settledAmount);
        cb.setCallbackStatus(clean(callbackStatus).toUpperCase());
        cb.setSource(clean(source));
        cb.setCallbackAt(LocalDateTime.now());
        settlementCallbackMapper.insert(cb);

        PaymentTransaction tx = paymentTransactionMapper.findByTransactionRef(ref);
        if (tx == null) {
            logger.warn("No matching payment transaction found for transactionRef: {} during callback processing", transactionRef);
            exception(ref, "MISSING_PAYMENT", "callback has no matching payment transaction");
        } else {
            logger.info("Updating payment transaction status to SETTLED for transactionRef: {}", transactionRef);
            paymentTransactionMapper.updateStatus(tx.getId(), "SETTLED");
            calculateRevenueShare(tx, settledAmount);
            logger.info("Revenue share calculated for transactionRef: {}", transactionRef);
        }

        trace(actor, "CALLBACK_PROCESSED", "settlement_callback", ref, "status=" + cb.getCallbackStatus());
        logger.info("Settlement callback processed successfully for transactionRef: {} by user: {}", transactionRef, actor);
        return true;
    }

    @Transactional
    public ReconciliationReportResponse importSettlementFile(String actor, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "settlement file is required");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unable to read settlement file");
        }

        String[] lines = content.split("\\R");
        int imported = 0;
        int processed = 0;
        for (String line : lines) {
            String row = line.trim();
            if (row.isEmpty() || row.startsWith("#") || row.toLowerCase().startsWith("transactionref")) {
                continue;
            }
            imported++;
            String[] cols = row.split(",");
            if (cols.length < 5) {
                exception("UNKNOWN", "INVALID_ROW", "invalid settlement row: " + row);
                continue;
            }
            String ref = cols[0].trim();
            String batch = cols[1].trim();
            BigDecimal amount = new BigDecimal(cols[2].trim());
            String status = cols[3].trim();
            String source = cols[4].trim();
            if (processCallback(actor, ref, batch, amount, status, source)) {
                processed++;
            }
        }
        trace(actor, "SETTLEMENT_IMPORT", "file", file.getOriginalFilename(), "imported=" + imported + ",processed=" + processed);
        return buildReport(imported, processed);
    }

    @Transactional
    public RefundTransaction refund(String actor, RefundRequest request) {
        PaymentTransaction tx = paymentTransactionMapper.findByTransactionRef(clean(request.getTransactionRef()));
        if (tx == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found");
        }

        BigDecimal available = tx.getGrossAmount().subtract(tx.getRefundedAmount());
        if (request.getAmount().compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "refund exceeds available amount");
        }
        String type = request.getAmount().compareTo(available) == 0 ? "FULL" : "PARTIAL";

        RefundTransaction refund = new RefundTransaction();
        refund.setPaymentId(tx.getId());
        refund.setTransactionRef(tx.getTransactionRef());
        refund.setRefundAmount(request.getAmount());
        refund.setRefundType(type);
        refund.setReason(clean(request.getReason()));
        refundTransactionMapper.insert(refund);

        paymentTransactionMapper.incrementRefunded(tx.getId(), request.getAmount());
        trace(actor, "REFUND_" + type, "payment_transaction", tx.getTransactionRef(), "amount=" + request.getAmount());
        return refund;
    }

    public ReconciliationReportResponse reconciliationReport() {
        return buildReport(settlementCallbackMapper.findAll().size(), settlementCallbackMapper.findAll().size());
    }

    public List<OperationTrace> traces() {
        return operationTraceMapper.findAll();
    }

    private ReconciliationReportResponse buildReport(int importedRows, int processedRows) {
        List<PaymentTransaction> payments = paymentTransactionMapper.findAll();
        List<RefundTransaction> refunds = refundTransactionMapper.findAll();
        List<RevenueShareRecord> shares = revenueShareRecordMapper.findAll();
        List<ReconciliationException> exceptions = reconciliationExceptionMapper.findAll();

        BigDecimal gross = BigDecimal.ZERO;
        for (PaymentTransaction tx : payments) {
            gross = gross.add(tx.getGrossAmount());
            SettlementCallback cb = settlementCallbackMapper.findByTransactionRef(tx.getTransactionRef());
            if (cb != null && cb.getSettledAmount().compareTo(tx.getGrossAmount()) != 0) {
                exception(tx.getTransactionRef(), "AMOUNT_MISMATCH", "payment gross differs from settled amount");
            }
        }

        BigDecimal refunded = BigDecimal.ZERO;
        for (RefundTransaction refund : refunds) {
            refunded = refunded.add(refund.getRefundAmount());
        }

        BigDecimal platform = BigDecimal.ZERO;
        BigDecimal merchant = BigDecimal.ZERO;
        for (RevenueShareRecord share : shares) {
            platform = platform.add(share.getPlatformShare());
            merchant = merchant.add(share.getMerchantShare());
        }

        ReconciliationReportResponse response = new ReconciliationReportResponse();
        response.setImportedRows(importedRows);
        response.setProcessedRows(processedRows);
        response.setGrossRevenue(gross);
        response.setRefundedRevenue(refunded);
        response.setNetRevenue(gross.subtract(refunded));
        response.setPlatformShare(platform);
        response.setMerchantShare(merchant);
        List<String> ex = new ArrayList<String>();
        for (ReconciliationException one : exceptions) {
            ex.add(one.getTransactionRef() + " | " + one.getExceptionType() + " | " + one.getDetail());
        }
        response.setExceptions(ex);
        return response;
    }

    private void calculateRevenueShare(PaymentTransaction tx, BigDecimal settledAmount) {
        BigDecimal platform = settledAmount.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal merchant = settledAmount.subtract(platform).setScale(2, RoundingMode.HALF_UP);

        RevenueShareRecord row = new RevenueShareRecord();
        row.setTransactionRef(tx.getTransactionRef());
        row.setPlatformShare(platform);
        row.setMerchantShare(merchant);
        row.setMerchantCode(tx.getMerchantCode());
        revenueShareRecordMapper.insert(row);
    }

    private void exception(String transactionRef, String type, String detail) {
        ReconciliationException row = new ReconciliationException();
        row.setTransactionRef(transactionRef);
        row.setExceptionType(type);
        row.setDetail(detail);
        reconciliationExceptionMapper.insert(row);
    }

    private void trace(String actor, String action, String entityType, String entityRef, String payload) {
        OperationTrace trace = new OperationTrace();
        trace.setTraceId(UUID.randomUUID().toString());
        trace.setActor(clean(actor));
        trace.setAction(action);
        trace.setEntityType(entityType);
        trace.setEntityRef(entityRef);
        trace.setPayload(payload);
        operationTraceMapper.insert(trace);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
