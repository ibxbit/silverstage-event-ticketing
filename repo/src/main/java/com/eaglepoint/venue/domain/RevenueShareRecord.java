package com.eaglepoint.venue.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RevenueShareRecord {
    private Long id;
    private String transactionRef;
    private BigDecimal platformShare;
    private BigDecimal merchantShare;
    private String merchantCode;
    private LocalDateTime calculatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public BigDecimal getPlatformShare() { return platformShare; }
    public void setPlatformShare(BigDecimal platformShare) { this.platformShare = platformShare; }
    public BigDecimal getMerchantShare() { return merchantShare; }
    public void setMerchantShare(BigDecimal merchantShare) { this.merchantShare = merchantShare; }
    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
}
