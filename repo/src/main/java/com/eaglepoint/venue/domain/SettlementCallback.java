package com.eaglepoint.venue.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettlementCallback {
    private Long id;
    private String transactionRef;
    private String gatewayBatchRef;
    private BigDecimal settledAmount;
    private String callbackStatus;
    private String source;
    private LocalDateTime callbackAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getGatewayBatchRef() { return gatewayBatchRef; }
    public void setGatewayBatchRef(String gatewayBatchRef) { this.gatewayBatchRef = gatewayBatchRef; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public void setSettledAmount(BigDecimal settledAmount) { this.settledAmount = settledAmount; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCallbackAt() { return callbackAt; }
    public void setCallbackAt(LocalDateTime callbackAt) { this.callbackAt = callbackAt; }
}
