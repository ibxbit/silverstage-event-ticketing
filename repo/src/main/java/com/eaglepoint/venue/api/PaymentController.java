package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.PaymentTenderRequest;
import com.eaglepoint.venue.api.dto.ReconciliationReportResponse;
import com.eaglepoint.venue.api.dto.RefundRequest;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.OperationTrace;
import com.eaglepoint.venue.domain.PaymentTransaction;
import com.eaglepoint.venue.domain.RefundTransaction;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import com.eaglepoint.venue.service.PaymentReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentReconciliationService paymentReconciliationService;
    private final AccountSecurityService accountSecurityService;

    public PaymentController(PaymentReconciliationService paymentReconciliationService, AccountSecurityService accountSecurityService) {
        this.paymentReconciliationService = paymentReconciliationService;
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/tenders")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentTransaction recordTender(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody PaymentTenderRequest request
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_SERVICE_STAFF, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return paymentReconciliationService.recordTender(user.getUsername(), request);
    }

    @PostMapping("/callbacks")
    public Map<String, Object> callback(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam String transactionRef,
            @RequestParam String gatewayBatchRef,
            @RequestParam BigDecimal settledAmount,
            @RequestParam String status,
            @RequestParam(defaultValue = "gateway") String source
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        boolean processed = paymentReconciliationService.processCallback(user.getUsername(), transactionRef, gatewayBatchRef, settledAmount, status, source);
        return java.util.Collections.singletonMap("processed", processed);
    }

    @PostMapping("/settlements/import")
    public ReconciliationReportResponse importSettlement(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam("file") MultipartFile file
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return paymentReconciliationService.importSettlementFile(user.getUsername(), file);
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundTransaction refund(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody RefundRequest request
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return paymentReconciliationService.refund(user.getUsername(), request);
    }

    @GetMapping("/reconciliation/report")
    public ReconciliationReportResponse report(@RequestHeader("X-Auth-Token") String token) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return paymentReconciliationService.reconciliationReport();
    }

    @GetMapping("/reconciliation/traces")
    public List<OperationTrace> traces(@RequestHeader("X-Auth-Token") String token) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return paymentReconciliationService.traces();
    }
}
