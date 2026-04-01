package com.eaglepoint.venue.api.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReconciliationReportResponse {
    private Integer importedRows;
    private Integer processedRows;
    private BigDecimal grossRevenue;
    private BigDecimal refundedRevenue;
    private BigDecimal netRevenue;
    private BigDecimal platformShare;
    private BigDecimal merchantShare;
    private List<String> exceptions = new ArrayList<String>();

    public Integer getImportedRows() { return importedRows; }
    public void setImportedRows(Integer importedRows) { this.importedRows = importedRows; }
    public Integer getProcessedRows() { return processedRows; }
    public void setProcessedRows(Integer processedRows) { this.processedRows = processedRows; }
    public BigDecimal getGrossRevenue() { return grossRevenue; }
    public void setGrossRevenue(BigDecimal grossRevenue) { this.grossRevenue = grossRevenue; }
    public BigDecimal getRefundedRevenue() { return refundedRevenue; }
    public void setRefundedRevenue(BigDecimal refundedRevenue) { this.refundedRevenue = refundedRevenue; }
    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }
    public BigDecimal getPlatformShare() { return platformShare; }
    public void setPlatformShare(BigDecimal platformShare) { this.platformShare = platformShare; }
    public BigDecimal getMerchantShare() { return merchantShare; }
    public void setMerchantShare(BigDecimal merchantShare) { this.merchantShare = merchantShare; }
    public List<String> getExceptions() { return exceptions; }
    public void setExceptions(List<String> exceptions) { this.exceptions = exceptions; }
}
